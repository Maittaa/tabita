package org.maittaa.tabita.train

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

sealed class Screen {
    data object Home : Screen()
    data object Workout : Screen()
    data object Guide : Screen()
    data object Program : Screen()
    data object Generator : Screen()
    data object TabitaData : Screen()
    data class Detail(val id: String) : Screen()
}

/** Шаги пошаговой тренировки. */
sealed class Step {
    data object Prep : Step()
    data class Warm(val w: WarmupDef, val index: Int, val total: Int) : Step()
    data class Set(val ex: PlannedEx, val exIndex: Int, val setIndex: Int) : Step()
    data object Results : Step()
}

/** Рекомендация на подход: числа для левой/правой (или одно) и пояснение. */
data class Rec(
    val left: Int?, val right: Int?, val reason: String, val last: String?, val startLoad: String? = null,
    /** 5.42: рекомендуемый вес из доступного инвентаря и пояснение. */
    val load: String? = null, val loadNote: String? = null,
)

class AppModel(app: Application) : AndroidViewModel(app) {
    val storage = Storage(app)
    /** 1.7: «умный режим» интенсивности — по данным дневника. */
    var autoIntensity by mutableStateOf(storage.autoIntensity); private set
    fun changeAutoIntensity(v: Boolean) { storage.autoIntensity = v; autoIntensity = v; SLog.i("Workout", "интенсивность автоматически: $v") }
    /** Текущая тренировка в умном режиме (интенсивность взята из готовности). */
    var smartMode by mutableStateOf(storage.smartDraft); private set
    private fun setSmart(v: Boolean) { smartMode = v; storage.smartDraft = v }
    /** Умный режим возможен: Tabita подключена и даёт оценку готовности. */
    val smartAvailable: Boolean get() = readiness != null

    /** Прочитанная резервная копия, ждёт подтверждения восстановления (1.6). */
    var importText by mutableStateOf<String?>(null)

    var program by mutableStateOf(storage.loadProgram()); private set
    /** Переносы тренировок (1.8). */
    var overrides by mutableStateOf(Schedule.prune(storage.schedOverrides, LocalDate.now())); private set

    var history by mutableStateOf(storage.loadHistory()); private set
    /** Только для тестов: подставить историю тренировок. */
    @androidx.annotation.VisibleForTesting fun replaceHistory(l: List<Session>) { history = l; storage.saveHistory(l) }
    var draft by mutableStateOf(storage.loadDraft()); private set
    var screen by mutableStateOf<Screen>(if (draft != null) Screen.Workout else Screen.Home)
    var tab by mutableStateOf(0)
    /** 5.52: раздел «Здоровья»: SEC_HUB — главная с плитками, иначе силовая / аэробика / йога / самочувствие. */
    var section by mutableStateOf(SEC_HUB)
    /** У раздела свой заголовок с «назад» (выбор блоков, чекап, подробности самочувствия) — верхнюю панель раздела прячем. */
    var ownBar by mutableStateOf(false)
    /** 5.23: попросить Tabita открыть вкладку «Тренировки» (из настроек). */
    var wantTab by mutableStateOf(false)
    var defaultRest by mutableStateOf(storage.restOverride); private set
    var equipment by mutableStateOf(storage.equipment); private set
    var gymEquipment by mutableStateOf(storage.gymEquipment); private set
    var inventory by mutableStateOf(storage.inventory); private set
    fun changeInventory(v: Inventory) { storage.inventory = v; inventory = v; SLog.i("Workout", "инвентарь: ${v.toJson()}") }

