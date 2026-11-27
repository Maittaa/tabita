package org.maittaa.tabita

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * 5.70: цветные плитки в духе Material You. Каждая категория получает свой контейнер:
 * часть — прямо из ролей схемы (primary/secondary/tertiary container), часть — «смысловые» оттенки
 * (вода синяя, пульс красный…), гармонизированные с основным цветом темы, как custom colors в Material 3:
 * оттенок сдвигается к primary на половину разницы, но не больше 15°, а светлота и насыщенность берутся
 * как у контейнеров M3 (тёмная тема ≈ тон 30, светлая ≈ тон 90). Считается в OKLCH.
 */
enum class Tone(val hue: Double?) {
    SLEEP(295.0), STEPS(185.0), HEART(20.0), BODY(85.0), TRAIN(55.0), WATER(240.0), FOOD(140.0), MONEY(110.0),
    MIND(345.0), CALM(165.0),
    // роли схемы Material You
    PRIMARY(null), SECONDARY(null), TERTIARY(null),
}

data class ToneColors(val container: Color, val onContainer: Color)

object Tones {
    // ---- OKLab / OKLCH ----
    private fun lin(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun gam(c: Double) = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055

    /** (L, C, h°) */
    fun toLch(c: Color): DoubleArray {
        val r = lin(c.red.toDouble()); val g = lin(c.green.toDouble()); val b = lin(c.blue.toDouble())
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val A = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val B = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        return doubleArrayOf(L, hypot(A, B), (Math.toDegrees(atan2(B, A)) + 360) % 360)
    }

    private fun rgb(L: Double, C: Double, h: Double): DoubleArray {
        val A = C * cos(Math.toRadians(h)); val B = C * sin(Math.toRadians(h))
        val l = (L + 0.3963377774 * A + 0.2158037573 * B).pow(3)
        val m = (L - 0.1055613458 * A - 0.0638541728 * B).pow(3)
        val s = (L - 0.0894841775 * A - 1.2914855480 * B).pow(3)
        return doubleArrayOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
    }

    /** Цвет из OKLCH; насыщенность уменьшается, пока цвет не влезет в sRGB. */
    fun fromLch(L: Double, C: Double, h: Double): Color {
        var c = C
        repeat(40) {
            val x = rgb(L, c, h)
            if (x.all { it in -0.0001..1.0001 }) return Color(gam(x[0].coerceIn(0.0, 1.0)).toFloat(), gam(x[1].coerceIn(0.0, 1.0)).toFloat(), gam(x[2].coerceIn(0.0, 1.0)).toFloat())
            c *= 0.9
        }
        val x = rgb(L, 0.0, h)
        return Color(gam(x[0].coerceIn(0.0, 1.0)).toFloat(), gam(x[1].coerceIn(0.0, 1.0)).toFloat(), gam(x[2].coerceIn(0.0, 1.0)).toFloat())
    }

    /** Гармонизация как в Material 3: сдвиг оттенка к [source] на половину разницы, не больше 15°. */
    fun harmonize(hue: Double, source: Double): Double {
        val d = ((source - hue + 540) % 360) - 180
        val shift = min(abs(d) * 0.5, 15.0) * (if (d >= 0) 1 else -1)
        return (hue + shift + 360) % 360
    }

    fun colors(tone: Tone, primary: Color, dark: Boolean): ToneColors {
        val h = harmonize(tone.hue ?: 0.0, toLch(primary)[2])
        return if (dark) ToneColors(fromLch(0.34, 0.075, h), fromLch(0.92, 0.06, h))
        else ToneColors(fromLch(0.93, 0.045, h), fromLch(0.30, 0.09, h))
    }
}

/** Цвета плитки категории; null — цветные плитки выключены или у категории нет цвета. */
@Composable
@ReadOnlyComposable
fun toneColors(tone: Tone?): ToneColors? {
    if (tone == null || !Look.colorful) return null
    val cs = MaterialTheme.colorScheme
    return when (tone) {
        Tone.PRIMARY -> ToneColors(cs.primaryContainer, cs.onPrimaryContainer)
        Tone.SECONDARY -> ToneColors(cs.secondaryContainer, cs.onSecondaryContainer)
        Tone.TERTIARY -> ToneColors(cs.tertiaryContainer, cs.onTertiaryContainer)
        else -> Tones.colors(tone, cs.primary, cs.surface.luminanceDark())
    }
}

private fun Color.luminanceDark(): Boolean = (0.2126f * red + 0.7152f * green + 0.0722f * blue) < 0.5f

/** Мягкий вариант для больших карточек: контейнер наполовину смешан с поверхностью. */
fun ToneColors.soft(surface: Color, k: Float = 0.45f) = ToneColors(lerp(surface, container, k), onContainer)

/** Категория пункта дня. */
fun toneOf(item: TemplateItem): Tone? = when {
    item.source == Source.HC_SLEEP -> Tone.SLEEP
    item.source == Source.HC_STEPS -> Tone.STEPS
    item.source == Source.HC_EXERCISE -> Tone.TRAIN
    item.source == Source.HC_RESTING_HR -> Tone.HEART
    item.source == Source.HC_WEIGHT || item.type == ItemType.BODY -> Tone.BODY
    item.type == ItemType.TRAINING -> Tone.TRAIN
    item.type == ItemType.WATER -> Tone.WATER
    item.type == ItemType.MEALS -> Tone.FOOD
    item.type == ItemType.EXPENSES -> Tone.MONEY
    item.type == ItemType.WELLBEING -> Tone.MIND
    item.type == ItemType.SCALE -> Tone.PRIMARY
    item.type == ItemType.CHECK -> Tone.SECONDARY
    item.type == ItemType.MULTI -> Tone.TERTIARY
    item.type == ItemType.TIPS -> Tone.TERTIARY
    item.type == ItemType.GOAL -> Tone.PRIMARY
    item.type == ItemType.RHYTHM -> Tone.SECONDARY
    else -> null
}
