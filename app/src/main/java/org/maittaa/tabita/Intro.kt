package org.maittaa.tabita

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Знакомство при первом запуске (5.35) — в духе мастера настройки Android: значок и крупный заголовок слева,
 * текст, внизу «Назад / Далее», сверху полоса прогресса. Шаги: приветствие и язык → возможности →
 * о себе (пол, год рождения, рост, вес) → цель → данные с часов и весов → уведомления → готово.
 * Всё необязательное можно пропустить; данные хранятся только на телефоне.
 */
@Composable
fun IntroScreen(act: MainActivity, onDone: () -> Unit) {
    val prefs = act.prefs; val repo = act.repo
    var page by remember { mutableIntStateOf(0) }
    var dir by remember { mutableIntStateOf(1) }
    var lang by remember { mutableStateOf(I18n.lang) }
    // о себе
    val p0 = repo.profile.value
    var sex by remember { mutableStateOf(p0.sex) }
    var year by remember { mutableStateOf(p0.birthYear?.toString() ?: "") }
    var height by remember { mutableStateOf(p0.heightCm?.let { formatNumber(it) } ?: "") }
    var weight by remember { mutableStateOf(Anthro.weightOn(repo.template.value, repo.entries.value, LocalDate.now())?.let { formatNumber(it) } ?: "") }
    var goal by remember { mutableStateOf(Goal.of(prefs.planGoal)) }
    val pages = 7
    fun go(to: Int) { dir = if (to > page) 1 else -1; page = to.coerceIn(0, pages - 1); Log.d("Intro", "шаг $page") }
    fun finish() {
        val h = parseNumber(height)?.takeIf { it in 100.0..250.0 }
        val y = year.toIntOrNull()?.takeIf { it in 1900..LocalDate.now().year - 8 }
        repo.setProfile(Profile(h, y, sex))
        val w = parseNumber(weight)?.takeIf { it in 25.0..350.0 }
        val wItem = repo.template.value.firstOrNull { it.source == Source.HC_WEIGHT }
        if (w != null && wItem != null) repo.setValue(LocalDate.now().toString(), wItem.id, formatNumber(w).replace(',', '.'))
        goal?.let { g ->
            prefs.planGoal = g.id; prefs.planStart = LocalDate.now().toString(); prefs.planW0 = w ?: 0.0
        }
        // плитки — в удобной раскладке, на «Сегодня» — ненавязчивые подсказки про жесты
        runCatching { repo.setLayout(repo.ergonomicLayout()) }
        prefs.tileHint = true; prefs.tileBtnHint = true
        prefs.introDone = true
        runCatching { Reminders.scheduleAll(act) }
        Log.i("Intro", "знакомство пройдено: цель=${goal?.id}, профиль=${h != null}/${y != null}/$sex, вес=${w != null}")
        onDone()
    }
    BackHandler(page > 0) { go(page - 1) }
    key(lang) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                if (page > 0) LinearProgressIndicator({ page / (pages - 1f) }, Modifier.fillMaxWidth().height(3.dp))
                else Spacer(Modifier.height(3.dp))
                AnimatedContent(page, Modifier.weight(1f), transitionSpec = {
                    (slideInHorizontally { it * dir } togetherWith slideOutHorizontally { -it * dir })
                }, label = "intro") { p ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp)) {
                        when (p) {
                            0 -> Welcome(act, lang) { l -> prefs.lang = l; I18n.lang = l; lang = l }
                            1 -> Features()
                            2 -> AboutYou(sex, { sex = it }, year, { year = it }, height, { height = it }, weight, { weight = it })
                            3 -> GoalPage(sex, year, height, weight, goal) { goal = it }
                            4 -> HealthPage(act)
                            5 -> NotifPage(act)
                            else -> DonePage(goal, parseNumber(weight), parseNumber(height))
                        }
                    }
                }
                // нижняя панель — как в мастере настройки
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (page) {
                        0 -> Spacer(Modifier.weight(1f))
                        in 2..5 -> { TextButton({ go(page - 1) }) { Text(t("Назад")) }; Spacer(Modifier.weight(1f)) }
                        else -> { TextButton({ go(page - 1) }) { Text(t("Назад")) }; Spacer(Modifier.weight(1f)) }
                    }
                    if (page in 2..5) TextButton({ go(page + 1) }) { Text(t("Пропустить")) }
                    Spacer(Modifier.width(8.dp))
                    Button({ if (page == pages - 1) finish() else go(page + 1) }) {
                        Text(when (page) { 0 -> t("Начать"); pages - 1 -> t("Готово"); else -> t("Далее") })
                    }
                }
            }
        }
    }
}

