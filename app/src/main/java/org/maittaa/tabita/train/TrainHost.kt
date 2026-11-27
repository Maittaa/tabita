package org.maittaa.tabita.train

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.FileProvider

/**
 * Тренировки внутри Tabita.
 * Здесь — выбор файлов, разрешения, перезапуск для экранов тренировок.
 */
class Actions(
    val requestHealth: () -> Unit,
    val askNotifications: () -> Unit,
    val share: (Session) -> Unit,
    val saveAs: (Session) -> Unit,
    val exportBackup: () -> Unit = {},
    val exportCsv: () -> Unit = {},
    val importBackup: () -> Unit = {},
    /** Перезапуск после восстановления копии или удаления данных — все экраны читают данные заново. */
    val restart: () -> Unit = {},
)

val LocalTrainActions = staticCompositionLocalOf<Actions?> { null }


/** Перезапуск приложения: новый процесс — данные читаются заново. */
fun restartApp(act: Activity) {
    val i = act.packageManager.getLaunchIntentForPackage(act.packageName)
        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
    act.startActivity(i)
    act.finishAffinity()
    Runtime.getRuntime().exit(0)
}

@Composable
fun rememberTrainActions(act: ComponentActivity, model: AppModel): Actions {
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val healthPerm = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { model.refreshHealth() }
    val pending = remember { arrayOfNulls<Session>(1) }
    val fileSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val s = pending[0]
        if (uri != null && s != null) runCatching { model.storage.writeToUri(uri, s.markdown) }
            .onSuccess { model.message = "Файл сохранён" }.onFailure { model.message = "Ошибка: ${it.message}" }
    }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { Backup.write(act, uri, Backup.build(act)) }
            .onSuccess { model.message = "Резервная копия сохранена"; SLog.i("Data", "копия сохранена") }
            .onFailure { model.message = "Ошибка: ${it.message}"; SLog.e("Data", "копия", it) }
    }
    val csvSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching { Backup.write(act, uri, Backup.csv(model.history)) }
            .onSuccess { model.message = "Таблица сохранена" }.onFailure { model.message = "Ошибка: ${it.message}" }
    }
    fun accept(uri: Uri, from: String) {
        runCatching { Backup.read(act, uri) }
            .onSuccess { t -> if (Backup.describe(t) != null) model.importText = t else model.message = "Это не копия тренировок" }
            .onFailure { model.message = "Не удалось прочитать: ${it.message}"; SLog.e("Data", "чтение копии ($from)", it) }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) accept(uri, "файл") }
    return remember(model) {
        Actions(
            requestHealth = { healthPerm.launch(HealthSync.permissions) },
            askNotifications = { if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) },
            share = { s ->
                val f = model.storage.shareFile(s.fileName, s.markdown)
                val uri = FileProvider.getUriForFile(act, "${act.packageName}.files", f)
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/markdown"
                    putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, s.markdown)
                    putExtra(Intent.EXTRA_SUBJECT, s.fileName.removeSuffix(".md"))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                act.startActivity(Intent.createChooser(i, "Отправить заметку"))
            },
            saveAs = { s -> pending[0] = s; fileSaver.launch(s.fileName) },
            exportBackup = { backupSaver.launch(Backup.fileName("json")) },
            exportCsv = { csvSaver.launch(Backup.fileName("csv")) },
            importBackup = { importPicker.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
            restart = { restartApp(act) },
        )
    }
}

/** Раздел настроек тренировок из «Ещё → Тренировки» (5.23): прежние диалоги Sink. */
@Composable
fun TrainSettings(id: String, m: AppModel, a: Actions, onClose: () -> Unit) {
    when (id) {
        "train_set" -> TrainingSettingsDialog(m, onClose)
        "train_eq" -> EquipmentDialog(m, onClose)
        "train_gym" -> GymDialog(m, onClose)
        "train_use" -> TabitaDialog(m, onClose)
        "train_hc" -> HealthDialog(m, a, onClose)
        "train_data" -> DataDialog(m, a, onClose = onClose)
        else -> onClose()
    }
}

/** Короткие подписи строк настроек. */
object TrainSummary {
    fun set(m: AppModel) = "Отдых: " + (if (m.defaultRest > 0) "${m.defaultRest} с" else "по программе") +
        " · программа: " + (m.program?.let { "${it.days.size} дн." } ?: "нет") + if (m.autoIntensity) " · умный режим" else ""
    fun eq(m: AppModel) = if (m.equipment.isEmpty()) "Ничего — только своё тело" else m.equipment.joinToString(", ") { e ->
        val db = m.inventory.dumbbells()
        if (e == Equipment.DUMBBELLS && db.isNotEmpty()) "гантели " + db.joinToString("/") { LoadPlan.kg(it).removeSuffix(" кг") } + " кг"
        else e.title.lowercase()
    }
    fun gym(m: AppModel) = if (!m.gymEnabled) "Не хожу в зал" else "Хожу · " + m.gymEquipment.joinToString(", ") { it.title.lowercase() }
    fun use(m: AppModel) = if (!m.useTabita) "Выключено" else (m.readiness?.let { "готовность: ${it.intensity.title.lowercase()}" } ?: "сон, пульс, вес, питание")
    fun hc(m: AppModel) = if (!m.useHealth) "Не записывать тренировки" else (m.hcStatus?.title ?: "Проверяю…")
    fun data(m: AppModel) = "Тренировок: ${m.history.size} · копия, таблица CSV"
}
