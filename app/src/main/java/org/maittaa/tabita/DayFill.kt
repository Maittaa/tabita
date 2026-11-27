package org.maittaa.tabita

import java.time.LocalDate

/**
 * 5.64: меньше ручного ввода. «Как вчера» — перенести вчерашние отметки и «что использовалось»
 * в незаполненные пункты сегодня (с подтверждением и выбором); счёт пунктов, которые заполнились сами.
 */
object DayCopy {
    data class Candidate(val item: TemplateItem, val value: String, val text: String)

    /** Что можно перенести со вчера: ручные отметки и списки, вчера заполненные, сегодня пустые. */
    fun fromYesterday(template: List<TemplateItem>, entries: Map<String, Map<String, String>>, date: LocalDate): List<Candidate> {
        val y = entries[date.minusDays(1).key()] ?: return emptyList()
        val d = entries[date.key()] ?: emptyMap()
        return template.filter { !it.hidden && it.source == Source.NONE && (it.type == ItemType.CHECK || it.type == ItemType.MULTI) }
            .mapNotNull { item ->
                val v = y[item.id]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (!d[item.id].isNullOrBlank()) return@mapNotNull null
                when (item.type) {
                    ItemType.CHECK -> Candidate(item, v, item.displayName + if (v == "2") " +" else "")
                    else -> parseCounts(v).takeIf { it.isNotEmpty() }?.let { c ->
                        Candidate(item, v, item.displayName + ": " + c.entries.joinToString(", ") { (k, n) -> if (n > 1) "${t(k)} ×$n" else t(k) })
                    }
                }
            }
    }

    fun apply(repo: Repo, date: LocalDate, picked: List<Candidate>) {
        picked.forEach { repo.setValue(date.key(), it.item.id, it.value) }
        Log.i("Day", "как вчера: ${picked.map { it.item.id }}")
    }

    /** Сколько пунктов дня заполнилось само — с часов, весов, Health Connect. */
    fun automatic(template: List<TemplateItem>, day: Map<String, String>): Int =
        template.count { !it.hidden && it.source != Source.NONE && !day[it.id].isNullOrBlank() && day[manualKey(it.id)] == null }
}
