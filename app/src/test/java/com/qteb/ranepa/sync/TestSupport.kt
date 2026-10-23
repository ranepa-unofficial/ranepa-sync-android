package com.qteb.ranepa.sync

import android.content.SharedPreferences
import com.qteb.ranepa.sync.core.Lesson
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/** Тесты идут по московскому времени, как у студентов. */
fun moscowTests() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
}

fun date(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Instant =
    LocalDateTime.of(y, m, d, h, min).atZone(ZoneId.systemDefault()).toInstant()

fun lesson(start: Instant, subject: String = "Пара", kind: String = "лекция", minutes: Long = 80, cancelled: Boolean = false) =
    Lesson(start, start.plusSeconds(minutes * 60), subject, kind, "5 - 307", "", "", emptyList(), "", "", cancelled)

/** Настройки в памяти — вместо SharedPreferences телефона. */
class MemoryPrefs : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = values[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String) = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { changes[key] = values }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) values.clear()
            removed.forEach(values::remove)
            changes.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
        }
    }
}
