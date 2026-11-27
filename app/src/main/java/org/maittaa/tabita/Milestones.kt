package org.maittaa.tabita

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 5.67: вехи по числу заполненных дней. Ничего не прячется до вехи — веха сообщает, что к этому
 * моменту данных стало достаточно, и ведёт туда, где это видно.
 *  14 — хватает для первых находок «Что влияет» (≥7 дней, ≥3 с и без) и «Сравнить» (≥7 пар).
 *  30 — расход калорий по весу (≥28 дней, Hall & Chow 2011) и полный месяц итогов.
 *  66 — в среднем столько закрепляется привычка (Lally и соавт., 2010; разброс 18–254 дня).
 * 14 и 30 — удобные точки по готовности данных, не научные пороги.
 */
object Milestones {
    val DAYS = listOf(14, 30, 66)

    /** Веха, которую пора показать: наибольшая достигнутая, ещё не просмотренная. */
    fun due(total: Int, seen: Int): Int? = DAYS.lastOrNull { total >= it }?.takeIf { it > seen }

    fun title(m: Int): String = when (m) {
        14 -> t("2 недели записей")
        30 -> t("Месяц записей")
        else -> t("66 дней записей")
    }

    fun text(m: Int): String = when (m) {
        14 -> t("Данных хватает для первых выводов: в «Итогах» — «Что влияет» на ваш индекс и оценку дня, а в «Графиках» можно сравнить два показателя между собой.")
        30 -> t("Итоги месяца теперь полные. Если цель связана с весом — во вкладке «Неделя» можно уточнить расход калорий по реальной динамике веса: для этого нужно не меньше 28 дней.")
        else -> t("В среднем столько нужно, чтобы привычка стала автоматической (у разных людей — от 18 до 254 дней). Посмотрите на отметки: то, что делается само собой, можно убрать — записывать станет меньше.")
    }

    fun button(m: Int): String = when (m) {
        14 -> t("Итоги")
        30 -> t("Неделя")
        else -> t("Изменить отметки")
    }
}

@Composable
fun MilestoneCard(m: Int, total: Int, onGo: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp).testTag("milestone_$m"),
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EmojiEvents, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(Milestones.title(m), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.weight(1f))
                Text(t("всего %d дн.", total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Text(Milestones.text(m), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onDismiss, Modifier.testTag("milestone_ok")) { Text(t("Понятно"), color = MaterialTheme.colorScheme.onTertiaryContainer) }
                TextButton(onGo, Modifier.testTag("milestone_go")) {
                    Text(Milestones.button(m), color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
