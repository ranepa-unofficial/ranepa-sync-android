package com.qteb.ranepa.sync.lk

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.qteb.ranepa.sync.core.addingDays
import com.qteb.ranepa.sync.core.json
import com.qteb.ranepa.sync.sync.FETCH_WEEKS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.coroutines.resume

/** Запрос расписания, который делает сама страница ЛК: в нём уже есть
 * uid_org, все группы и подгруппы студента и нужные заголовки. */
data class ScheduleTemplate(val url: String, val headers: Map<String, String>) {
    /** Тот же запрос на другие дни. */
    fun with(dates: List<Instant>): String {
        val uri = Uri.parse(url)
        val builder = uri.buildUpon().clearQuery()
        for (name in uri.queryParameterNames) {
            if (name == "dates[]") continue
            for (value in uri.getQueryParameters(name)) builder.appendQueryParameter(name, value)
        }
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T00:00:00.000'").withZone(ZoneId.systemDefault())
        for (d in dates) builder.appendQueryParameter("dates[]", f.format(d))
        return builder.build().toString()
    }
}

data class FetchResult(
    val days: JsonArray,
    val group: String? = null,
    val program: String? = null,
    /** «Фамилия Имя Отчество». */
    val fullName: String? = null,
    /** data:image/…;base64,… — null, если фото не запрашивали; пустая строка, если в ЛК его нет. */
    val photo: String? = null,
    /** Когда истекает refresh-токен: сутки, а с «Запомнить меня» — 30 дней.
     * Сам токен из страницы не выходит, только его срок. */
    val sessionUntil: Instant? = null,
    /** План обучения (JSON StudyPlan) — при загрузке семестра. */
    val plan: String? = null,
)

sealed class LkError(message: String) : Exception(message) {
    class NeedLogin : LkError("Сессия истекла, войди в ЛК заново")
    /** ЛК перегружен или лежит (429, 5xx): обновления ставятся на паузу. */
    class ServerBusy(val status: Int, val retryAfter: Int?) : LkError("ЛК сейчас недоступен, попробую позже")
    class Message(text: String) : LkError(text)

    companion object {
        fun busy(status: Int) = status == 429 || status >= 500
    }
}

/**
 * Работа с ЛК идёт только через WebView: сайт закрыт JS-проверкой браузера,
 * поэтому обычный HTTP-клиент вместо JSON получает заглушку-лоадер.
 * Cookie и токены лежат в хранилище WebView внутри приложения.
 */
@SuppressLint("SetJavaScriptEnabled")
class LkWebView(context: Context) {
    companion object {
        const val SCHEDULE_URL = "https://my.ranepa.ru/lk/student/schedule"
        private const val GROUPS_URL = "https://my.ranepa.ru/lk/n-api/manual/student-groups"
        private const val PROFILE_URL = "https://my.ranepa.ru/lk/n-api/profile"
        private const val PHOTO_URL = "https://my.ranepa.ru/lk/n-api/profile/photo"

        fun isLoginPage(url: String?): Boolean = url?.let { Uri.parse(it).path?.contains("/login") } ?: false

        /**
         * Обновление без интерфейса: невидимый WebView открывает ЛК с сохранёнными
         * cookie и токенами. Страница сама обновит токен и запросит расписание.
         */
        suspend fun refresh(
            context: Context, monday: Instant, weeks: Int, stopAfterEmpty: Int? = null,
            withProfile: Boolean, withPhoto: Boolean, withPlan: Boolean = false,
        ): FetchResult = withContext(Dispatchers.Main) {
            val lk = LkWebView(context.applicationContext)
            // Размер, как у телефона: страница ЛК строит мобильную вёрстку.
            lk.webView.layout(0, 0, 400, 800)
            try {
                val template = withTimeout(60_000) { lk.waitForTemplate() }
                lk.fetch(template, monday, weeks, stopAfterEmpty, withProfile, withPhoto, withPlan)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw LkError.Message("ЛК не ответил за минуту, попробуй позже")
            } finally {
                lk.destroy()
            }
        }

        /** Стирает cookie и localStorage (там лежат токены ЛК). */
        suspend fun logout() = withContext(Dispatchers.Main) {
            WebStorage.getInstance().deleteAllData()
            suspendCancellableCoroutine { cont ->
                CookieManager.getInstance().removeAllCookies { cont.resume(Unit) }
            }
            CookieManager.getInstance().flush()
        }
    }

