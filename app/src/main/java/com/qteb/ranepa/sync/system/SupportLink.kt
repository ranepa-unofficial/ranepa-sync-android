package com.qteb.ranepa.sync.system

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

/**
 * Ссылка на бота поддержки. В параметре start бот получает версию приложения,
 * систему и модель, например `app-1-0-0-1_android-16_pixel-9`. Формат общий
 * с iOS-версией (`app-2-2-0-30_ios-27-0-1_iphone-17-pro-max`).
 */
object SupportLink {
    const val BOT = "ranepa_sync_robot"
    /** Telegram принимает в start не больше 64 символов: латиницу, цифры, `_` и `-`. */
    const val MAX_LENGTH = 64

    fun uri(context: Context): Uri {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val start = startParameter(
            versionName = info.versionName.orEmpty(),
            versionCode = PackageInfoCompat.getLongVersionCode(info),
            osVersion = Build.VERSION.RELEASE,
            model = Build.MODEL + if (isEmulator()) "-emulator" else "",
        )
        return Uri.parse("https://t.me/$BOT?start=$start")
    }

    /**
     * «app-<версия>-<сборка>_android-<версия системы>_<модель>». Части приложения
     * и системы не обрезаются; при нехватке места укорачивается только модель,
     * а если для неё места нет совсем — она не передаётся.
     */
    fun startParameter(versionName: String, versionCode: Long, osVersion: String, model: String): String {
        val head = "app-${slug("$versionName-$versionCode")}_android-${slug(osVersion)}"
        val room = MAX_LENGTH - head.length - 1
        val device = slug(slug(model).take(maxOf(room, 0)))
        return if (device.isEmpty()) head else "${head}_$device"
    }

    /** Строчные латинские буквы и цифры, всё остальное (и кириллица тоже) — одиночные дефисы. */
    fun slug(text: String): String =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.joinToString("-")

    /** Эмулятор Android Studio: у него «generic»-отпечаток и виртуальное железо goldfish или ranchu. */
    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") ||
            Build.HARDWARE == "goldfish" || Build.HARDWARE == "ranchu" ||
            Build.PRODUCT.startsWith("sdk") || Build.MODEL.startsWith("sdk_gphone")
}
