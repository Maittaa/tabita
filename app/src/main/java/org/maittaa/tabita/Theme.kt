package org.maittaa.tabita

import android.app.Activity
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Оформление: режим (системный/светлый/тёмный) и акцент (системный Material You или свой цвет). */
object Look {
    var mode by mutableStateOf("system")     // system | light | dark
    var accent by mutableStateOf("dynamic")  // dynamic | ключ из [accents]

    fun init(p: Prefs) { mode = p.themeMode; accent = p.accent }

    val dynamicAvailable get() = Build.VERSION.SDK_INT >= 31

    /** Готовые акценты: ключ → (название, цвет). */
    val accents = listOf(
        "green" to ("Зелёный" to Color(0xFF2E5E4E)),
        "blue" to ("Синий" to Color(0xFF2F5DA8)),
        "indigo" to ("Индиго" to Color(0xFF4A4FB0)),
        "purple" to ("Фиолетовый" to Color(0xFF7A4BAE)),
        "pink" to ("Розовый" to Color(0xFFB0416E)),
        "red" to ("Красный" to Color(0xFFB3382C)),
        "orange" to ("Оранжевый" to Color(0xFFC25E12)),
        "amber" to ("Янтарный" to Color(0xFF9A6B00)),
        "teal" to ("Бирюзовый" to Color(0xFF00796B)),
        "slate" to ("Графит" to Color(0xFF4F5B66)),
    )
}

/** Внутри плитки карточка не рисует свой фон и обводку — их рисует плитка. */
val LocalInTile = compositionLocalOf { false }

private fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)

/** Схема Material 3 из одного цвета (упрощённая тональная палитра). */
fun schemeFrom(seed: Color, dark: Boolean): ColorScheme {
    val white = Color.White; val black = Color.Black
    val neutralDark = mix(Color(0xFF121316), seed, 0.04f)
    val neutralLight = mix(Color(0xFFFCFCFF), seed, 0.03f)
    return if (!dark) lightColorScheme(
        primary = seed, onPrimary = white,
        primaryContainer = mix(seed, white, 0.82f), onPrimaryContainer = mix(seed, black, 0.6f),
        secondary = mix(seed, Color(0xFF7A7A7A), 0.45f), onSecondary = white,
        secondaryContainer = mix(seed, white, 0.86f), onSecondaryContainer = mix(seed, black, 0.65f),
        tertiary = Color(0xFFB5761A), tertiaryContainer = Color(0xFFFFDDB5),
        background = neutralLight, surface = neutralLight,
        surfaceContainerLowest = white,
        surfaceContainerLow = mix(neutralLight, seed, 0.04f),
        surfaceContainer = mix(neutralLight, seed, 0.06f),
        surfaceContainerHigh = mix(neutralLight, seed, 0.09f),
        surfaceContainerHighest = mix(neutralLight, seed, 0.12f),
        surfaceVariant = mix(Color(0xFFE2E2E6), seed, 0.08f),
        outline = Color(0xFF75777C), outlineVariant = Color(0xFFC6C6CC),
    ) else darkColorScheme(
        primary = mix(seed, white, 0.55f), onPrimary = mix(seed, black, 0.65f),
        primaryContainer = mix(seed, black, 0.35f), onPrimaryContainer = mix(seed, white, 0.8f),
        secondary = mix(mix(seed, white, 0.55f), Color(0xFF9A9A9A), 0.4f), onSecondary = black,
        secondaryContainer = mix(seed, Color(0xFF2A2C30), 0.65f), onSecondaryContainer = mix(seed, white, 0.85f),
        tertiary = Color(0xFFF0B862), tertiaryContainer = Color(0xFF5E4100),
        background = neutralDark, surface = neutralDark,
        surfaceContainerLowest = mix(neutralDark, black, 0.4f),
        surfaceContainerLow = mix(neutralDark, Color(0xFF2A2B30), 0.45f),
        surfaceContainer = mix(neutralDark, Color(0xFF2A2B30), 0.6f),
        surfaceContainerHigh = mix(neutralDark, Color(0xFF34353A), 0.7f),
        surfaceContainerHighest = mix(neutralDark, Color(0xFF3E3F44), 0.8f),
        surfaceVariant = mix(Color(0xFF44464C), seed, 0.08f),
        outline = Color(0xFF8E9096), outlineVariant = Color(0xFF45474C),
    )
}

/**
 * Системная тёмная Material You — как в настройках Android: фон — «контейнер» палитры обоев (тон ≈12),
 * карточки и плитки — залитые плашки самого светлого контейнера (тон ≈22) без обводки.
 * Вложенные поверхности — ещё на ступень светлее, чтобы читались внутри карточки.
 */
fun liftDark(dyn: ColorScheme): ColorScheme {
    val up = dyn.onSurface
    return dyn.copy(
        background = dyn.surfaceContainer, surface = dyn.surfaceContainer,
        surfaceDim = dyn.surfaceContainer,
        surfaceContainerLowest = dyn.surfaceContainerLow,
        surfaceContainerLow = dyn.surfaceContainerHighest,
        surfaceContainer = mix(dyn.surfaceContainerHighest, up, 0.04f),
        surfaceContainerHigh = mix(dyn.surfaceContainerHighest, up, 0.07f),
        surfaceContainerHighest = mix(dyn.surfaceContainerHighest, up, 0.10f),
        surfaceVariant = mix(dyn.surfaceContainerHighest, up, 0.08f),
    )
}

/** Карточки залиты без обводки (системная тёмная тема, как в настройках Android). */
val LocalFilledCards = compositionLocalOf { false }

/** Обводка карточек и плиток: в тёмной теме — заметная, как в Google Keep. */
@Composable
fun cardBorder(): BorderStroke {
    val dark = isDark()
    if (LocalFilledCards.current) return BorderStroke(0.dp, Color.Transparent)
    return BorderStroke(1.dp, if (dark) MaterialTheme.colorScheme.outline.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
}

@Composable
fun isDark(): Boolean = when (Look.mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isDark()
    val ctx = LocalContext.current
    val scheme = when {
        Look.accent == "dynamic" && Look.dynamicAvailable -> if (dark) liftDark(dynamicDarkColorScheme(ctx)) else dynamicLightColorScheme(ctx)
        else -> schemeFrom(Look.accents.firstOrNull { it.first == Look.accent }?.second?.second ?: Look.accents[0].second.second, dark)
    }
    // значки строки состояния под выбранную тему
    LaunchedEffect(dark) {
        (ctx as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(Color.Transparent.toArgb())
            else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = if (dark) SystemBarStyle.dark(Color.Transparent.toArgb())
            else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
        )
    }
    val filled = dark && Look.accent == "dynamic" && Look.dynamicAvailable
    androidx.compose.runtime.CompositionLocalProvider(LocalFilledCards provides filled) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Suppress("unused") private fun Activity.unused() = Unit
