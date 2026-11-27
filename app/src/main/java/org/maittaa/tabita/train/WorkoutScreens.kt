package org.maittaa.tabita.train

import android.app.Activity
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

fun mmss(sec: Long) = "%d:%02d".format(sec / 60, sec % 60)

@Composable
fun ticker(periodMs: Long = 250): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(periodMs) } }
    return now
}

@Composable
fun KeepScreenOn() {
    val act = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        act?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

// ——— запись из истории ———

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(m: AppModel, a: Actions, s: Session, snack: SnackbarHostState) {
    var confirm by remember { mutableStateOf(false) }
    var showMd by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton({ m.screen = Screen.Home; m.tab = 1 }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                title = { Text(s.title.substringBefore(" — ")) },
                actions = { IconButton({ a.share(s) }) { Icon(Icons.Outlined.Share, "Отправить") } },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(prettyDate(s.date), style = MaterialTheme.typography.headlineSmall)
            Text(
                listOfNotNull(
                    formatDuration(s.durationSec),
                    s.week.takeIf { it.isNotBlank() }?.let { "неделя $it" },
                    s.wellbeing.takeIf { it.isNotBlank() }?.let { "самочувствие: $it" },
                ).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.exercises.forEach { r ->
                        Row {
                            Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (r.values.isEmpty()) "—" else r.values.joinToString(" · ") + if (r.seconds) " с" else "",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            )
                        }
                        r.load?.let { Text("рюкзак: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            if (s.notes.isNotBlank()) Text("Заметки: ${s.notes}")
            if (m.useHealth && m.hcStatus == HcStatus.OK) OutlinedButton({ m.saveToHealth(s) }, Modifier.fillMaxWidth()) {
                Text(if (s.hcSaved) "Записать в Health Connect ещё раз" else "Записать в Health Connect")
            }
            OutlinedButton({ a.saveAs(s) }, Modifier.fillMaxWidth()) { Text("Сохранить .md в файл…") }
            OutlinedButton({ a.share(s) }, Modifier.fillMaxWidth()) { Text("Отправить заметку") }
            TextButton({ showMd = !showMd }) { Text(if (showMd) "Скрыть текст заметки" else "Показать текст заметки") }
            if (showMd) Text(s.markdown, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            TextButton({ confirm = true }) { Text("Удалить из истории", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Удалить запись?") },
        text = { Text("Запись в Health Connect, если была, останется.") },
        confirmButton = { TextButton({ confirm = false; m.deleteSession(s.id) }) { Text("Удалить") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Отмена") } },
    )
}

// ——— пояснение по упражнению ———

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(name: String, templateText: String?, onClose: () -> Unit) {
    val info = Guide.find(name)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(info?.name ?: name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (!templateText.isNullOrBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Коротко", style = MaterialTheme.typography.labelMedium)
                        Text(templateText)
                    }
                }
            }
            if (info == null) {
                Text("Подробного описания для этого упражнения пока нет.")
                return@Column
            }
            Block("Что работает") { Text(info.muscles) }
            Block("Как делать") {
                info.steps.forEachIndexed { i, s ->
                    Row { Text("${i + 1}.", Modifier.width(24.dp), fontWeight = FontWeight.Bold); Text(s) }
                }
            }
            Block("Частые ошибки") { info.mistakes.forEach { Text("• $it") } }
            Block("Дыхание") { Text(info.breathing) }
            if (info.easier != "—") Block("Если тяжело") { Text(info.easier) }
            if (info.harder != "—") Block("Когда станет легко") { Text(info.harder) }
            info.note?.let { Block("Важно") { Text(it) } }
        }
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
