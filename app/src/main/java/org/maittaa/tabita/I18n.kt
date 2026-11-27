package org.maittaa.tabita

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.util.Locale

/**
 * Простая локализация: строки в коде пишутся по-русски, t() возвращает английский
 * перевод, если выбран английский. Язык хранится в Compose-состоянии,
 * поэтому интерфейс сразу перерисовывается при переключении.
 */
object I18n {
    /** 5.67: в тестах — собирать русские строки без английского перевода. */
    @Volatile var track = false
    val missing: MutableSet<String> = java.util.Collections.synchronizedSet(LinkedHashSet())

    var lang by mutableStateOf("ru")
    var currency by mutableStateOf("₽")
    val isEn get() = lang == "en"

    fun init(prefs: Prefs) {
        lang = resolve(prefs.lang)
        currency = prefs.currency
    }

    fun resolve(pref: String) = when (pref) {
        "ru", "en" -> pref
        else -> if (Locale.getDefault().language == "ru") "ru" else "en"
    }

    private val dowRu = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
    private val dowEn = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val monGenRu = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    private val monRu = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
    private val monEn = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    fun dayTitle(d: LocalDate) =
        if (isEn) "${dowEn[d.dayOfWeek.value - 1]}, ${monEn[d.monthValue - 1].take(3)} ${d.dayOfMonth}"
        else "${d.dayOfMonth} ${monGenRu[d.monthValue - 1]}, ${dowRu[d.dayOfWeek.value - 1]}"

    fun month(m: Int) = if (isEn) monEn[m - 1] else monRu[m - 1]
}

/** Перевод строки интерфейса. */
fun t(ru: String): String = if (I18n.isEn) EN[ru] ?: ru.also { if (I18n.track && ru.any { c -> c in 'А'..'я' || c == 'ё' }) I18n.missing += ru } else ru

/** Перевод с подстановкой: t("заполнено %d из %d", a, b) */
fun t(ru: String, vararg args: Any?): String = t(ru).format(*args)
