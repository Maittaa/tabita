package org.maittaa.tabita

import androidx.compose.material.icons.filled.Favorite
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.consumeWindowInsets
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Flag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : FragmentActivity() {
    lateinit var repo: Repo
    lateinit var health: Health
    lateinit var prefs: Prefs
    lateinit var openScale: OpenScale
    /** Сообщение о последней синхронизации с часами. */
    val syncStatus = MutableStateFlow("")
    /** Health Connect подключён (есть хоть одно разрешение): null — ещё не проверяли (5.39). */
    val hcConnected = MutableStateFlow<Boolean?>(null)
    /** Открыть день на вкладке «День» (из «Итогов месяца»). */
    val jumpDate = MutableStateFlow<String?>(null)
    /** Открыть вкладку «Самочувствие» и начать чекап (из плитки дня). */
    val wbStart = MutableStateFlow(false)
    /** Показать знакомство (первый запуск или «Ещё → О приложении»). */
    val showIntro = MutableStateFlow(false)
    /** Открыть «Программу» на разделе (PSUB_*); −1 — ничего. */
    val openProgram = MutableStateFlow(-1)
    /** Открыть вкладку «Тренировки» (из плитки дня). */
    val openTrain = MutableStateFlow(false)
    /** Открыть раздел настроек (из карточки приёма пищи → «Весь справочник»). */
    val openSettings = MutableStateFlow<String?>(null)

    /** Тренировки (вкладка «Тренировки»). */
    val train: org.maittaa.tabita.train.AppModel by viewModels()

    /** Приложение заблокировано (показан экран входа). */
    var locked by mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = Prefs(applicationContext)
        Yoga.customJson = prefs.yogaCustom
        I18n.init(prefs)
        Look.init(prefs)
        repo = Repo(applicationContext)
        health = Health(applicationContext)
        openScale = OpenScale(applicationContext)
        Log.d("UI", "MainActivity.onCreate, блокировка=${prefs.lockEnabled}")
        locked = prefs.lockEnabled
        applySecureFlag()
        AppIcons.normalize(this)
        Reminders.createChannels(this)
        Reminders.scheduleAll(this)
        org.maittaa.tabita.train.TimerService.createChannels(this)
        // 5.44: плановая тренировка — для плана дня на «Ритме дня»
        Habits.appTrainings = { train.history.map { it.startedAt } }
        DayPlan.trainSource = { d ->
            train.todayPlanned(d)?.let { DayPlan.TrainPlan(it.name, train.genSettings.minutes, train.program?.place == org.maittaa.tabita.train.Place.GYM) }
        }
        // 5.59.1: отдельная плитка самочувствия → раздел в «Отметках дня»
        runCatching { DayMarks.merge(repo, prefs) }
        // 5.59: отметки самочувствия — в готовность к тренировке
        train.marksSource = { DayMarks.marked(repo, java.time.LocalDate.now()) }
        // 5.56: дни силовой для недельного шаблона — из программы (или настроек генерации)
        WeekPlan.strengthDays = {
            train.program?.days?.filter { it.exercises.isNotEmpty() }?.mapNotNull { it.weekday }?.takeIf { it.isNotEmpty() }
                ?: if (train.program != null) train.genSettings.days else emptyList()
        }
        if (intent?.getBooleanExtra("openTrain", false) == true) openTrain.value = true
        val showRationale = intent?.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
            intent?.action == "android.intent.action.VIEW_PERMISSION_USAGE"
        // 5.35: знакомство — только на чистой установке; у кого уже есть записи или профиль — сразу в приложение
        if (!prefs.introDone && (repo.entries.value.isNotEmpty() || !repo.profile.value.empty)) prefs.introDone = true
        // в тестах (Robolectric) знакомство — только если его включили явно
        if (android.os.Build.FINGERPRINT == "robolectric" && !prefs.sp.contains("introDone")) prefs.introDone = true
        showIntro.value = !prefs.introDone
        setContent {
            AppTheme {
                val intro = collect(showIntro)
                if (locked) LockScreen(this) else if (intro) IntroScreen(this) { showIntro.value = false } else App(this, showRationale)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // уведомление таймера отдыха — сразу на вкладку тренировки
        if (intent.getBooleanExtra("openTrain", false)) openTrain.value = true
    }

    fun applySecureFlag() {
        if (prefs.hideInRecents) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStart() {
        super.onStart()
        // Блокируем снова, если приложение было свёрнуто дольше 30 секунд
        // (короткие уходы — выбор фото, камера, окно разрешений — не считаются).
        if (prefs.lockEnabled && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > 30_000) locked = true
        train.resumeClock(manual = false)
    }

    override fun onResume() {
        super.onResume()
        // Подтягиваем данные с часов за последнюю неделю при каждом открытии
        Log.d("UI", "onResume")
        lifecycleScope.launch {
            syncHealth(LocalDate.now().minusDays(6), LocalDate.now())
            if (prefs.osReady) syncOpenScale(LocalDate.now().minusDays(30))
            repo.saveNow()
            train.refreshTabita()
        }
        train.refreshHealth()
        Alarms.refresh(this)
    }

    override fun onStop() {
        super.onStop()
        Log.d("UI", "onStop")
        stoppedAt = SystemClock.elapsedRealtime()
        repo.saveNow()
        // общий таймер тренировки стоит, пока приложение в фоне (таймер отдыха идёт — он в уведомлении)
        if (!isChangingConfigurations) train.pauseClock(manual = false)
    }

    // ---------- вход по отпечатку / блокировке телефона ----------

    private val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun canUseDeviceAuth(): Boolean =
        BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun promptDeviceAuth(onOk: () -> Unit = { locked = false }) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Log.i("Lock", "вход по отпечатку/блокировке телефона")
                onOk()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Log.i("Lock", "биометрия: ошибка $errorCode $errString")
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(t("Вход в Tabita"))
            .setSubtitle(t("Отпечаток или блокировка экрана телефона"))
            .setAllowedAuthenticators(authenticators)
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { Log.e("Lock", "authenticate", it) }
    }

    /**
     * 5.51: занятиям с таймера и записанным вручную — пульс с часов за их время (часы пишут пульс весь день,
     * даже если тренировку на них не запускали). Только если есть доступ к пульсу.
     */
    private suspend fun addHeartToManual(from: LocalDate, to: LocalDate) {
        if (health.hrPermission !in health.granted()) return
        val now = System.currentTimeMillis()
        var d = from
        while (!d.isAfter(to)) {
            val day = repo.entries.value[d.key()]
            if (day != null) repo.template.value.filter { it.source == Source.HC_EXERCISE }.forEach { item ->
                val list = Workouts.parse(day[Workouts.manualKey(item.id)])
                var changed = false
                val upd = list.map { s ->
                    val st = s.start
                    if (st == null || s.hist.isNotEmpty() || !Cardio.isCardio(s) || st + s.min * 60_000L > now) return@map s
                    val h = health.heartHist(st, st + s.min * 60_000L) ?: return@map s
                    changed = true
                    s.copy(hr = HrZones.avg(h), hrMax = h.keys.maxOrNull(), hist = h)
                }
                if (changed) { repo.setWorkouts(d.key(), item, upd); Log.i("Health", "пульс добавлен к занятиям ${d.key()}") }
            }
            d = d.plusDays(1)
        }
    }

    /**
     * 5.54: активные минуты за каждый день по пульсу с часов (весь день, не только тренировки):
     * зоны — по пульсу покоя на этот день, как в Google Health. Только если есть доступ к пульсу.
     */
    private suspend fun updateActiveMinutes(from: LocalDate, to: LocalDate) {
        if (health.hrPermission !in health.granted()) return
        val zone = java.time.ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val out = mutableMapOf<String, Map<String, String>>()
        var d = from
        while (!d.isAfter(to)) {
            val z = HrZones.of(repo.profile.value, prefs.hrMax, HrZones.latestRest(repo.template.value, repo.entries.value, d), d)
            if (z != null) {
                val s0 = d.atStartOfDay(zone).toInstant().toEpochMilli()
                val s1 = minOf(d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), now)
                val hist = if (s1 > s0) health.heartHist(s0, s1) else null
                if (hist != null) out[d.key()] = mapOf(ActiveMin.KEY to ActiveMin.from(hist, z).toString())
            }
            d = d.plusDays(1)
        }
        if (out.isNotEmpty()) { repo.setValues(out); Log.i("Health", "активные минуты: ${out.size} дн.") }
    }

    suspend fun syncHealth(from: LocalDate, to: LocalDate): String {
        if (!health.available) return t("Health Connect недоступен").also { syncStatus.value = it; hcConnected.value = false }
        if (health.granted().none { it in health.permissions }) {
            return t("Нет доступа к Health Connect").also { syncStatus.value = it; hcConnected.value = false }
        }
        hcConnected.value = true
        val msg = runCatching {
            val data = health.read(from, to, repo.template.value)
            Log.d("Health", "прочитано $from..$to: $data")
            repo.setValues(data)
            runCatching { addHeartToManual(from, to) }.onFailure { Log.e("Health", "пульс к ручным занятиям", it) }
            runCatching { updateActiveMinutes(from, to) }.onFailure { Log.e("Health", "активные минуты", it) }
            Hydration.updateNorms(repo, prefs, data.keys)
            t("Загружено с часов: %d дн. (%s–%s)", data.size, from.ruShort(), to.ruShort())
        }.getOrElse { Log.e("Health", "ошибка чтения", it); t("Ошибка чтения: %s", it.message) }
        Log.i("Health", msg)
        syncStatus.value = msg
        return msg
    }

    /** Загрузка всех показателей из openScale (from = null — вся история). */
    suspend fun syncOpenScale(from: LocalDate?): String {
        val pkg = prefs.osPackage
        if (pkg.isEmpty() || !openScale.granted(pkg)) return t("openScale не подключён")
        return runCatching {
            val days = openScale.readDays(pkg, prefs.osUserId, from)
            repo.ensureBodyItem()
            repo.setBodyDays(days.mapKeys { it.key.key() }, pkg)
            // 5.42: взвешивания до и после тренировок → личная потеря пота; норма воды за последние дни
            runCatching {
                val since = LocalDate.now().minusDays(30)
                val ws = openScale.weighIns(pkg, prefs.osUserId, since.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                val sessions = (0L..30L).flatMap { i ->
                    val d = repo.entries.value[since.plusDays(i).key()] ?: emptyMap()
                    repo.template.value.filter { it.source == Source.HC_EXERCISE }.flatMap { Workouts.sessions(d, it.id) }
                }
                val n = Hydration.addSamples(prefs, Hydration.findSamples(ws, sessions))
                if (n > 0) Log.i("Water", "новых замеров пота: $n, скорость ${Hydration.sweatRate(prefs).first} л/ч")
            }.onFailure { Log.e("Water", "замер пота", it) }
            Hydration.updateNorms(repo, prefs, (0L..30L).map { LocalDate.now().minusDays(it).key() })
            t("openScale: загружено дней — %d", days.size)
        }.getOrElse { Log.e("openScale", "ошибка чтения", it); t("Ошибка чтения: %s", it.message) }
            .also { Log.i("openScale", it) }
    }
}

/** Вкладки (5.30): Сегодня · Программа (план, графики, самочувствие) · Тренировки · Ещё. */
const val TAB_PROGRAM = 1
const val TAB_TRAIN = 2
const val TAB_MORE = 3

/**
 * Нижняя панель (5.28): вкладки крутятся вбок, если не помещаются (у каждой не меньше 80 dp —
 * подписи целиком); края гаснут со стрелкой — видно, что есть ещё. Выбранная вкладка сама прокручивается в видимую часть.
 */
@Composable
fun ScrollNavBar(tabs: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val s = androidx.compose.foundation.rememberScrollState()
    androidx.compose.material3.Surface(color = cs.surfaceContainer, tonalElevation = 3.dp) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()
            .windowInsetsPadding(androidx.compose.material3.NavigationBarDefaults.windowInsets)) {
            val itemW = maxOf(maxWidth / tabs.size, 80.dp)
            val itemPx = with(androidx.compose.ui.platform.LocalDensity.current) { itemW.toPx() }
            LaunchedEffect(selected, maxWidth) {
                val vis = with(this@BoxWithConstraints) { constraints.maxWidth }
                val l = (itemPx * selected).toInt(); val r = (itemPx * (selected + 1)).toInt()
                if (l < s.value) s.animateScrollTo(l) else if (r > s.value + vis) s.animateScrollTo(r - vis)
            }
            Row(Modifier.fillMaxWidth().height(80.dp).hScroll(s), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { i, (title, icon) ->
                    val on = i == selected
                    Column(
                        Modifier.width(itemW).fillMaxHeight()
                            .androidx_selectable(on, title) { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(width = 64.dp, height = 32.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (on) cs.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, null, tint = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant) }
                        Spacer(Modifier.height(4.dp))
                        Text(title, maxLines = 1, softWrap = false, color = if (on) cs.onSurface else cs.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
                                letterSpacing = androidx.compose.ui.unit.TextUnit(0f, androidx.compose.ui.unit.TextUnitType.Sp),
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
                    }
                }
            }
        }
    }
}