    val webView = WebView(context)
    var onTemplate: ((ScheduleTemplate) -> Unit)? = null
    var onUrl: ((String?) -> Unit)? = null
    var onLoading: ((Boolean) -> Unit)? = null
    /** Страница ЛК не открылась: нет сети, TLS или 429/5xx. */
    var onFailure: ((LkError) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val results = mutableMapOf<String, CompletableDeferred<String>>()

    /** Методы для JavaScript страницы; вызываются не в главном потоке. */
    inner class Bridge {
        @JavascriptInterface
        fun scheduleRequest(payload: String) {
            main.post { received(payload) }
        }

        @JavascriptInterface
        fun result(id: String, payload: String) {
            main.post { results.remove(id)?.complete(payload) }
        }
    }

    init {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        webView.addJavascriptInterface(Bridge(), "RanepaBridge")
        val documentStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (documentStart) WebViewCompat.addDocumentStartJavaScript(webView, LkScripts.HOOK, setOf("*"))
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (!documentStart) view.evaluateJavascript(LkScripts.HOOK, null)
                onLoading?.invoke(true)
                onUrl?.invoke(url)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(LkScripts.REMEMBER_ME, null)
                onLoading?.invoke(false)
                onUrl?.invoke(url)
            }

            // Сайт — одностраничное приложение: вход и уход с формы входа
            // меняют адрес без новой загрузки страницы.
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                onUrl?.invoke(url)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                onLoading?.invoke(false)
                onFailure?.invoke(LkError.Message("Нет связи с ЛК: ${error.description}"))
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && LkError.busy(response.statusCode)) {
                    onFailure?.invoke(LkError.ServerBusy(response.statusCode, null))
                }
            }
        }
    }

    fun load() = webView.loadUrl(SCHEDULE_URL)

    fun destroy() {
        CookieManager.getInstance().flush()
        webView.stopLoading()
        webView.destroy()
    }

    private fun received(payload: String) {
        val data = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return
        val url = (data["url"] as? JsonPrimitive)?.contentOrNull ?: return
        val headers = (data["headers"] as? JsonObject).orEmpty()
            .mapValues { (it.value as? JsonPrimitive)?.contentOrNull ?: it.value.toString() }
        onTemplate?.invoke(ScheduleTemplate(url, headers))
    }

    private suspend fun waitForTemplate(): ScheduleTemplate = suspendCancellableCoroutine { cont ->
        fun finish(result: Result<ScheduleTemplate>) {
            if (cont.isActive) cont.resumeWith(result)
        }
        onTemplate = { finish(Result.success(it)) }
        onUrl = { if (isLoginPage(it)) finish(Result.failure(LkError.NeedLogin())) }
        onFailure = { finish(Result.failure(it)) }
        load()
    }

    /**
     * Скачивает [weeks] недель начиная с [monday] в уже авторизованной странице.
     * С [stopAfterEmpty] останавливается после стольких пустых недель подряд
     * (но не раньше месяца): дальше семестр кончился.
     */
    suspend fun fetch(
        template: ScheduleTemplate, monday: Instant, weeks: Int, stopAfterEmpty: Int? = null,
        withProfile: Boolean = true, withPhoto: Boolean = false, withPlan: Boolean = false,
    ): FetchResult = withContext(Dispatchers.Main) {
        val urls = (0 until weeks).map { w -> template.with((0 until 7).map { monday.addingDays(w * 7 + it) }) }
        val args = buildJsonObject {
            putJsonArray("urls") { urls.forEach { add(JsonPrimitive(it)) } }
            putJsonObject("headers") {
                template.headers.filterKeys { it.lowercase() != "authorization" }.forEach { (k, v) -> put(k, v) }
            }
            put("stopAfterEmpty", stopAfterEmpty ?: 0)
            put("minWeeks", FETCH_WEEKS)
            put("groupsUrl", GROUPS_URL)
            put("profileUrl", PROFILE_URL)
            put("photoUrl", PHOTO_URL)
            put("withProfile", withProfile)
            put("withPhoto", withPhoto)
            put("withPlan", withPlan && withProfile)
        }
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()
        results[id] = deferred
        webView.evaluateJavascript(LkScripts.fetch(id, args.toString()), null)
        val raw = try {
            withTimeout(120_000) { deferred.await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            results.remove(id)
            throw LkError.Message("ЛК не ответил вовремя, попробуй позже")
        }
        val data = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: throw LkError.Message("Сайт ответил непонятно")
        (data["error"] as? JsonPrimitive)?.contentOrNull?.let { error ->
            if (error == "auth") throw LkError.NeedLogin()
            Regex("""^http (\d+) ?(.*)$""").find(error)?.let { m ->
                val status = m.groupValues[1].toInt()
                if (LkError.busy(status)) throw LkError.ServerBusy(status, m.groupValues[2].toIntOrNull())
            }
            throw LkError.Message("Сайт ответил ошибкой: $error")
        }
        fun text(key: String) = (data[key] as? JsonPrimitive)?.contentOrNull
        FetchResult(
            days = data["days"] as? JsonArray ?: JsonArray(emptyList()),
            group = text("group"),
            program = text("program"),
            fullName = text("name"),
            photo = text("photo"),
            sessionUntil = (data["sessionUntil"] as? JsonPrimitive)?.doubleOrNull?.let { Instant.ofEpochSecond(it.toLong()) },
            plan = (data["plan"] as? JsonObject)?.toString(),
        )
    }
}