    /** 5.42: рабочий вес упражнения из доступного инвентаря. */
    fun loadPick(ex: PlannedEx, intensity: Intensity, place: Place): LoadPlan.Pick {
        val last = lastResult(ex.exId, ex.name)
        val opts = LoadPlan.options(ex.load, place, inventory)
        val reps = last?.values?.firstOrNull()?.let { leadingInt(it) }
        // 5.55: первый подход на верхней границе и «запас 3+» — было легко, можно сразу тяжелее
        val easyTop = !ex.seconds && reps != null && reps >= ex.repMax && last?.rirs?.firstOrNull() == 3
        val start = ex.def?.startPct?.let { p -> body.weightKg?.let { it * p } }
        val hold = intensity != Intensity.NORMAL || readiness?.holdProgression == true
        return LoadPlan.pick(ex.load, opts, last?.load, reps, last?.allTop == true || easyTop, ex.repMin, ex.seconds, intensity, hold, start, ex.repMax)
    }
    var gymEnabled by mutableStateOf(storage.gymEnabled); private set
    var heightCm by mutableStateOf(storage.heightCm); private set
    var manualWeight by mutableStateOf(storage.manualWeight); private set
    var birthYear by mutableStateOf(storage.birthYear); private set
    var sex by mutableStateOf(storage.sex); private set
    var useTabita by mutableStateOf(storage.useTabita); private set
    var genSettings by mutableStateOf(storage.genSettings); private set
    var message by mutableStateOf<String?>(null)
    private var tabitaBack: Screen = Screen.Home
    fun openTabitaData() { tabitaBack = screen; screen = Screen.TabitaData }
    fun closeTabitaData() { screen = tabitaBack }

    var useHealth by mutableStateOf(storage.useHealth); private set
    var hcStatus by mutableStateOf<HcStatus?>(null); private set

    fun refreshHealth() {
        viewModelScope.launch {
            hcStatus = runCatching { HealthSync.status(getApplication()) }.onFailure { SLog.e("Health", "статус", it) }.getOrDefault(HcStatus.UNAVAILABLE)
            SLog.d("Health", "статус $hcStatus")
            flushHealthQueue()
        }
    }

    /** При входе: дописать в Health Connect тренировки, которые не записались после завершения (нет доступа, ошибка). */
    private fun flushHealthQueue() {
        if (!useHealth || hcStatus != HcStatus.OK) return
        val q = storage.hcQueue
        if (q.isEmpty()) return
        val pending = history.filter { it.id in q && !it.hcSaved }
        storage.hcQueue = pending.map { it.id }.toSet()
        if (pending.isEmpty()) return
        viewModelScope.launch {
            var ok = 0
            pending.forEach { s ->
                runCatching { withContext(Dispatchers.IO) { HealthSync.write(getApplication(), s) } }.onSuccess {
                    ok++
                    history = history.map { if (it.id == s.id) it.copy(hcSaved = true) else it }
                    storage.hcQueue = storage.hcQueue - s.id
                }
            }
            SLog.i("Health", "очередь при входе: дописано $ok из ${pending.size}")
            if (ok > 0) {
                storage.saveHistory(history)
                message = if (ok == 1) "Тренировка дописана в Health Connect" else "Дописано в Health Connect: $ok тренировки"
            }
        }
    }

    fun changeUseHealth(v: Boolean) { storage.useHealth = v; useHealth = v; refreshHealth() }

