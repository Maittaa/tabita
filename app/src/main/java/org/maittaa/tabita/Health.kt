package org.maittaa.tabita

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/** Чтение данных часов (Pixel Watch → Fitbit/Google Health → Health Connect). */
class Health(private val context: Context) {

    val permissions = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )
    val historyPermission = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    fun sdkStatus(): Int =
        runCatching { HealthConnectClient.getSdkStatus(context) }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)

    val available get() = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun granted(): Set<String> =
        if (available) runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        else emptySet()

    /**
     * Читает данные за период [from]..[to] (включительно).
     * Сон относится к дню пробуждения. Возвращает дата -> (id пункта -> значение)
     * только для тех пунктов шаблона, у которых указан источник.
     */
    suspend fun read(from: LocalDate, to: LocalDate, template: List<TemplateItem>): Map<String, Map<String, String>> {
        val granted = granted()
        val zone = ZoneId.systemDefault()
        val result = mutableMapOf<String, MutableMap<String, String>>()
        fun put(d: LocalDate, id: String, v: String, src: Collection<String> = emptyList()) {
            val m = result.getOrPut(d.key()) { mutableMapOf() }
            m[id] = v
            // приложение-источник (например, Fitbit/Google Health, openScale) — для подписи «из …»
            if (src.isNotEmpty()) m[srcKey(id)] = src.distinct().joinToString(",")
        }
        val bySource = template.filter { it.source != Source.NONE }.groupBy { it.source }
        val rangeStart = from.atStartOfDay()
        val rangeEnd = to.plusDays(1).atStartOfDay()

        // Шаги
        bySource[Source.HC_STEPS]?.let { items ->
            if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
                val groups = client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(rangeStart, rangeEnd),
                        timeRangeSlicer = Period.ofDays(1),
                    )
                )
                groups.forEach { g ->
                    val v = g.result[StepsRecord.COUNT_TOTAL] ?: return@forEach
                    if (v > 0) items.forEach { put(g.startTime.toLocalDate(), it.id, v.toString(), g.result.dataOrigins.map { o -> o.packageName }) }
                }
            }
        }

        // Тренировки: каждая сессия с видом и временем (ключ <id>!ex); минуты считает Repo с учётом «засчитывать ходьбу»
        bySource[Source.HC_EXERCISE]?.let { items ->
            if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
                val byDay = mutableMapOf<LocalDate, MutableList<WorkoutSession>>()
                var token: String? = null
                do {
                    val resp = client.readRecords(
                        ReadRecordsRequest(
                            recordType = ExerciseSessionRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(rangeStart.atZone(zone).toInstant(), rangeEnd.atZone(zone).toInstant()),
                            pageToken = token,
                        )
                    )
                    resp.records.forEach { r ->
                        val min = ((r.endTime.toEpochMilli() - r.startTime.toEpochMilli()) / 60000).toInt()
                        if (min <= 0) return@forEach
                        val d = r.startTime.atZone(zone).toLocalDate()
                        byDay.getOrPut(d) { mutableListOf() } += WorkoutSession(
                            Workouts.fromHc(r.exerciseType).key, min, r.startTime.toEpochMilli(),
                            r.title.orEmpty(), r.metadata.dataOrigin.packageName,
                        )
                    }
                    token = resp.pageToken
                } while (token != null)
                byDay.forEach { (d, list) ->
                    val clean = Workouts.dedupe(list)
                    items.forEach {
                        put(d, it.id, Workouts.counted(clean, false).toString(), clean.map { s -> s.src }.distinct())
                        result.getOrPut(d.key()) { mutableMapOf() }[Workouts.syncedKey(it.id)] = Workouts.toJson(clean)!!
                    }
                }
            }
        }

        // Пульс покоя
        bySource[Source.HC_RESTING_HR]?.let { items ->
            if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in granted) {
                val groups = client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(RestingHeartRateRecord.BPM_AVG),
                        timeRangeFilter = TimeRangeFilter.between(rangeStart, rangeEnd),
                        timeRangeSlicer = Period.ofDays(1),
                    )
                )
                groups.forEach { g ->
                    val v = g.result[RestingHeartRateRecord.BPM_AVG] ?: return@forEach
                    items.forEach { put(g.startTime.toLocalDate(), it.id, v.toString(), g.result.dataOrigins.map { o -> o.packageName }) }
                }
            }
        }

        // Вес (весы Xiaomi через Mi Fitness / Zepp Life и др.): последнее взвешивание за день
        bySource[Source.HC_WEIGHT]?.let { items ->
            if (HealthPermission.getReadPermission(WeightRecord::class) in granted) {
                val last = mutableMapOf<LocalDate, Pair<java.time.Instant, Double>>()
                val lastSrc = mutableMapOf<LocalDate, String>()
                var token: String? = null
                do {
                    val resp = client.readRecords(
                        ReadRecordsRequest(
                            recordType = WeightRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(rangeStart, rangeEnd),
                            pageToken = token,
                        )
                    )
                    resp.records.forEach { r ->
                        val d = r.time.atZone(zone).toLocalDate()
                        val prev = last[d]
                        if (prev == null || r.time > prev.first) {
                            last[d] = r.time to r.weight.inKilograms; lastSrc[d] = r.metadata.dataOrigin.packageName
                        }
                    }
                    token = resp.pageToken
                } while (token != null)
                last.forEach { (d, p) ->
                    val kg = Math.round(p.second * 10) / 10.0
                    items.forEach { put(d, it.id, kg.toString().removeSuffix(".0"), listOfNotNull(lastSrc[d])) }
                }
            }
        }

        // Сон: сессии, закончившиеся в этот день, без бодрствования; плюс фазы для блока «Сон»
        bySource[Source.HC_SLEEP]?.let { items ->
            if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
                val start = from.minusDays(1).atStartOfDay(zone).toInstant()
                val end = rangeEnd.atZone(zone).toInstant()
                // ключ: день + приложение-источник (чтобы не сложить один сон дважды)
                val sessions = mutableMapOf<Pair<LocalDate, String>, MutableList<Sleep.RawSession>>()
                var token: String? = null
                do {
                    val resp = client.readRecords(
                        ReadRecordsRequest(
                            recordType = SleepSessionRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(start, end),
                            pageToken = token,
                        )
                    )
                    resp.records.forEach { s ->
                        val day = s.endTime.atZone(zone).toLocalDate()
                        if (day < from || day > to) return@forEach
                        sessions.getOrPut(day to s.metadata.dataOrigin.packageName) { mutableListOf() } += Sleep.RawSession(
                            s.startTime.toEpochMilli(), s.endTime.toEpochMilli(),
                            s.stages.map { Sleep.RawStage(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.stage) },
                        )
                    }
                    token = resp.pageToken
                } while (token != null)
                sessions.entries.groupBy { it.key.first }.forEach { (d, list) ->
                    val best = list.map { it.key.second to Sleep.combine(it.value) }.maxBy { it.second.first }
                    val (minutes, detail) = best.second
                    if (minutes > 0) items.forEach {
                        put(d, it.id, minutes.toString(), listOf(best.first))
                        result.getOrPut(d.key()) { mutableMapOf() }[SleepDetail.key(it.id)] = detail.toJson()
                    }
                }
            }
        }
        return result
    }
}
