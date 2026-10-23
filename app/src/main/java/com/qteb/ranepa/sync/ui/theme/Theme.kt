package com.qteb.ranepa.sync.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.qteb.ranepa.sync.core.KindColors

/** Палитра от бордового #8E1B2E — на телефонах без Material You и в превью. */
private val light = lightColorScheme(
    primary = Color(0xFF8E1B2E), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADB), onPrimaryContainer = Color(0xFF3F0010),
    secondary = Color(0xFF775657), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5DDDE), onSecondaryContainer = Color(0xFF2C1517),
    tertiary = Color(0xFF755A2F), tertiaryContainer = Color(0xFFFFDDB1),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFFF8F7), onBackground = Color(0xFF22191A),
    surface = Color(0xFFFFF8F7), onSurface = Color(0xFF22191A),
    surfaceVariant = Color(0xFFF4DDDE), onSurfaceVariant = Color(0xFF534344),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFEF1F1),
    surfaceContainer = Color(0xFFFAEBEB), surfaceContainerHigh = Color(0xFFF4E5E5),
    surfaceContainerHighest = Color(0xFFEEE0E0),
    outline = Color(0xFF857374), outlineVariant = Color(0xFFD8C1C2),
)

private val dark = darkColorScheme(
    primary = Color(0xFFFFB2B8), onPrimary = Color(0xFF561D26),
    primaryContainer = Color(0xFF72333C), onPrimaryContainer = Color(0xFFFFDADB),
    secondary = Color(0xFFE6BDBE), onSecondary = Color(0xFF44292B),
    secondaryContainer = Color(0xFF5B3F42), onSecondaryContainer = Color(0xFFF5DDDE),
    tertiary = Color(0xFFE5C18D), tertiaryContainer = Color(0xFF5B4319),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1112), onBackground = Color(0xFFF0DEDF),
    surface = Color(0xFF1A1112), onSurface = Color(0xFFF0DEDF),
    surfaceVariant = Color(0xFF534344), onSurfaceVariant = Color(0xFFD8C1C2),
    surfaceContainerLowest = Color(0xFF140C0D), surfaceContainerLow = Color(0xFF22191A),
    surfaceContainer = Color(0xFF271D1E), surfaceContainerHigh = Color(0xFF322828),
    surfaceContainerHighest = Color(0xFF3D3233),
    outline = Color(0xFFA08C8D), outlineVariant = Color(0xFF534344),
)

/** Тёмная ли тема сейчас — для цветов типов пар. */
val LocalDark = staticCompositionLocalOf { false }

/** [mode]: 0 — как в системе, 1 — светлая, 2 — тёмная. */
@Composable
fun RanepaTheme(mode: Int = 0, dynamic: Boolean = true, content: @Composable () -> Unit) {
    val isDark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        isDark -> dark
        else -> light
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalDark provides isDark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Цвет полоски типа пары. */
@Composable
fun kindStripe(kind: String, muted: Boolean = false): Color {
    if (muted) return if (LocalDark.current) Color(0xFF48484A) else Color(0xFFC7C7CC)
    val (l, d) = KindColors.stripe(kind)
    return Color(if (LocalDark.current) d else l)
}

/** Цвет подписи типа пары: в светлой теме темнее, чтобы читалась на светлом фоне. */
@Composable
fun kindText(kind: String): Color {
    val (l, d) = KindColors.text(kind)
    return Color(if (LocalDark.current) d else l)
}

/** Красный «сейчас»: линия времени и остаток пары. */
@Composable
fun nowRed(): Color = if (LocalDark.current) Color(0xFFFF6961) else Color(0xFFD70015)
