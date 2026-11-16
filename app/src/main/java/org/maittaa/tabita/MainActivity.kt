package org.maittaa.tabita

import androidx.compose.foundation.layout.consumeWindowInsets
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
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
    /** Открыть день на вкладке «День» (из «Итогов месяца»). */
    val jumpDate = MutableStateFlow<String?>(null)
    /** Открыть вкладку «Самочувствие» и начать чекап (из плитки дня). */
    val wbStart = MutableStateFlow(false)

    /** Приложение заблокировано (показан экран входа). */
    var locked by mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = Prefs(applicationContext)
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
        val showRationale = intent?.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
            intent?.action == "android.intent.action.VIEW_PERMISSION_USAGE"
        setContent {
            AppTheme {
                if (locked) LockScreen(this) else App(this, showRationale)
            }
        }
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
    }

    override fun onResume() {
        super.onResume()
        // Подтягиваем данные с часов за последнюю неделю при каждом открытии
        Log.d("UI", "onResume")
        lifecycleScope.launch {
            syncHealth(LocalDate.now().minusDays(6), LocalDate.now())
            if (prefs.osReady) syncOpenScale(LocalDate.now().minusDays(30))
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("UI", "onStop")
        stoppedAt = SystemClock.elapsedRealtime()
        repo.saveNow()
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

    suspend fun syncHealth(from: LocalDate, to: LocalDate): String {
        if (!health.available) return t("Health Connect недоступен").also { syncStatus.value = it }
        if (health.granted().none { it in health.permissions }) {
            return t("Нет доступа к Health Connect").also { syncStatus.value = it }
        }
        val msg = runCatching {
            val data = health.read(from, to, repo.template.value)
            Log.d("Health", "прочитано $from..$to: $data")
            repo.setValues(data)
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
            t("openScale: загружено дней — %d", days.size)
        }.getOrElse { Log.e("openScale", "ошибка чтения", it); t("Ошибка чтения: %s", it.message) }
            .also { Log.i("openScale", it) }
    }
}

@Composable
fun App(act: MainActivity, showRationale: Boolean) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var rationale by remember { mutableStateOf(showRationale) }
    val snack = remember { SnackbarHostState() }
    val scope: CoroutineScope = rememberCoroutineScope()
    val notify: (String) -> Unit = { m -> scope.launch { snack.showSnackbar(m) } }
    val wbStart = collect(act.wbStart)
    LaunchedEffect(wbStart) { if (wbStart) tab = 2 }
    val tabs = listOf(
        t("День") to Icons.Filled.CheckCircle,
        t("Графики") to Icons.Filled.Insights,
        t("Самочувствие") to Icons.Filled.Psychology,
        t("Ещё") to Icons.Filled.Settings,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (title, icon) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i; Log.d("UI", "вкладка: $title") },
                        icon = { Icon(icon, null) }, label = { Text(title) },
                    )
                }
            }
        },
    ) { pad ->
        // отступ под нижнюю панель «съедаем» из отступа клавиатуры: клавиатура и так закрывает панель,
        // иначе над ней остаётся пустая полоса высотой с панель
        val m = Modifier.padding(pad).consumeWindowInsets(pad)
        when (tab) {
            0 -> DayScreen(act, m, notify)
            1 -> StatsScreen(act.repo, act.prefs, m, openDay = { d -> act.jumpDate.value = d.key(); tab = 0 })
            2 -> WellbeingScreen(act, m)
            else -> SettingsScreen(act, m, notify)
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
                        "Приложение только читает сон, шаги, тренировки, пульс покоя и вес из Health Connect, " +
                            "чтобы заполнять дневник автоматически. Данные хранятся только на этом телефоне " +
                            "и никуда не отправляются."
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
