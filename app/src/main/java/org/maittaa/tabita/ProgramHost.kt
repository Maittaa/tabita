package org.maittaa.tabita

import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import java.time.LocalDate

const val PSUB_PLAN = 0
/** 5.56: «Неделя» — обзор, правки, реальный расход. */
const val PSUB_WEEK = 1
const val PSUB_CHARTS = 2
const val PSUB_MONTH = 3

/**
 * «Программа» (5.30): одна вкладка вместо трёх — план к цели, графики и самочувствие.
 * Сверху — короткие вкладки-разделы без прокрутки; раздел запоминается.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProgramHost(act: MainActivity, modifier: Modifier, openDay: (LocalDate) -> Unit) {
    var sub by remember { mutableIntStateOf(act.prefs.programSub.let { if (it !in 0..3) 0 else it }) }
    var monthStart by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    Column(modifier.fillMaxSize()) {
        // 5.31: шапка как во вкладке «Тренировки» — заголовок, справочник и вкладки-разделы
        androidx.compose.material3.TopAppBar(
            title = { Text(t("Программа")) },
            actions = {
                androidx.compose.material3.IconButton({ act.openSettings.value = "ref" }) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.MenuBook, t("Справочник"))
                }
            },
        )
        // 5.38: значок + короткая подпись, вкладки равной ширины — как в «Тренировках»
        IconTabRow(sub, listOf(
            t("Цель") to androidx.compose.material.icons.Icons.Filled.Flag,
            t("Неделя") to androidx.compose.material.icons.Icons.Filled.DateRange,
            t("Графики") to androidx.compose.material.icons.Icons.Filled.Insights,
            t("Итоги") to androidx.compose.material.icons.Icons.Filled.CalendarMonth,
        )) { i -> sub = i; act.prefs.programSub = i; Log.d("UI", "программа: $i") }
        Box(Modifier.weight(1f)) {
            val m = Modifier.fillMaxSize()
            when (sub) {
                PSUB_PLAN -> PlanScreen(act, m)
                PSUB_WEEK -> WeekScreen(act, m)
                PSUB_CHARTS -> StatsScreen(act.repo, act.prefs, m, openDay = openDay,
                    trainCharts = { mod -> Box(mod) { org.maittaa.tabita.train.ProgressTab(act.train, androidx.compose.foundation.layout.PaddingValues()) } })
                else -> MonthView(act.repo, act.prefs, LocalDate.parse(monthStart), onMonth = { monthStart = it.withDayOfMonth(1).toString() },
                    openDay = openDay, modifier = m)
            }
        }
    }
}
