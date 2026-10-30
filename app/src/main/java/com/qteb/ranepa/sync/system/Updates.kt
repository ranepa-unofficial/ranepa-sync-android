package com.qteb.ranepa.sync.system

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.qteb.ranepa.sync.BuildConfig
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.Keys
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.core.json
import com.qteb.ranepa.sync.system.UpdateRules.Channel
import com.qteb.ranepa.sync.system.UpdateRules.Manifest
import com.qteb.ranepa.sync.system.UpdateRules.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import ru.rustore.sdk.appupdate.manager.factory.RuStoreAppUpdateManagerFactory
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlin.coroutines.resume
import ru.rustore.sdk.appupdate.model.AppUpdateOptions as RuStoreOptions
import ru.rustore.sdk.appupdate.model.AppUpdateType as RuStoreType
import ru.rustore.sdk.appupdate.model.InstallStatus as RuStoreInstallStatus
import ru.rustore.sdk.appupdate.model.UpdateAvailability as RuStoreAvailability

/**
 * Новая версия: откуда установлено приложение, оттуда и обновляем.
 * Google Play и RuStore сообщают о версии через свои SDK, APK с GitHub —
 * через `update.json` в репозитории. Минимум из манифеста общий для всех.
 */
object Updates {
    const val MANIFEST_RAW = "https://raw.githubusercontent.com/ranepa-unofficial/ranepa-sync-android/main/update.json"
    const val MANIFEST_CDN = "https://cdn.jsdelivr.net/gh/ranepa-unofficial/ranepa-sync-android@main/update.json"

    /** Что предложить: [key] — по нему «Позже» и точка на аватаре узнают версию. */
    @Serializable
    data class Offer(val channel: Channel, val code: Int, val version: String? = null, val notes: String? = null) {
        val key: String get() = "$channel:$code"
        val title: String get() = version?.let { "Вышла версия $it" } ?: "Вышла новая версия"
        val button: String get() = if (channel == Channel.GITHUB) "Обновить" else "Обновить в ${channel.storeName}"
    }

    val current: String get() = BuildConfig.VERSION_NAME
    private val currentCode: Int get() = BuildConfig.VERSION_CODE