/** Заголовок шага: значок в кружке и крупный текст слева — как в мастере настройки Android. */
@Composable
private fun Head(icon: ImageVector, title: String, text: String? = null) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(52.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(28.dp), tint = cs.onPrimaryContainer)
    }
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium)
    if (text != null) {
        Spacer(Modifier.height(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun Welcome(act: MainActivity, lang: String, setLang: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bmp = remember { ContextCompat.getDrawable(act, R.mipmap.ic_brain)?.toBitmap(288, 288)?.asImageBitmap() }
    Spacer(Modifier.height(40.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        bmp?.let { Image(it, null, Modifier.size(132.dp).clip(CircleShape)) }
        Spacer(Modifier.height(28.dp))
        Text(t("Добро пожаловать"), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(10.dp))
        Text(t("Tabita — дневник, программа к цели и тренировки в одном приложении. Настроим всё за пару минут."),
            style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(36.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Translate, null, tint = cs.onSurfaceVariant)
            FilterChip(lang != "en", { setLang("ru") }, label = { Text("Русский") })
            FilterChip(lang == "en", { setLang("en") }, label = { Text("English") })
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(26.dp), tint = cs.primary)
        Spacer(Modifier.width(18.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun Features() {
    Head(Icons.Filled.AutoAwesome, t("Что умеет Tabita"))
    Feature(Icons.Filled.CheckCircle, t("Чек-лист дня"), t("Оценка дня, сон, вода, питание, привычки — плитками, которые вы сами собираете и двигаете."))
    Feature(Icons.Filled.Flag, t("Программа к цели"), t("Похудение, мышцы или самочувствие: нормы на день, меню из ваших продуктов, вехи и советы под цель."))
    Feature(Icons.Filled.Insights, t("Графики и итоги"), t("Динамика по любому показателю, итоги месяца и что на самом деле влияет на ваши дни."))
    Feature(Icons.Filled.FitnessCenter, t("Тренировки"), t("Программа под ваше оборудование, таймер, прогресс и подсказка, стоит ли тренироваться сегодня."))
    Feature(Icons.Filled.MonitorHeart, t("Часы и весы"), t("Сон, шаги, пульс и вес подтягиваются из Health Connect — вводить руками не нужно."))
    Feature(Icons.Filled.Restaurant, t("Питание"), t("Справочник продуктов с БЖУ и ГИ, свои продукты по этикетке, калории по фото с помощью ИИ."))
    Feature(Icons.Filled.Lock, t("Всё у вас"), t("Данные хранятся только на телефоне, без аккаунтов. Резервная копия — одним файлом."))
}

@Composable
private fun AboutYou(
    sex: String?, setSex: (String?) -> Unit, year: String, setYear: (String) -> Unit,
    height: String, setHeight: (String) -> Unit, weight: String, setWeight: (String) -> Unit,
) {
    Head(Icons.Filled.Person, t("Немного о вас"),
        t("Из этого считаются расход калорий, ИМТ, нормы белка и воды и рекомендация цели. Можно пропустить и заполнить позже в профиле."))
    Text(t("Пол"), style = MaterialTheme.typography.labelLarge)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(sex == "m", { setSex("m") }, label = { Text(t("Мужской")) })
        FilterChip(sex == "f", { setSex("f") }, label = { Text(t("Женский")) })
    }
    Spacer(Modifier.height(16.dp))
    @Composable
    fun Num(v: String, set: (String) -> Unit, label: String, suffix: String, digits: Boolean = false) = OutlinedTextField(
        v, { set(it.filter { c -> c.isDigit() || (!digits && (c == ',' || c == '.')) }.take(6)) }, Modifier.fillMaxWidth(),
        label = { Text(label) }, suffix = { Text(suffix) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (digits) KeyboardType.Number else KeyboardType.Decimal))
    Num(year, setYear, t("Год рождения"), "", digits = true)
    Spacer(Modifier.height(10.dp))
    Num(height, setHeight, t("Рост"), t("см"))
    Spacer(Modifier.height(10.dp))
    Num(weight, setWeight, t("Вес"), t("кг"))
    Spacer(Modifier.height(8.dp))
    Text(t("Вес лучше утром натощак. Если подключите весы через Health Connect, дальше он будет подтягиваться сам."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun GoalPage(sex: String?, year: String, height: String, weight: String, goal: Goal?, set: (Goal) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val h = parseNumber(height)?.takeIf { it in 100.0..250.0 }?.div(100)
    val w = parseNumber(weight)?.takeIf { it in 25.0..350.0 }
    val bmi = if (h != null && w != null) w / (h * h) else null
    val rec = remember(bmi) {
        Plan.recommend(PlanStats(w, h, bmi, null, null, null, null, null, null, null, 0.0, 0.0, null, null, 0, 0, 0, 0, 7))
    }
    LaunchedEffect(Unit) { if (goal == null && bmi != null) set(rec.goal) }
    Head(Icons.Filled.Flag, t("Ваша цель"),
        if (bmi != null) t("По вашим данным рекомендую «%s»: %s. Выберите любую — сменить можно в любой момент.", rec.goal.title, rec.reasons.joinToString("; "))
        else t("Под цель строится программа: нормы на день, меню, тренировки и советы. Сменить можно в любой момент."))
    Goal.entries.forEach { g ->
        val on = goal == g
        Surface(
            Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).clickable { set(g) }
                .border(if (on) 2.dp else 1.dp, if (on) cs.primary else cs.outlineVariant, RoundedCornerShape(16.dp)),
            color = if (on) cs.secondaryContainer else cs.surface,
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.title + if (g == rec.goal && bmi != null) "  ·  " + t("рекомендую") else "", style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (on) FontWeight.SemiBold else null)
                    Text(t(g.about), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                androidx.compose.material3.RadioButton(on, { set(g) })
            }
        }
    }
}

@Composable
private fun HealthPage(act: MainActivity) {
    val cs = MaterialTheme.colorScheme
    val health = act.health
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(tick) { granted = runCatching { health.granted() }.getOrDefault(emptySet()) }
    val launcher = rememberLauncherForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { res ->
        Log.i("Intro", "Health Connect: $res"); tick++
        if (res.isNotEmpty()) scope.launch { runCatching { act.syncHealth(LocalDate.now().minusDays(29), LocalDate.now()) } }
    }
    Head(Icons.Filled.Sync, t("Часы и весы"),
        t("Tabita берёт сон, шаги, пульс, тренировки и вес из Health Connect — туда пишут Pixel Watch, Mi Fitness, Samsung Health, openScale и другие. Чем больше данных, тем точнее советы и программа."))
    val ok = granted.isNotEmpty()
    if (!health.available) Text(t("Health Connect на телефоне не найден — его можно установить из Google Play и подключить позже в «Ещё → Синхронизация»."),
        style = MaterialTheme.typography.bodyMedium, color = cs.error)
    else Surface(color = cs.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Favorite, null, tint = if (ok) cs.primary else cs.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Health Connect"), style = MaterialTheme.typography.titleMedium)
                Text(if (ok) t("Подключено: %d из %d видов данных", granted.size, health.permissions.size) else t("Не подключено"),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            if (!ok) Button({ runCatching { launcher.launch(health.permissions) } }) { Text(t("Подключить")) }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(t("Доступ можно в любой момент изменить или отозвать в настройках Health Connect."),
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
}

@Composable
private fun NotifPage(act: MainActivity) {
    val prefs = act.prefs
    val cs = MaterialTheme.colorScheme
    var day by remember { mutableStateOf(prefs.reminderOn) }
    var bed by remember { mutableStateOf(prefs.bedOn) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Log.i("Intro", "уведомления: $ok"); if (ok) pending?.invoke(); pending = null
    }
    fun withPerm(a: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(act, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pending = a; runCatching { perm.launch(Manifest.permission.POST_NOTIFICATIONS) }
        } else a()
    }
    Head(Icons.Filled.Notifications, t("Напоминания"), t("Ненавязчиво: одно вечером, если день не записан, и про отбой — время считается по вашему режиму и будильнику."))
    @Composable
    fun Opt(icon: ImageVector, title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = cs.primary); Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Switch(on, set)
        }
    }
    var remTime by remember { mutableIntStateOf(prefs.reminderTime) }
    var bedTime by remember { mutableIntStateOf(prefs.rhythmBed) }
    var lead by remember { mutableIntStateOf(prefs.bedLead) }
    var pick by remember { mutableStateOf<String?>(null) }
    Opt(Icons.Filled.CheckCircle, t("Заполнить день"), t("только если что-то не записано"), day) { v ->
        if (v) withPerm { prefs.reminderOn = true; day = true } else { prefs.reminderOn = false; day = false }
    }
    // 5.36: своё время — сразу здесь
    if (day) Row(Modifier.padding(start = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.AssistChip({ pick = "day" }, label = { Text(t("в %s", clockOf(remTime))) },
            leadingIcon = { Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp)) })
    }
    Opt(Icons.Filled.Bedtime, t("Отбой"), t("«скоро отбой» заранее и «пора спать» в само время"), bed) { v ->
        if (v) withPerm { prefs.bedOn = true; bed = true } else { prefs.bedOn = false; bed = false }
    }
    if (bed) Column(Modifier.padding(start = 40.dp)) {
        Text(t("Время отбоя"), style = MaterialTheme.typography.labelLarge)
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(bedTime < 0, { bedTime = -1; prefs.rhythmBed = -1 }, label = { Text(t("по режиму"), maxLines = 1, softWrap = false) })
            FilterChip(bedTime >= 0, { pick = "bed" }, label = { Text(if (bedTime >= 0) clockOf(bedTime) else t("своё время"), maxLines = 1, softWrap = false) },
                leadingIcon = { Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp)) })
        }
        Text(if (bedTime < 0) t("Считается по сну и будильнику на завтра, подстраивается сам.") else t("Каждый день в одно время — режим держится лучше."),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(t("Предупредить заранее"), style = MaterialTheme.typography.labelLarge)
        // чипы переносятся на новую строку, а не сжимаются в столбик (крупный шрифт)
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 30, 60, 90).forEach { m ->
                FilterChip(lead == m, { lead = m; prefs.bedLead = m }, label = { Text(if (m == 0) t("нет") else t("за %d мин", m), maxLines = 1, softWrap = false) })
            }
        }
    }
    pick?.let { which ->
        TimeDialog(if (which == "day") t("Время напоминания") else t("Время отбоя"),
            if (which == "day") remTime else (if (bedTime >= 0) bedTime else 23 * 60), { m ->
                if (which == "day") { remTime = m; prefs.reminderTime = m } else { bedTime = m; prefs.rhythmBed = m }
                pick = null
            }) { pick = null }
    }
    Spacer(Modifier.height(8.dp))
    Text(t("Время и остальные уведомления — в «Ещё → Уведомления»."), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
}

@Composable
private fun DonePage(goal: Goal?, w: Double?, h: Double?) {
    Head(Icons.Filled.CheckCircle, t("Всё готово"),
        t("Начните с чек-листа «Сегодня»: отметьте день, запишите еду и воду. Через неделю появятся первые выводы, через месяц — итоги."))
    val cs = MaterialTheme.colorScheme
    listOfNotNull(
        goal?.let { t("Цель: %s", it.title) },
        if (w != null && h != null && h in 100.0..250.0) t("ИМТ: %s", formatNumber(Math.round(w / (h / 100 * h / 100) * 10) / 10.0)) else null,
        t("Плитки и пункты чек-листа меняются в режиме правки — кнопка с карандашом."),
        t("Знакомство можно пройти снова: «Ещё → О приложении»."),
    ).forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp), color = cs.onSurface) }
}