private fun Modifier.androidx_selectable(on: Boolean, label: String, onClick: () -> Unit) =
    this.then(Modifier.selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick))

@Composable
fun App(act: MainActivity, showRationale: Boolean) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var rationale by remember { mutableStateOf(showRationale) }
    val snack = remember { SnackbarHostState() }
    val scope: CoroutineScope = rememberCoroutineScope()
    val notify: (String) -> Unit = { m -> scope.launch { snack.showSnackbar(m) } }
    val wbStart = collect(act.wbStart)
    // 5.50: чекап самочувствия — во вкладке «Здоровье»
    LaunchedEffect(wbStart) { if (wbStart) { act.train.section = org.maittaa.tabita.train.SEC_WB; act.train.screen = org.maittaa.tabita.train.Screen.Home; tab = TAB_TRAIN } }
    val openProgram = collect(act.openProgram)
    LaunchedEffect(openProgram) { if (openProgram >= 0) { act.prefs.programSub = openProgram; tab = TAB_PROGRAM; act.openProgram.value = -1 } }
    val openSet = collect(act.openSettings)
    LaunchedEffect(openSet) { if (openSet != null) tab = TAB_MORE }
    val openTrain = collect(act.openTrain)
    LaunchedEffect(openTrain) { if (openTrain) { act.train.section = org.maittaa.tabita.train.SEC_STRENGTH; act.train.tab = 0; tab = TAB_TRAIN; act.openTrain.value = false } }
    LaunchedEffect(act.train.wantTab) { if (act.train.wantTab) { tab = TAB_TRAIN; act.train.wantTab = false } }
    val tabs = listOf(
        t("Сегодня") to Icons.Filled.CheckCircle,
        t("Программа") to Icons.Filled.Flag,
        t("Здоровье") to Icons.Filled.Favorite,
        t("Ещё") to Icons.Filled.Settings,
    )
    val trainActions = org.maittaa.tabita.train.rememberTrainActions(act, act.train)
    // во время тренировки — весь экран под неё
    val inWorkout = tab == TAB_TRAIN && act.train.screen == org.maittaa.tabita.train.Screen.Workout
    androidx.activity.compose.BackHandler(enabled = tab == TAB_TRAIN && act.train.screen != org.maittaa.tabita.train.Screen.Home) {
        val tm = act.train
        if (tm.screen is org.maittaa.tabita.train.Screen.TabitaData) tm.closeTabitaData() else tm.screen = org.maittaa.tabita.train.Screen.Home
    }

    androidx.compose.runtime.CompositionLocalProvider(org.maittaa.tabita.train.LocalTrainActions provides trainActions) {
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (!inWorkout) ScrollNavBar(tabs, tab) { i -> tab = i; Log.d("UI", "вкладка: ${tabs[i].first}") }
        },
    ) { pad ->
        // отступ под нижнюю панель «съедаем» из отступа клавиатуры: клавиатура и так закрывает панель,
        // иначе над ней остаётся пустая полоса высотой с панель
        val m = Modifier.padding(pad).consumeWindowInsets(pad)
        when (tab) {
            0 -> DayScreen(act, m, notify)
            TAB_PROGRAM -> ProgramHost(act, m, openDay = { d -> act.jumpDate.value = d.key(); tab = 0 })
            TAB_TRAIN -> androidx.compose.foundation.layout.Box(m) {
                androidx.compose.runtime.CompositionLocalProvider(org.maittaa.tabita.train.LocalWellbeing provides { mod -> WellbeingScreen(act, mod) },
                    org.maittaa.tabita.train.LocalCardio provides { tb, mod -> CardioScreen(act, tb, mod) },
                    org.maittaa.tabita.train.LocalHealthHub provides { mod -> HealthHub(act, mod) },
                    org.maittaa.tabita.train.LocalYoga provides { tb, mod -> YogaScreen(act, tb, mod) },
                    org.maittaa.tabita.train.LocalActivity provides { mod -> ActivityScreen(act, mod) },
                    org.maittaa.tabita.train.LocalBody provides { tb, mod -> BodyScreen(act, tb, mod) },
                    org.maittaa.tabita.train.LocalSleep provides { mod -> SleepScreen(act, mod) }) {
                    org.maittaa.tabita.train.App(act.train, trainActions)
                }
            }
            else -> SettingsScreen(act, m, notify)
        }
    }
    }

    if (rationale) {
        AlertDialog(
            onDismissRequest = { rationale = false },
            confirmButton = { TextButton(onClick = { rationale = false }) { Text(t("Понятно")) } },
            title = { Text(t("Зачем доступ к данным здоровья")) },
            text = {
                Text(
                    t(
                        "Приложение читает сон, шаги, тренировки, пульс покоя и вес из Health Connect, " +
                            "чтобы заполнять дневник автоматически, и записывает туда завершённые тренировки. " +
                            "Данные хранятся только на этом телефоне и никуда не отправляются."
                    )
                )
            },
        )
    }
}

@Composable
fun <T> collect(flow: kotlinx.coroutines.flow.StateFlow<T>): T {
    val v by androidx.compose.runtime.produceState(flow.value, flow) { flow.collect { value = it } }
    return v
}
