package org.maittaa.tabita.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.maittaa.tabita.SectionCard
import java.time.LocalDate

/**
 * 5.24: плитка «Тренировка» в чек-листе — то же решение, что и на вкладке «Тренировки»:
 * тренироваться ли сегодня, план, перенос и советы по восстановлению.
 */
data class TrainTileInfo(val value: String, val sub: String, val accent: Boolean) {
    companion object {
        /** Кратко для уменьшенной плитки. */
        fun of(m: AppModel, date: LocalDate): TrainTileInfo {
            val today = LocalDate.now()
            val done = m.history.firstOrNull { it.date == date.toString() }
            if (done != null) return TrainTileInfo("Сделано ✓", "${done.title} · ${done.durationSec / 60} мин", true)
            if (date != today) {
                val p = m.program?.let { Schedule.dayOn(it, m.overrides, date) }
                return TrainTileInfo(if (p != null) (if (date < today) "Пропущена" else "По плану") else "Отдых", p?.name ?: "", false)
            }
            if (m.program == null) return TrainTileInfo("Нет программы", "Собрать — на вкладке «Тренировки»", false)
            val planned = m.todayPlanned()
            val r = m.readiness
            val v = r?.let { TodayAdvice.verdict(it) }
            if (planned == null) {
                val next = m.suggestion()
                return TrainTileInfo(if (v == null || v == Verdict.GO) "Отдых" else "Отдых — кстати", next?.let { "Дальше: ${it.day.name}" } ?: "", false)
            }
            val bad = r?.reasons?.filter { it.tone == Tone.BAD }?.mapNotNull { it.short }?.take(2)?.joinToString(" · ")
            return when (v) {
                Verdict.SKIP -> TrainTileInfo("Лучше перенести", listOfNotNull(planned.name, bad).joinToString(" · "), true)
                Verdict.EASY -> TrainTileInfo("Облегчённо", listOfNotNull(planned.name, bad).joinToString(" · "), true)
                else -> TrainTileInfo(planned.name, if (r != null) "Готовность в норме — можно" else "По плану", true)
            }
        }
    }
}

/** Полная плитка в чек-листе. [onOpen] — перейти на вкладку «Тренировки». */
@Composable
fun TrainDayBlock(m: AppModel, title: String, date: LocalDate, onOpen: () -> Unit) {
    val a = LocalTrainActions.current
    SectionCard(title, action = {
        TextButton(onOpen, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Открыть") }
    }) {
        val today = LocalDate.now()
        val done = m.history.firstOrNull { it.date == date.toString() }
        when {
            done != null -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Сделано: ${done.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${done.durationSec / 60} мин · упражнений ${done.exercises.size}" + (done.wellbeing.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            date != today -> {
                val i = TrainTileInfo.of(m, date)
                Text(listOf(i.value, i.sub).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            m.program == null -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Программы пока нет — приложение соберёт её под цель, дни и инвентарь.", style = MaterialTheme.typography.bodySmall)
                Button({ m.screen = Screen.Generator; onOpen() }) { Text("Создать программу") }
            }
            else -> {
                val start: (ProgramDay) -> Unit = { d -> a?.askNotifications?.invoke(); if (m.draft == null) m.start(d); m.screen = Screen.Workout; onOpen() }
                if (m.draft != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Тренировка не закончена: ${m.draft?.plan?.title ?: ""}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Button({ m.screen = Screen.Workout; onOpen() }) { Text("Продолжить") }
                } else m.readiness?.let { r ->
                    TodayCard(m, r, onStart = start)
                    // всё в норме — кнопка старта прямо в плитке (при «облегчённо/перенести» кнопки есть в карточке)
                    val planned = m.todayPlanned()
                    if (planned != null && TodayAdvice.verdict(r) == Verdict.GO)
                        Button({ start(planned) }, Modifier.fillMaxWidth()) { Text("Начать: ${planned.name}") }
                } ?: run {
                    // данных дневника нет — просто план
                    val sug = m.suggestion()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(sug?.let { "${it.label}: ${it.day.name}" } ?: "Сегодня отдых", style = MaterialTheme.typography.bodyMedium)
                        m.todayPlanned()?.let { d -> OutlinedButton({ start(d) }) { Text("Начать тренировку") } }
                    }
                }
                // советы по восстановлению — коротко, когда тренировку лучше облегчить или перенести
                m.readiness?.let { r ->
                    if (TodayAdvice.verdict(r) != Verdict.GO && m.draft == null) {
                        TodayAdvice.recovery(m.tabitaData, r, today, m.body.weightKg).take(2).forEach { tip ->
                            Column(Modifier.fillMaxWidth()) {
                                Text(tip.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Text(tip.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
