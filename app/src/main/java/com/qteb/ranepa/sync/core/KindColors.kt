package com.qteb.ranepa.sync.core

/** Цвета типов пар — как в iOS-версии: полоска системным цветом, подпись темнее,
 * чтобы читалась на светлом фоне. Значения ARGB: (светлая тема, тёмная). */
object KindColors {
    fun stripe(kind: String): Pair<Long, Long> = when (kind) {
        "лекция" -> 0xFF007AFF to 0xFF0A84FF
        "семинар" -> 0xFF34C759 to 0xFF30D158
        "физкультура" -> 0xFFFF9500 to 0xFFFF9F0A
        "лабораторная" -> 0xFFAF52DE to 0xFFBF5AF2
        "консультация" -> 0xFF30B0C7 to 0xFF40C8E0
        "экзамен", "зачёт" -> 0xFFFF2D55 to 0xFFFF375F
        else -> 0xFF8E8E93 to 0xFF8E8E93
    }

    fun text(kind: String): Pair<Long, Long> = when (kind) {
        "лекция" -> 0xFF0062CC to 0xFF409CFF
        "семинар" -> 0xFF248A3D to 0xFF30D158
        "физкультура" -> 0xFFC93400 to 0xFFFF9F0A
        "лабораторная" -> 0xFF8944AB to 0xFFDA8FFF
        "консультация" -> 0xFF0071A4 to 0xFF5DE6FF
        "экзамен", "зачёт" -> 0xFFD30F45 to 0xFFFF6482
        else -> 0xFF6C6C70 to 0xFFAEAEB2
    }

    /** Акцент приложения: бордовый, в тёмной теме светлее. */
    val accent: Pair<Long, Long> = 0xFF8E1B2E to 0xFFE5737F
}