    fun channel(context: Context): Channel {
        if (BuildConfig.DEBUG) Channel.parse(Store(context).string(Keys.UPDATE_DEBUG_CHANNEL))?.let { return it }
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= 30) context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            else @Suppress("DEPRECATION") context.packageManager.getInstallerPackageName(context.packageName)
        }.getOrNull()
        return Channel.fromInstaller(installer)
    }

    /** Отладочный манифест вместо настоящего — и без паузы в 3 часа. */
    private fun debugManifest(d: Store): String? = if (BuildConfig.DEBUG) d.string(Keys.UPDATE_DEBUG_MANIFEST) else null

    private fun overridden(d: Store) = BuildConfig.DEBUG &&
        (d.has(Keys.UPDATE_DEBUG_MANIFEST) || d.has(Keys.UPDATE_DEBUG_CHANNEL))

    fun cachedManifest(d: Store): Manifest? = (debugManifest(d) ?: d.string(Keys.UPDATE_MANIFEST))?.let(UpdateRules::parseManifest)

    /** Последняя обязательная проверка из кэша: экран блокировки показывается и без сети. */
    fun requiredFromCache(context: Context): Boolean = UpdateRules.required(cachedManifest(Store(context)), currentCode)

    /** Предложение из прошлой проверки, если эта версия ещё не установлена. */
    fun cachedOffer(context: Context): Offer? = Store(context).string(Keys.UPDATE_OFFER)
        ?.let { runCatching { json.decodeFromString<Offer>(it) }.getOrNull() }
        ?.takeIf { it.code > currentCode }

    /** Итог проверки: Required, Available или None. */
    data class Result(val required: Boolean, val offer: Offer?)

    /** Проверить, если пора (раз в 3 часа). null — проверять ещё рано. */
    suspend fun check(context: Context, now: Instant = Clock.now): Result? {
        val d = Store(context)
        if (!overridden(d) && !UpdateRules.downloadDue(d.time(Keys.UPDATE_CHECKED_AT), now)) return null
        val manifest = debugManifest(d)?.let(UpdateRules::parseManifest) ?: download()?.also {
            d.setString(Keys.UPDATE_MANIFEST, json.encodeToString(Manifest.serializer(), it))
        } ?: cachedManifest(d)
        d.setTime(Keys.UPDATE_CHECKED_AT, now)
        if (UpdateRules.required(manifest, currentCode)) return Result(true, null)
        val channel = channel(context)
        val offer = when (channel) {
            Channel.GITHUB -> manifest?.let { UpdateRules.githubStatus(it, currentCode) }
                .let { it as? Status.Available }
                ?.let { Offer(channel, manifest!!.github!!.latestCode, it.latest, it.notes) }
            Channel.PLAY, Channel.RUSTORE -> storeCode(context, channel)?.let { code ->
                // Имя версии магазины не отдают: берём из манифеста, если номер сборки совпал.
                val github = manifest?.github?.takeIf { it.latestCode == code }
                Offer(channel, code, github?.latest, github?.notes)
            }
        }
        d.setString(Keys.UPDATE_OFFER, offer?.let { json.encodeToString(Offer.serializer(), it) })
        return Result(false, offer)
    }

    private suspend fun download(): Manifest? = withContext(Dispatchers.IO) {
        for (url in listOf(MANIFEST_RAW, MANIFEST_CDN)) {
            runCatching {
                // Только GET файла: без cookies, профиля и номера телефона.
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.useCaches = false
                if (connection.responseCode == 200) UpdateRules.parseManifest(connection.inputStream.bufferedReader().use { it.readText() }) else null
            }.getOrNull()?.let { return@withContext it }
        }
        null
    }

    /** Номер сборки, которую предлагает магазин; null — обновления нет или магазин недоступен. */
    private suspend fun storeCode(context: Context, channel: Channel): Int? = runCatching {
        when (channel) {
            Channel.PLAY -> AppUpdateManagerFactory.create(context).requestAppUpdateInfo()
                .takeIf { it.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE }
                ?.availableVersionCode()
            Channel.RUSTORE -> ruStoreInfo(context)
                ?.takeIf { it.updateAvailability == RuStoreAvailability.UPDATE_AVAILABLE }
                ?.availableVersionCode?.toInt()
            Channel.GITHUB -> null
        }
    }.getOrNull()?.takeIf { it > currentCode }

    /** Сведения RuStore. Нет RuStore или не вошли в него — null, без ошибок пользователю. */
    private suspend fun ruStoreInfo(context: Context) = suspendCancellableCoroutine { cont ->
        RuStoreAppUpdateManagerFactory.create(context).getAppUpdateInfo()
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
    }

    /**
     * «Обновить»: магазин скачивает в фоне и перезапускает приложение,
     * APK — ссылка на файл в браузере. [required] — сразу и целиком.
     */
    fun start(activity: Activity, channel: Channel, required: Boolean) {
        when (channel) {
            Channel.PLAY -> {
                val manager = AppUpdateManagerFactory.create(activity)
                val type = if (required) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE
                manager.appUpdateInfo.addOnSuccessListener { info ->
                    if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE || !info.isUpdateTypeAllowed(type)) {
                        openStorePage(activity, channel)
                        return@addOnSuccessListener
                    }
                    if (type == AppUpdateType.FLEXIBLE) {
                        manager.registerListener(object : InstallStateUpdatedListener {
                            override fun onStateUpdate(state: com.google.android.play.core.install.InstallState) {
                                if (state.installStatus() == InstallStatus.DOWNLOADED) {
                                    manager.unregisterListener(this)
                                    manager.completeUpdate()
                                }
                            }
                        })
                    }
                    manager.startUpdateFlow(info, activity, AppUpdateOptions.defaultOptions(type))
                }.addOnFailureListener { openStorePage(activity, channel) }
            }
            Channel.RUSTORE -> {
                val manager = RuStoreAppUpdateManagerFactory.create(activity)
                val type = if (required) RuStoreType.IMMEDIATE else RuStoreType.FLEXIBLE
                // Сведения годятся на один запуск — запрашиваем заново.
                manager.getAppUpdateInfo().addOnSuccessListener { info ->
                    if (info.updateAvailability != RuStoreAvailability.UPDATE_AVAILABLE) {
                        openStorePage(activity, channel)
                        return@addOnSuccessListener
                    }
                    if (!required) {
                        manager.registerListener(object : ru.rustore.sdk.appupdate.listener.InstallStateUpdateListener {
                            override fun onStateUpdated(state: ru.rustore.sdk.appupdate.model.InstallState) {
                                if (state.installStatus == RuStoreInstallStatus.DOWNLOADED) {
                                    manager.unregisterListener(this)
                                    manager.completeUpdate(RuStoreOptions.Builder().appUpdateType(RuStoreType.FLEXIBLE).build())
                                }
                            }
                        })
                    }
                    manager.startUpdateFlow(info, RuStoreOptions.Builder().appUpdateType(type).build())
                }.addOnFailureListener { openStorePage(activity, channel) }
            }
            Channel.GITHUB -> {
                val url = cachedManifest(Store(activity))?.github?.url ?: LATEST_APK
                open(activity, Uri.parse(url))
            }
        }
    }

    const val LATEST_APK = "https://github.com/ranepa-unofficial/ranepa-sync-android/releases/latest/download/ranepa.apk"

    /** SDK магазина не смог — страница приложения в магазине. */
    private fun openStorePage(activity: Activity, channel: Channel) {
        val pkg = activity.packageName.removeSuffix(".debug")
        open(activity, Uri.parse(when (channel) {
            Channel.PLAY -> "https://play.google.com/store/apps/details?id=$pkg"
            Channel.RUSTORE -> "https://www.rustore.ru/catalog/app/$pkg"
            Channel.GITHUB -> LATEST_APK
        }))
    }

    private fun open(activity: Activity, uri: Uri) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}
