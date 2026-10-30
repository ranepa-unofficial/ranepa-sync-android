package com.qteb.ranepa.sync.system

import com.qteb.ranepa.sync.core.json
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/**
 * Правила проверки новой версии — без Android, чтобы проверять тестами.
 * Манифест `update.json` лежит в корне репозитория; как его обновлять
 * при выпуске — в docs/release.md.
 */
object UpdateRules {
    /** Откуда установлено приложение: обновлять можно только оттуда же —
     * подписи сборок в магазинах и на GitHub разные. */
    enum class Channel(val storeName: String) {
        PLAY("Google Play"), RUSTORE("RuStore"), GITHUB("GitHub");

        companion object {
            /** `com.android.vending` — Play, `ru.vk.store` — RuStore, остальное
             * (null, adb, браузер, файловый менеджер) — APK с GitHub. */
            fun fromInstaller(installer: String?): Channel = when (installer) {
                "com.android.vending" -> PLAY
                "ru.vk.store" -> RUSTORE
                else -> GITHUB
            }

            fun parse(name: String?): Channel? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }

    @Serializable
    data class Manifest(val github: GitHub? = null, val minimum: Minimum? = null) {
        /** Последний APK в GitHub Releases. */
        @Serializable
        data class GitHub(val latest: String, val latestCode: Int, val notes: String? = null, val url: String)

        /** Самая старая сборка, которая ещё работает, — общая для всех каналов. */
        @Serializable
        data class Minimum(val version: String, val code: Int = 0)
    }

    sealed interface Status {
        data object None : Status
        /** Есть новая версия — шторка, точка на аватаре и строка в аккаунте. */
        data class Available(val latest: String, val notes: String?) : Status
        /** Эта версия больше не работает — экран поверх всего приложения. */
        data object Required : Status
    }

    /** Загружать манифест не чаще раза в 3 часа. */
    val downloadInterval: Duration = Duration.ofHours(3)
    /** После «Позже» шторка возвращается через 3 дня, о более новой версии — сразу. */
    val sheetInterval: Duration = Duration.ofDays(3)

    fun parseManifest(text: String): Manifest? = runCatching { json.decodeFromString<Manifest>(text) }.getOrNull()

    /** Версии вида «1.2.3»: 1.2 и 1.2.0 равны, 1.10 новее 1.9. */
    fun compareVersions(a: String, b: String): Int {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    /** Нужно ли обновиться обязательно: общий минимум по номеру сборки. */
    fun required(manifest: Manifest?, versionCode: Int): Boolean =
        manifest?.minimum?.let { versionCode < it.code } ?: false

    /** Решение для APK с GitHub. Play и RuStore о новой версии сообщают сами, по манифесту — только минимум. */
    fun githubStatus(manifest: Manifest, versionCode: Int): Status {
        if (required(manifest, versionCode)) return Status.Required
        val github = manifest.github ?: return Status.None
        return if (versionCode < github.latestCode) Status.Available(github.latest, github.notes) else Status.None
    }

    /** Пора ли снова скачивать манифест. Отметка из будущего (переводили часы) — считаем давней. */
    fun downloadDue(checkedAt: Instant?, now: Instant): Boolean {
        checkedAt ?: return true
        val gap = Duration.between(checkedAt, now)
        return gap.isNegative || gap >= downloadInterval
    }

    /** Показать ли шторку «Вышла версия [latest]»: впервые, о новой версии или через 3 дня после «Позже». */
    fun offerSheet(latest: String, shownVersion: String?, shownAt: Instant?, now: Instant): Boolean {
        if (shownVersion != latest || shownAt == null) return true
        val gap = Duration.between(shownAt, now)
        return gap.isNegative || gap >= sheetInterval
    }
}
