package org.maittaa.tabita.train

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneId

enum class HcStatus(val title: String) {
    UNAVAILABLE("Health Connect недоступен на этом телефоне"),
    NEEDS_UPDATE("Нужно обновить Health Connect"),
    NO_PERMISSION("Нет разрешения на запись тренировок"),
    OK("Тренировки записываются в Health Connect"),
}

/**
 * Запись тренировок в Health Connect: силовая тренировка (тип 70) с началом, концом, названием и итогами.
 * Оттуда её видят Tabita (блок «Тренировки») и Google Health / Fitbit. Повторная запись той же тренировки
 * не создаёт дубль — clientRecordId = "sink-<id сессии>".
 */
/** Что делать, если Health Connect недоступен (1.2.1). На Android 14+ он встроен, на 8–13 — отдельное приложение. */
fun hcHelp(st: HcStatus?): String? = when (st) {
    HcStatus.UNAVAILABLE, HcStatus.NEEDS_UPDATE -> if (Build.VERSION.SDK_INT >= 34)
        "На Android 14 и новее Health Connect встроен в систему: проверь «Настройки → Безопасность и конфиденциальность → Health Connect» и обнови систему."
    else "На Android 8–13 Health Connect — отдельное приложение: установи или обнови его в Google Play. " +
        "Если Health Connect нет, тренировки всё равно сохраняются в истории, но в Google Health и в дневник не попадут."
    HcStatus.NO_PERMISSION -> "Нажми «Разрешить запись» и включи запись тренировок для Tabita."
    else -> null
}

object HealthSync {
    const val PLAY = "market://details?id=com.google.android.apps.healthdata"
    val permissions = setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class))

    fun available(ctx: Context): Int = HealthConnectClient.getSdkStatus(ctx)

    suspend fun status(ctx: Context): HcStatus = when (available(ctx)) {
        HealthConnectClient.SDK_AVAILABLE -> {
            val granted = HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()
            if (granted.containsAll(permissions)) HcStatus.OK else HcStatus.NO_PERMISSION
        }
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcStatus.NEEDS_UPDATE
        else -> HcStatus.UNAVAILABLE
    }

    suspend fun write(ctx: Context, s: Session) {
        // 5.32: тестовая копия не пишет в Health Connect — иначе пробные тренировки попадут в основной дневник
        if (org.maittaa.tabita.BuildConfig.BUILD_TYPE == "fresh") error("в тестовой копии запись в Health Connect выключена")
        val client = HealthConnectClient.getOrCreate(ctx)
        val start = Instant.ofEpochMilli(s.startedAt)
        val end = Instant.ofEpochMilli(s.startedAt + maxOf(s.durationSec, 60) * 1000)
        val zone = ZoneId.systemDefault().rules
        val notes = s.exercises.filter { it.values.isNotEmpty() }.joinToString("\n") { r ->
            "${r.name}: ${r.values.joinToString(" · ")}${if (r.seconds) " с" else ""}${r.load?.let { " ($it)" } ?: ""}"
        }.take(1000)
        val record = ExerciseSessionRecord(
            startTime = start, startZoneOffset = zone.getOffset(start),
            endTime = end, endZoneOffset = zone.getOffset(end),
            metadata = Metadata.activelyRecorded(
                Device(Device.TYPE_PHONE, Build.MANUFACTURER, Build.MODEL), "sink-${s.id}", 1L,
            ),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = s.title + if (s.place.isNotBlank()) " · ${s.place.lowercase()}" else "",
            notes = notes.ifBlank { null },
        )
        client.insertRecords(listOf(record))
    }
}