    /** Записать тренировку в Health Connect; отметить в истории. */
    fun saveToHealth(s: Session, quiet: Boolean = false) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { HealthSync.write(getApplication(), s) } }
                .onSuccess {
                    history = history.map { if (it.id == s.id) it.copy(hcSaved = true) else it }
                    storage.saveHistory(history)
                    storage.hcQueue = storage.hcQueue - s.id
                    if (!quiet) message = "Записано в Health Connect"
                    SLog.i("Health", "тренировка ${s.id} записана")
                }
                .onFailure {
                    SLog.e("Health", "не удалось записать тренировку ${s.id}", it)
                    storage.hcQueue = storage.hcQueue + s.id
                    message = "Health Connect: ${it.message ?: "ошибка записи"} — запишу при следующем входе"
                }
        }
    }

    var tabitaStatus by mutableStateOf<TabitaStatus?>(null); private set
    /** Как пришло из Tabita; наружу — [tabitaData] с учётом выбора «какие данные брать». */
    var tabitaRaw by mutableStateOf<TabitaData?>(null); private set
    /** Только для тестов: подставить данные Tabita. */
    @androidx.annotation.VisibleForTesting fun injectTabita(d: TabitaData?) { tabitaRaw = d; tabitaStatus = TabitaStatus.OK }
    var tabitaUse by mutableStateOf(storage.tabitaUse); private set
    val tabitaData: TabitaData? get() = tabitaRaw?.let { TabitaUse.apply(it, tabitaUse) }
    fun changeTabitaUse(v: Set<String>) { storage.tabitaUse = v; tabitaUse = v; SLog.i("Tabita", "берём данные: ${v.sorted()}") }

    // ——— разгрузочная неделя (1.3) ———
    var deloadUntil by mutableStateOf(storage.deloadUntil); private set
    /** 5.55: прибавка подходов к плану по упражнениям. */
    var extraSets by mutableStateOf(SetProgression.parse(storage.extraSets)); private set
    val deloadActive: Boolean get() = deloadUntil.isNotEmpty() && deloadUntil >= LocalDate.now().toString()
    /** Предложить разгрузку: есть признаки, она не идёт и не отложена. */
    private var deloadSnooze by mutableStateOf(storage.deloadSnooze)
    val deloadOffer: Boolean get() = readiness?.deloadSuggested == true && !deloadActive && deloadSnooze < LocalDate.now().toString()
    fun startDeload() { SLog.i("Deload", "разгрузочная неделя включена"); val u = LocalDate.now().plusDays(6).toString(); storage.deloadUntil = u; deloadUntil = u; message = "Разгрузочная неделя до ${prettyDate(u)}: подходов на 40 % меньше" }
    fun cancelDeload() { SLog.i("Deload", "разгрузка отменена"); storage.deloadUntil = ""; deloadUntil = "" }
    fun snoozeDeload() { SLog.i("Deload", "разгрузка отложена на 3 дня"); val d = LocalDate.now().plusDays(3).toString(); storage.deloadSnooze = d; deloadSnooze = d; message = "Напомню через 3 дня, если пульс не придёт в норму" }

    init { refreshTabita(); refreshHealth() }

    /** Профиль тела: каждое поле — из Tabita, если она его отдаёт, иначе введённое вручную. */
    val body: Body
        get() {
            val td = if (useTabita) tabitaData else null
            val tw = td?.days?.lastOrNull { it.weight != null }?.weight
            val p = td?.profile
            val th = p?.heightCm?.takeIf { it in 100.0..250.0 }?.roundToInt()
            val ta = p?.age?.takeIf { it in 10..110 }
            val ts = p?.sex
            val manualAge = birthYear.takeIf { it > 0 }?.let { LocalDate.now().year - it }?.takeIf { it in 10..110 }
            return Body(
                heightCm = th ?: heightCm.takeIf { it > 0 }, weightKg = tw ?: manualWeight.toDouble().takeIf { it > 0 }, weightFromTabita = tw != null,
                age = ta ?: manualAge, sex = ts ?: sex.takeIf { it.isNotEmpty() },
                heightFromTabita = th != null, ageFromTabita = ta != null, sexFromTabita = ts != null,
            )
        }

    fun changeHeight(cm: Int) { storage.heightCm = cm; heightCm = cm }
    fun changeBirthYear(y: Int) { storage.birthYear = y; birthYear = y }
    fun changeSex(s: String) { storage.sex = s; sex = s }
    fun changeManualWeight(kg: Float) { storage.manualWeight = kg; manualWeight = kg }
    fun changeGymEnabled(v: Boolean) { storage.gymEnabled = v; gymEnabled = v }
    fun changeGymEquipment(e: Set<Equipment>) { storage.gymEquipment = e; gymEquipment = e }

    /** 5.59: отметки самочувствия за сегодня (ставит MainActivity). */
    var marksSource: (() -> List<org.maittaa.tabita.DayMarks.Mark>)? = null

    val readiness: Readiness?
        get() = if (!useTabita) null else tabitaData?.let { d ->
            val today = LocalDate.now()
            val week = history.count { s -> runCatching { LocalDate.parse(s.date) }.getOrNull()?.let { !it.isBefore(today.minusDays(6)) } == true }
            ReadinessCalc.evaluate(d, today, program?.goal ?: Goal.MASS, sessions7 = week,
                bodyWeight = manualWeight.toDouble().takeIf { it > 0 }, marks = runCatching { marksSource?.invoke() }.getOrNull().orEmpty())
        }

    fun refreshTabita() {
        if (!useTabita) return
        viewModelScope.launch {
            val (st, data) = withContext(Dispatchers.IO) { TabitaClient.load(getApplication()) }
            tabitaStatus = st
            if (data != null) tabitaRaw = data
            SLog.i("Tabita", "статус $st" + (data?.let { ", дней ${it.days.size}, профиль ${it.profile != null}, чекап ${it.checkup?.date}" } ?: ""))
        }
    }

    fun changeUseTabita(v: Boolean) {
        storage.useTabita = v; useTabita = v
        if (v) refreshTabita() else { tabitaRaw = null; tabitaStatus = null }
    }

    // ——— программа ———

    /** Через сколько дней после автосборки пересобрать: 7 и 14 — по первым тренировкам подбирается свой план. */
    fun rebuildDue(today: java.time.LocalDate = java.time.LocalDate.now()): Int? {
        val st = storage.autoStart; if (st <= 0 || program == null) return null
        val days = today.toEpochDay() - st
        val stage = storage.autoStage
        if (today.toEpochDay() <= storage.autoSnooze) return null
        return when { stage <= 1 && days >= 14 -> 14; stage == 0 && days >= 7 -> 7; else -> null }
    }

    fun snoozeRebuild() { storage.autoSnooze = java.time.LocalDate.now().toEpochDay() + 2 }

    fun generate(s: GenSettings, auto: Boolean = false) {
        if (auto) {
            val today = java.time.LocalDate.now().toEpochDay()
            val st = storage.autoStart
            when {
                st <= 0 || storage.autoStage >= 2 -> { storage.autoStart = today; storage.autoStage = 0 }
                today - st >= 14 -> storage.autoStage = 2
                today - st >= 7 -> storage.autoStage = 1
            }
            storage.autoSnooze = 0
            SLog.i("Program", "автосборка: старт ${storage.autoStart}, этап ${storage.autoStage}")
        }
        storage.genSettings = s; genSettings = s
        saveProgram(Generator.generate(s, equipment, gymEquipment, body))
        SLog.i("Program", "собрана: ${program?.name}; дни: ${program?.days?.joinToString { it.name + "(" + it.exercises.size + ")" }}")
        screen = Screen.Program
        message = "Программа собрана — можно поправить упражнения"
    }

    fun saveProgram(p: Program?) {
        // новая программа (другие дни) или смена дней недели — старые переносы больше не имеют смысла
        val old = program
        if (overrides.isNotEmpty() && (p == null || old == null || p.days.map { it.id to it.weekday } != old.days.map { it.id to it.weekday })) {
            storage.schedOverrides = emptyMap(); overrides = emptyMap()
        }
        storage.saveProgram(p); program = p
    }

    fun updateDay(day: ProgramDay) {
        val p = program ?: return
        saveProgram(p.copy(days = p.days.map { if (it.id == day.id) day else it }))
    }

    fun addDay() {
        val p = program ?: return
        val letters = listOf("А", "Б", "В", "Г", "Д", "Е", "Ж")
        val l = letters.firstOrNull { l -> p.days.none { it.name == "Тренировка $l" } } ?: (p.days.size + 1).toString()
        saveProgram(p.copy(days = p.days + ProgramDay(name = "Тренировка $l", weekday = null, exercises = emptyList())))
    }

    fun deleteDay(id: String) { program?.let { p -> saveProgram(p.copy(days = p.days.filterNot { it.id == id })) } }

    fun changeProgramRest(sec: Int) { program?.let { saveProgram(it.copy(restSec = sec)) } }

    fun changeEquipment(e: Set<Equipment>) { storage.equipment = e; equipment = e }

    // ——— выбор тренировки на сегодня ———
    data class Suggestion(val day: ProgramDay, val label: String)

    fun suggestion(today: LocalDate = LocalDate.now()): Suggestion? {
        val p = program ?: return null
        val days = p.days.filter { it.exercises.isNotEmpty() }
        if (days.isEmpty()) return null
        val doneToday = history.any { it.date == today.toString() }
        val moved = overrides[today.toString()]?.isNotEmpty() == true
        if (!doneToday) Schedule.dayOn(p, overrides, today)?.let { return Suggestion(it, if (moved) "Сегодня · перенесена" else "Сегодня ${today.dayOfWeek.ru()}") }
        for (k in 1..7) {
            val d = today.plusDays(k.toLong())
            Schedule.dayOn(p, overrides, d)?.let {
                return Suggestion(it, if (doneToday) "Сегодня уже сделано · следующая — ${d.dayOfWeek.ru()}" else "Сегодня отдых · ближайшая — ${d.dayOfWeek.ru()}")
            }
        }
        val last = history.firstOrNull()?.dayId
        val idx = days.indexOfFirst { it.id == last }
        return Suggestion(days[(idx + 1).mod(days.size)], "Следующая по порядку")
    }

    /** Сегодняшняя тренировка по расписанию (с переносами), если ещё не сделана. */
    fun todayPlanned(today: LocalDate = LocalDate.now()): ProgramDay? {
        val p = program ?: return null
        if (history.any { it.date == today.toString() }) return null
        return Schedule.dayOn(p, overrides, today)
    }

    /** Перенести сегодняшнюю тренировку и сдвинуть следующие, сколько нужно для отдыха между ними. */
    fun postponeToday(today: LocalDate = LocalDate.now()) {
        val p = program ?: return
        val (o, moves) = Schedule.postpone(p, overrides, today)
        if (moves.isEmpty()) return
        val clean = Schedule.simplify(p, o)
        storage.schedOverrides = clean; overrides = clean
        val txt = moves.joinToString(", ") { (d, from, to) -> "${d.name}: ${from.dayOfWeek.ruShort()} → ${to.dayOfWeek.ruShort()}" }
        SLog.i("Schedule", "перенос: $txt")
        message = "Перенесено — $txt"
    }

    /** Вернуть обычное расписание. */
    fun resetSchedule() {
        storage.schedOverrides = emptyMap(); overrides = emptyMap()
        SLog.i("Schedule", "расписание сброшено"); message = "Расписание — как в программе"
    }

    /** Переносы на ближайшие дни для показа: (день, дата). */
    fun upcomingMoves(today: LocalDate = LocalDate.now()): List<Pair<ProgramDay, LocalDate>> {
        val p = program ?: return emptyList()
        return overrides.filterValues { it.isNotEmpty() }.mapNotNull { (d, id) ->
            val date = LocalDate.parse(d); if (date < today) null else p.days.firstOrNull { it.id == id }?.let { it to date }
        }.sortedBy { it.second }
    }

    // ——— тренировка ———

    private fun buildPlan(day: ProgramDay, intensity: Intensity, noEq: Boolean, place: Place, warm: Set<String>): WorkoutPlan? {
        val p = program ?: return null
        return Planner.build(
            p, day, equipment, gymEquipment, place, noEq, intensity,
            readiness?.legsLight == true, defaultRest, body, warm,
            deloadUntil = deloadUntil.takeIf { deloadActive },
            extra = extraSets,
        )
    }

    /** Где сегодня: зал, если он включён и программа для зала, иначе дом. */
    fun defaultPlace(): Place = if (gymEnabled && program?.place == Place.GYM) Place.GYM else Place.HOME

    fun start(day: ProgramDay) {
        RestTimer.stop(getApplication())
        val smart = autoIntensity && smartAvailable
        setSmart(smart)
        val plan = buildPlan(day, if (smart) readiness!!.intensity else Intensity.NORMAL, false, defaultPlace(), storage.warmups) ?: return
        update(Draft(plan, 0L, loads = defaultLoads(plan)))
        screen = Screen.Workout
        SLog.i("Workout", "старт «${day.name}»: ${plan.intensity.title}, ${plan.place.title}, упражнений ${plan.exercises.size}" +
            (readiness?.let { r -> "; готовность: " + r.reasons.joinToString(" | ") { it.text } } ?: "") + (if (deloadActive) "; разгрузка" else ""))
    }

    /** На шаге «Подготовка», пока ни одного подхода: интенсивность, место, без инвентаря. */
    // ——— пауза общего таймера (1.7) ———
    /** Пауза: [manual] — кнопкой; иначе — ушли из приложения (снимется при возвращении). */
    fun pauseClock(manual: Boolean) {
        val d = draft ?: return
        if (!d.running || d.paused) { if (manual && d.paused && !d.pausedManual) update(d.copy(pausedManual = true)); return }
        update(d.copy(pausedSince = System.currentTimeMillis(), pausedManual = manual))
        SLog.d("Workout", "таймер на паузе" + if (manual) " (кнопкой)" else " (ушли из приложения)")
    }

    /** Продолжить: при возвращении в приложение — только если пауза была автоматической. */
    fun resumeClock(manual: Boolean) {
        val d = draft ?: return
        if (!d.paused || (!manual && d.pausedManual)) return
        update(d.copy(pausedMs = d.pausedMs + (System.currentTimeMillis() - d.pausedSince), pausedSince = 0, pausedManual = false))
        SLog.d("Workout", "таймер продолжен")
    }

    /** Включить умный режим на подготовке: интенсивность — по готовности из Tabita. */
    fun replanSmart(noEq: Boolean, place: Place) {
        val r = readiness ?: return
        replan(r.intensity, noEq, place, smart = true)
    }

    fun replan(intensity: Intensity, noEq: Boolean, place: Place, smart: Boolean? = null) {
        val d = draft ?: return
        if (d.started) return
        smart?.let { setSmart(it) }
        val day = program?.days?.firstOrNull { it.id == d.plan.dayId } ?: return
        val plan = buildPlan(day, intensity, noEq, place, storage.warmups) ?: return
        SLog.d("Workout", "план изменён: ${intensity.title}, ${place.title}, без инвентаря $noEq")
        update(d.copy(plan = plan, restSec = plan.restSec, loads = defaultLoads(plan)))
    }

    /** Включить или выключить упражнение разминки (запоминается для следующих тренировок). */
    fun toggleWarmupOn(id: String) {
        val d = draft ?: return
        val on = storage.warmups.let { if (id in it) it - id else it + id }
        storage.warmups = on
        val list = Library.warmups.map { it.id }.filter { it == id && id !in d.plan.warmups || (it != id && it in d.plan.warmups) }
        update(d.copy(plan = d.plan.copy(warmups = list)))
    }

    /** Нажата «Вперёд» на подготовке — пошло время тренировки. */
    fun begin() {
        val d = draft ?: return
        update(d.copy(startedAt = if (d.running) d.startedAt else System.currentTimeMillis(), step = 1))
    }

    fun completeWarm(id: String) {
        val d = draft ?: return
        update(d.copy(warmDone = d.warmDone + id, step = d.step + 1))
    }

    /** Замена упражнения по ходу тренировки — пока по нему нет сделанных подходов. */
    fun canReplace(exIndex: Int): Boolean = draft?.sets?.none { (k, v) -> k.startsWith("$exIndex:") && v.done } ?: false

    fun replaceExercise(exIndex: Int, def: ExerciseDef) {
        val d = draft ?: return
        if (!canReplace(exIndex)) return
        val old = d.plan.exercises.getOrNull(exIndex) ?: return
        val r = rangeFor(def, program?.goal ?: Goal.MASS)
        val ne = PlannedEx.of(def, old.sets, r, "вместо «${old.name}»")
        val plan = d.plan.copy(exercises = d.plan.exercises.mapIndexed { i, x -> if (i == exIndex) ne else x })
        val loads = d.loads - exIndex + (defaultLoads(plan)[exIndex]?.let { mapOf(exIndex to it) } ?: emptyMap())
        update(d.copy(plan = plan, loads = loads, sets = d.sets.filterKeys { !it.startsWith("$exIndex:") }))
        message = "«${old.name}» → «${def.name}»"
    }

    /** Чем можно заменить упражнение на текущем месте тренировки. */
    fun alternatives(exIndex: Int): List<ExerciseDef> {
        val d = draft ?: return emptyList()
        val cur = d.plan.exercises.getOrNull(exIndex)?.def ?: return emptyList()
        val eq = if (d.plan.place == Place.GYM) gymEquipment else equipment
        val inPlan = d.plan.exercises.map { it.exId }.toSet()
        return Library.all.filter { it.pattern == cur.pattern && it.id !in inPlan && it.available(eq, d.plan.noEquipment) }
            .sortedBy { kotlin.math.abs(it.level - cur.level) }
    }

    private fun defaultLoads(plan: WorkoutPlan): Map<Int, String> = plan.exercises.mapIndexedNotNull { i, ex ->
        if (ex.load == LoadKind.NONE) null
        else i to (loadPick(ex, plan.intensity, plan.place).text ?: lastResult(ex.exId, ex.name)?.load ?: when (ex.load) {
            LoadKind.BACKPACK -> "2 книги"
            LoadKind.DUMBBELLS -> if (plan.place == Place.GYM) "" else "2 × 2,5 кг"
            else -> startLoad(ex.def, body)?.removePrefix("≈ ") ?: ""
        })
    }.toMap()

    fun steps(plan: WorkoutPlan): List<Step> = buildList {
        add(Step.Prep)
        val warm = plan.warmups.mapNotNull { Library.warmup(it) }
        warm.forEachIndexed { i, w -> add(Step.Warm(w, i, warm.size)) }
        plan.exercises.forEachIndexed { ei, ex -> repeat(ex.sets) { si -> add(Step.Set(ex, ei, si)) } }
        add(Step.Results)
    }

    private fun update(d: Draft?) { draft = d; storage.saveDraft(d) }

    fun goTo(step: Int) { draft?.let { update(it.copy(step = step.coerceIn(0, steps(it.plan).lastIndex))) } }

    fun completeSet(step: Step.Set, r: SetResult) {
        val d = draft ?: return
        val all = steps(d.plan)
        val next = d.step + 1
        update(d.copy(sets = d.sets + (setKey(step.exIndex, step.setIndex) to r.copy(done = true)), step = next))
        val ns = all.getOrNull(next)
        if (ns is Step.Set) RestTimer.start(getApplication(), d.restSec, "Дальше: ${ns.ex.name}, подход ${ns.setIndex + 1}")
        else RestTimer.stop(getApplication())
    }

    fun toggleAfter(i: Int) { draft?.let { update(it.copy(after = if (i in it.after) it.after - i else it.after + i)) } }
    fun setLoad(i: Int, v: String) { draft?.let { update(it.copy(loads = it.loads + (i to v))) } }

    fun changeDefaultRest(sec: Int) {
        storage.restOverride = sec; defaultRest = sec
        draft?.let { d -> update(d.copy(restSec = if (sec > 0) sec else program?.restSec ?: d.restSec)) }
    }

    fun discard() {
        RestTimer.stop(getApplication())
        update(null); screen = Screen.Home
    }

    fun lastResult(exId: String, name: String): ExResult? =
        history.asSequence().flatMap { it.exercises }
            .firstOrNull { (it.exId == exId || (it.exId.isEmpty() && it.name == name)) && it.values.isNotEmpty() }

    /**
     * Рекомендация по прошлым тренировкам: не дошёл до верхней границы — +1 повтор (+5 с),
     * дошёл — столько же и усложнить. Облегчённая тренировка и падающий вес — без прибавки,
     * восстановительная — нижняя граница.
     */
    fun recommend(ex: PlannedEx, setIndex: Int, intensity: Intensity): Rec {
        val min = ex.repMin
        val max = ex.repMax
        val inc = if (ex.seconds) 5 else 1
        val unit = if (ex.seconds) "с" else "повт."
        val lastEx = lastResult(ex.exId, ex.name)
        val last = lastEx?.values?.getOrNull(setIndex)
        // 5.55: запас в этом подходе в прошлый раз: 0 — держим, 1–2 — +1, 3+ — +2
        val rir = lastEx?.rirs?.getOrNull(setIndex) ?: -1
        val parts = last?.split("/")?.map { leadingInt(it) }
        val prevL = parts?.getOrNull(0)
        val prevR = if (ex.perSide) parts?.getOrNull(1) ?: prevL else null
        val hold = intensity != Intensity.NORMAL || readiness?.holdProgression == true
        fun next(p: Int?): Int = when {
            p == null -> min
            intensity == Intensity.RECOVERY -> minOf(p, min)
            p >= max -> max
            hold -> p.coerceAtLeast(min)
            rir == 0 -> p.coerceAtLeast(min)
            rir == 3 -> (p + 2 * inc).coerceAtMost(max)
            else -> (p + inc).coerceAtMost(max)
        }
        val place = draft?.plan?.place ?: Place.HOME
        val pick = loadPick(ex, intensity, place)
        // новый вес — повторы снова с нижней границы
        val l = if (pick.resetReps) min else next(prevL)
        val r = if (ex.perSide) (if (pick.resetReps) min else next(prevR)) else null
        val atTop = prevL != null && prevL >= max && (!ex.perSide || (prevR ?: 0) >= max)
        val reason = when {
            last == null && min != max -> "Первый раз — начни с нижней границы ($min–$max $unit)"
            last == null -> "По плану"
            intensity == Intensity.RECOVERY -> "Восстановительная тренировка — легко, без отказа"
            atTop && !hold -> "В прошлый раз $last — верхняя граница. Если было легко, пора усложнить"
            atTop -> "В прошлый раз $last — держим, сегодня без усложнения"
            intensity == Intensity.LIGHT -> "В прошлый раз $last — сегодня облегчённо, без прибавки"
            hold -> "В прошлый раз $last — прибавку придерживаем (вес снижается)"
            rir == 0 -> "В прошлый раз $last, запаса не было — повтори тот же результат"
            rir == 3 -> "В прошлый раз $last с запасом 3+ → прибавь ${if (inc == 1) "2 повтора" else "${2 * inc} с"}"
            else -> "В прошлый раз $last → прибавь ${if (inc == 1) "повтор" else "$inc с"}"
        }
        return Rec(l, r, if (pick.resetReps) "Новый вес — начни с нижней границы ($min $unit)" else reason, last,
            if (last == null && pick.kg == null) startLoad(ex.def, body) else null, pick.text, pick.note)
    }

    fun suggestedWeek(today: LocalDate = LocalDate.now()): String {
        val first = history.lastOrNull()?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return "1"
        return (ChronoUnit.DAYS.between(first, today) / 7 + 1).toString()
    }

    fun finish(week: String, wellbeing: String, notes: String) {
        val d = draft ?: return
        RestTimer.stop(getApplication())
        val date = today()
        val weekday = program?.days?.firstOrNull { it.id == d.plan.dayId }?.weekday?.ru()
        val md = MarkdownExport.build(d, date, weekday, week, wellbeing, notes, readiness)
        val fileName = "$date ${d.plan.title}.md".replace('/', '-')
        val savedTo: String? = null
        val error: String? = null
        val begun = if (d.running) d.startedAt else System.currentTimeMillis()
        val activeSec = if (d.running) d.elapsedMs() / 1000 else 0
        val s = Session(
            id = begun.toString(), dayId = d.plan.dayId, title = d.plan.title, date = date,
            startedAt = begun, durationSec = activeSec,
            week = week, wellbeing = wellbeing, notes = notes,
            exercises = Results.summarize(d), markdown = md, fileName = fileName, savedTo = savedTo,
            intensity = d.plan.intensity.title, place = d.plan.place.title,
        )
        history = listOf(s) + history
        storage.saveHistory(history)
        // 5.55: прибавка или откат подходов, когда вес прибавить нечем
        val changes = runCatching {
            SetProgression.evaluate(s, history, extraSets,
                planned = { id -> program?.days?.firstOrNull { it.id == d.plan.dayId }?.exercises?.firstOrNull { it.exId == id }?.sets },
                heavierAvailable = { r ->
                    val def = Library.get(r.exId)
                    val cur = LoadPlan.parseKg(r.load)
                    def != null && def.load != LoadKind.NONE && cur != null &&
                        LoadPlan.options(def.load, d.plan.place, inventory).any { it > cur + 1e-6 }
                })
        }.getOrDefault(emptyList())
        if (changes.isNotEmpty()) {
            extraSets = SetProgression.apply(extraSets, changes)
            storage.extraSets = SetProgression.toJson(extraSets)
            changes.forEach { SLog.i("Workout", "подходы «${it.name}»: ${if (it.delta > 0) "+" else ""}${it.delta} — ${it.why}") }
        }
        update(null)
        SLog.i("Workout", "сохранена «${s.title}»: ${s.durationSec / 60} мин, упражнений ${s.exercises.size}, подходов ${s.exercises.sumOf { it.values.size }}" +
            (error?.let { "; $it" } ?: ""))
        // в Health Connect (а через него — в Google Health и Tabita); не вышло сейчас — допишется при следующем входе
        if (useHealth) storage.hcQueue = storage.hcQueue + s.id
        if (useHealth && hcStatus == HcStatus.OK) saveToHealth(s, quiet = true)
        val toHc = useHealth && hcStatus == HcStatus.OK
        val setsNote = changes.joinToString("") { c -> " · «${c.name}»: " + if (c.delta > 0) "в следующий раз на подход больше" else "на подход меньше" }
        message = error ?: ("Тренировка сохранена" + listOfNotNull(
            if (toHc) "Health Connect" else null,
        ).takeIf { it.isNotEmpty() }?.joinToString(" и ", prefix = " и записана в ").orEmpty() +
            if (useHealth && !toHc) " · в Health Connect запишу при следующем входе" else "") + setsNote
        tab = 0
        screen = Screen.Home
    }

    fun deleteSession(id: String) {
        history = history.filterNot { it.id == id }; storage.saveHistory(history); screen = Screen.Home
    }

    fun today(): String = LocalDate.now(ZoneId.systemDefault()).toString()
}
