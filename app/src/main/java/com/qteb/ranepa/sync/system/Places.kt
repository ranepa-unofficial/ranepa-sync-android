package com.qteb.ranepa.sync.system

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import com.qteb.ranepa.sync.core.Keys
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.core.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class Point(val lat: Double, val lon: Double)

/**
 * Где проходит пара: адрес из строки аудитории ЛК и точка на карте.
 * «1 - 2262 (24) П+ПК Блок 2F (Вернадского, 84 - корпус 1)» →
 * «Москва, проспект Вернадского, 84, корпус 1».
 */
object Places {
    /** Адрес в последних скобках строки аудитории. */
    fun address(room: String): String? {
        // Физкультура: у стадиона и спорткомплекса в ЛК нет нормального адреса.
        // Стадион — «территория перед 6 корпусом», бассейн и залы — корпус 2
        // (ranepa.ru/sveden/objects).
        val lower = room.lowercase()
        if (lower.contains("стадион") || lower.contains("кфк")) return "Вернадского, 82, стадион кампуса"
        if (lower.contains("бассейн") || lower.contains("спорткомплекс")) return "Вернадского, 82, корпус 2"
        val open = room.lastIndexOf('(')
        val close = room.lastIndexOf(')')
        if (open < 0 || close <= open) return null
        val raw = room.substring(open + 1, close).trim()
        // В скобках бывает и вместимость: «(24)».
        if (raw.none { it.isLetter() }) return null
        return raw.replace(" - ", ", ")
    }

    /** «Вернадского, 84, корпус 1» → «Москва, проспект Вернадского, 84, корпус 1». */
    fun query(address: String): String {
        var a = address
        if (a.startsWith("Вернадского")) a = "проспект $a"
        return "Москва, $a"
    }

    private fun cache(context: Context): Map<String, Point> {
        val raw = Store(context).string(Keys.PLACES) ?: return emptyMap()
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return emptyMap()
        return obj.mapNotNull { (k, v) ->
            val c = (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull } ?: return@mapNotNull null
            if (c.size == 2) k to Point(c[0], c[1]) else null
        }.toMap()
    }

    /** Точка корпуса без геокодера: по схеме кампуса или из кэша. */
    fun cachedLocation(context: Context, address: String): Point? =
        Campus.building(address)?.center ?: cache(context)[address]

    /** Точка корпуса: по схеме кампуса, из кэша или геокодером. null — не нашлось. */
    suspend fun locate(context: Context, address: String): Point? {
        cachedLocation(context, address)?.let { return it }
        // Без номера дома геокодер находит только город и ставит точку в центр
        // Москвы. Такой адрес на карте не показываем.
        if (address.none { it.isDigit() } || !Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale("ru"))
        val found = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocationName(query(address), 1) { cont.resume(it.firstOrNull()) }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query(address), 1)?.firstOrNull()
                }
            }.getOrNull()
        } ?: return null
        if (found.thoroughfare == null || found.subThoroughfare?.any { it.isDigit() } != true) return null
        val point = Point(found.latitude, found.longitude)
        val updated = cache(context) + (address to point)
        Store(context).setString(Keys.PLACES, JsonObject(updated.mapValues { (_, p) ->
            JsonArray(listOf(JsonPrimitive(p.lat), JsonPrimitive(p.lon)))
        }).toString())
        return point
    }
}

/** Приложения карт, которые умеют открываться на точке. */
enum class MapApp(val title: String, private val pkg: String) {
    YANDEX("Яндекс Карты", "ru.yandex.yandexmaps"),
    DGIS("2ГИС", "ru.dublgis.dgismobile"),
    GOOGLE("Google Maps", "com.google.android.apps.maps");

    /** Пакеты перечислены в `<queries>` манифеста — иначе Android 11+ их не покажет. */
    fun installed(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(pkg, 0)
    }.isSuccess

    fun intent(point: Point?, name: String, address: String): Intent {
        val q = Uri.encode(name)
        val uri = when (this) {
            YANDEX -> if (point != null) "yandexmaps://maps.yandex.ru/?pt=${point.lon},${point.lat}&z=17&l=map"
                else "yandexmaps://maps.yandex.ru/?text=${Uri.encode(address)}"
            DGIS -> if (point != null) "dgis://2gis.ru/geo/${point.lon},${point.lat}"
                else "dgis://2gis.ru/search/${Uri.encode(address)}"
            GOOGLE -> if (point != null) "geo:${point.lat},${point.lon}?q=${point.lat},${point.lon}($q)"
                else "geo:0,0?q=${Uri.encode(address)}"
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply { if (this@MapApp == GOOGLE) setPackage(pkg) }
    }

    companion object {
        fun installedApps(context: Context): List<MapApp> = entries.filter { it.installed(context) }

        /** Выбранное в настройках приложение; null — спрашивать каждый раз. */
        fun preferred(context: Context): MapApp? =
            Store(context).string(Keys.MAP_APP)?.let { name -> entries.firstOrNull { it.name == name } }
                ?.takeIf { it.installed(context) }

        fun setPreferred(context: Context, app: MapApp?) = Store(context).setString(Keys.MAP_APP, app?.name)

        /** Системный выбор карт по ссылке geo:, если своё приложение не выбрано. */
        fun systemIntent(point: Point?, name: String, address: String): Intent {
            val uri = if (point != null) "geo:${point.lat},${point.lon}?q=${point.lat},${point.lon}(${Uri.encode(name)})"
                else "geo:0,0?q=${Uri.encode(address)}"
            return Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        }
    }
}
