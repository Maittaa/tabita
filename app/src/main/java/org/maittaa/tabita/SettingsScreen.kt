package org.maittaa.tabita

import androidx.compose.ui.platform.testTag

import androidx.compose.runtime.DisposableEffect

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.RadioButton

import androidx.compose.material.icons.filled.PlayArrow

import androidx.compose.material.icons.filled.NotificationsActive

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.DeleteForever
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Watch
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
    ) {
        Text(text, Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, null)
    }
}

@Composable
private fun Hint(text: String) = Text(
    text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
)

private fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

/** Строка раздела настроек на главном экране «Ещё». */
private data class SetRow(val id: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val sub: String, val ok: Boolean? = null)

@Composable
private fun SettingsRow(r: SetRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(r.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.bodyLarge)
            Text(r.sub, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = when (r.ok) { true -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant })
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun SubTitle(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(bottom = 4.dp))

private fun aiTitle(p: Prefs) = when {
    p.aiProvider == "gigachat" -> "GigaChat"
    p.aiProvider == "anthropic" -> "Claude"
    p.aiUrl.contains("openrouter") -> "OpenRouter"
    p.aiUrl.contains("openai.com") -> "OpenAI"
    else -> p.aiModel.ifBlank { t("свой сервис") }
}

private fun profileSummary(p: Profile): String =
    listOfNotNull(
        p.heightCm?.let { formatNumber(it) + " " + t("см") },
        p.age()?.let { t("%d лет", it) },
        when { p.male -> t("мужской"); p.female -> t("женский"); else -> null },
    ).joinToString(" · ").ifEmpty { t("не заполнен — нужен для ИМТ, FFMI и расхода энергии") }

/** Раздел «Профиль»: рост, год рождения, пол и что из них считается. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileBody(repo: Repo) {
    val p = collect(repo.profile)
    var hText by remember { mutableStateOf(p.heightCm?.let { formatNumber(it) } ?: "") }
    var yText by remember { mutableStateOf(p.birthYear?.toString() ?: "") }
    val year = LocalDate.now().year
    val hVal = parseNumber(hText)
    val hErr = hText.isNotBlank() && (hVal == null || hVal !in 100.0..250.0)
    val yVal = yText.trim().toIntOrNull()
    val yErr = yText.isNotBlank() && (yVal == null || yVal !in (year - 110)..(year - 10))

    Hint(t("Данные постоянные, поэтому они здесь, а не в чек-листе. Нужны только для расчётов в графиках."))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            hText, { v ->
                hText = v.filter { it.isDigit() || it == ',' || it == '.' }.take(5)
                val n = parseNumber(hText)
                if (hText.isBlank()) repo.setProfile(repo.profile.value.copy(heightCm = null))
                else if (n != null && n in 100.0..250.0) repo.setProfile(repo.profile.value.copy(heightCm = n))
            },
            Modifier.weight(1f), label = { Text(t("Рост, см")) }, singleLine = true, isError = hErr,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        )
        OutlinedTextField(
            yText, { v ->
                yText = v.filter { it.isDigit() }.take(4)
                val n = yText.toIntOrNull()
                if (yText.isBlank()) repo.setProfile(repo.profile.value.copy(birthYear = null))
                else if (n != null && n in (year - 110)..(year - 10)) repo.setProfile(repo.profile.value.copy(birthYear = n))
            },
            Modifier.weight(1f), label = { Text(t("Год рождения")) }, singleLine = true, isError = yErr,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(t("Пол — для формулы базового обмена"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("m" to t("Мужской"), "f" to t("Женский")).forEach { (k, title) ->
            FilterChip(selected = p.sex == k, onClick = {
                repo.setProfile(p.copy(sex = if (p.sex == k) null else k))
            }, label = { Text(title) })
        }
    }

    // что получается из профиля прямо сейчас
    val entries = collect(repo.entries)
    val tmpl = collect(repo.template)
    val today = LocalDate.now()
    val w = remember(entries, tmpl) { Anthro.weightOn(tmpl, entries, today) }
    val lean = remember(entries, tmpl) {
        (0..60).firstNotNullOfOrNull { i -> entries[today.minusDays(i.toLong()).toString()]?.let { Anthro.leanOf(tmpl, it) } }
    }
    val h = p.h
    if (h != null) {
        Spacer(Modifier.height(10.dp))
        SubTitle(t("Что считается"))
        val (lo, hi) = Anthro.normalWeight(h)
        val rows = listOfNotNull(
            w?.let { t("ИМТ") to formatNumber(Math.round(Anthro.bmi(it, h) * 10) / 10.0) + t(" (вес %s кг)", formatNumber(it)) },
            t("Нормальный вес по ИМТ") to t("%s–%s кг", formatNumber(Math.round(lo).toDouble()), formatNumber(Math.round(hi).toDouble())),
            lean?.let { t("FFMI") to formatNumber(Math.round(Anthro.ffmi(it, h) * 10) / 10.0) + " · " + t("норм. %s", formatNumber(Math.round(Anthro.ffmiNorm(it, h) * 10) / 10.0)) },
            w?.let { Anthro.bmr(p, it) }?.let { t("Базовый обмен") to Anthro.kcal(it) },
            w?.let { Anthro.bmr(p, it) }?.let { t("Расход в спокойный день") to Anthro.kcal(it * 1.2) },
            Anthro.strideM(p)?.let { t("Длина шага") to t("%d см · 10 000 шагов ≈ %s км", Math.round(it * 100).toInt(), formatNumber(Math.round(it * 100) / 10.0)) },
        )
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (w == null) Hint(t("Вес ещё не записан — ИМТ и базовый обмен появятся после первого взвешивания."))
        else if (p.age() == null) Hint(t("Укажите год рождения — без него базовый обмен не считается."))
        if (lean == null) Hint(t("FFMI нужен процент жира — его дают весы openScale."))
        Hint(t("В графиках: карточка «Тело и рост» (ИМТ, FFMI, FMI) в группе «Весы», «Энергобаланс» в «Питании», километры в карточке шагов."))
    }
}

@Composable
private fun AboutBody() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bmp = remember {
        runCatching { ctx.packageManager.getApplicationIcon(ctx.packageName).toBitmap(160, 160).asImageBitmap() }.getOrNull()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        bmp?.let { androidx.compose.foundation.Image(it, null, Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape)) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Tabita", style = MaterialTheme.typography.headlineSmall)
            Text(t("версия %s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(t("Личный дневник: ежедневный чек-лист, плитки дня, сон, питание, финансы, самочувствие и графики за любой период."),
        style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(10.dp))
    SubTitle(t("Что нового в %s", Changelog.current.version))
    Changelog.current.items.forEach { Text("• " + t(it), style = MaterialTheme.typography.bodyMedium) }
    // прошлые версии — выпадающим списком
    var pick by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    Spacer(Modifier.height(8.dp))
    Box {
        OutlinedButton(onClick = { menu = true }, Modifier.fillMaxWidth()) {
            Text(pick?.let { t("Версия %s", it) } ?: t("Предыдущие версии"), Modifier.weight(1f))
            Icon(if (menu) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        DropdownMenu(menu, { menu = false }, Modifier.heightIn(max = 320.dp)) {
            Changelog.all.drop(1).forEach { r ->
                DropdownMenuItem(text = { Text(r.version) }, onClick = { pick = r.version; menu = false })
            }
        }
    }
    Changelog.all.firstOrNull { it.version == pick }?.let { r ->
        Column(Modifier.padding(top = 6.dp)) {
            r.items.forEach { Text("• " + t(it), style = MaterialTheme.typography.bodyMedium) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Hint(t("Данные хранятся только на этом телефоне. Из Health Connect и openScale данные читаются, в Health Connect записываются только завершённые тренировки; в сеть уходят лишь фото и описание блюда — в выбранный вами ИИ-сервис."))
    Hint(ctx.packageName)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(act: MainActivity, modifier: Modifier, notify: (String) -> Unit) {
    val repo = act.repo
    val health = act.health
    val prefs = act.prefs
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }
    val status = collect(act.syncStatus)
    val sdk = remember(refresh) { health.sdkStatus() }
    // для перерисовки после изменения настроек
    var tick by remember { mutableIntStateOf(0) }
    fun changed() {
        tick++
        Log.d("Settings", "напоминание=${prefs.reminderOn} ${prefs.reminderTime}, мотивации=${prefs.motivOn} ${prefs.motivPerDay}/день")
        runCatching { Reminders.scheduleAll(act) }.onFailure { Log.e("Remind", "scheduleAll", it) }
    }

    LaunchedEffect(refresh) { granted = health.granted() }

    val permLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { res ->
        Log.i("Health", "результат запроса доступа: $res")
        refresh++
        if (res.any { it in health.permissions }) scope.launch {
            busy = true
            notify(act.syncHealth(LocalDate.now().minusDays(29), LocalDate.now()))
            busy = false
        } else notify(t("Доступ не выдан"))
    }
    var pendingNotif by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Log.i("Remind", "разрешение на уведомления: $ok")
        if (ok) pendingNotif?.invoke() else notify(t("Без разрешения уведомления не будут показываться"))
        pendingNotif = null
    }
    fun withNotifPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(act, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingNotif = action
            Log.i("Remind", "запрос разрешения на уведомления")
            runCatching { notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
                .onFailure { Log.e("Remind", "запрос разрешения", it); notify(t("Ошибка: %s", it.message)) }
        } else action()
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            act.contentResolver.openOutputStream(uri)!!.use { it.write(buildCsv(repo).toByteArray(Charsets.UTF_8)) }
        }.onSuccess { notify(t("Таблица сохранена")) }.onFailure { notify(t("Ошибка: %s", it.message)) }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            act.contentResolver.openOutputStream(uri)!!.use { repo.writeBackup(it) }
        }.onSuccess { notify(t("Резервная копия сохранена")) }.onFailure { notify(t("Ошибка: %s", it.message)) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    // ---------- openScale ----------
    var osUsers by remember { mutableStateOf<List<OpenScale.User>>(emptyList()) }
    var osBusy by remember { mutableStateOf(false) }
    var osPkgCandidate by remember { mutableStateOf("") }
    suspend fun osAfterGrant(pkg: String) {
        prefs.osPackage = pkg
        osUsers = runCatching { act.openScale.users(pkg) }.onFailure { Log.e("openScale", "users", it) }.getOrDefault(emptyList())
        Log.i("openScale", "пакет $pkg, API ${act.openScale.apiVersion(pkg)}, пользователи: ${osUsers.map { "${it.id}:${it.name}" }}")
        if (osUsers.none { it.id == prefs.osUserId }) prefs.osUserId = osUsers.firstOrNull()?.id ?: -1
        if (prefs.osReady) {
            osBusy = true
            val hidden = act.repo.template.value.any { it.id == "weight" && !it.hidden && it.source == Source.HC_WEIGHT }
            notify(act.syncOpenScale(null))
            if (hidden) notify(t("Пункт «Вес» скрыт: вес и другие показатели теперь в блоке «Весы»"))
            osBusy = false
        } else notify(t("В openScale нет пользователей"))
        tick++
    }
    val osPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Log.i("openScale", "разрешение $osPkgCandidate: $ok")
        if (ok) scope.launch { osAfterGrant(osPkgCandidate) } else notify(t("Доступ не выдан"))
    }
    LaunchedEffect(Unit) {
        val pkg = prefs.osPackage
        if (pkg.isNotEmpty() && act.openScale.granted(pkg)) osUsers = runCatching { act.openScale.users(pkg) }.getOrDefault(emptyList())
    }

    var timeDialog by remember { mutableStateOf<Triple<String, Int, (Int) -> Unit>?>(null) }
    var pinDialog by remember { mutableStateOf(false) }
    var newMotiv by remember { mutableStateOf("") }

    @Suppress("UNUSED_EXPRESSION") tick

    var open by remember { mutableStateOf<String?>(null) }
    val openReq = collect(act.openSettings)
    androidx.compose.runtime.LaunchedEffect(openReq) { openReq?.let { open = it; act.openSettings.value = null } }
    var wipeAsk by remember { mutableStateOf(false) }

    /** Содержимое раздела во всплывающем окне. */
    @Composable
    fun SectionBody(id: String) {
        when (id) {
            "appearance" -> {
            Text(t("Значок приложения"), style = MaterialTheme.typography.labelLarge)
            var icon by remember { mutableStateOf(runCatching { AppIcons.current(act) }.getOrDefault("brain")) }
            // 5.34: значки — одним рядом с прокруткой вбок; выбранный — в видимой части
            val iconState = androidx.compose.foundation.lazy.rememberLazyListState(
                initialFirstVisibleItemIndex = (AppIcons.all.indexOfFirst { it.first == icon } - 1).coerceAtLeast(0))
            androidx.compose.foundation.lazy.LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    .scrollHint({ iconState.canScrollBackward }, { iconState.canScrollForward }, MaterialTheme.colorScheme.onSurfaceVariant),
                state = iconState, horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            ) {
                items(AppIcons.all.size) { i ->
                    val (k, v) = AppIcons.all[i]
                    val (name, res) = v
                    val bmp = remember(res) {
                        androidx.core.content.ContextCompat.getDrawable(act, res)?.let {
                            // адаптивный значок рисуется уже с маской — как на рабочем столе
                            it.toBitmap(144, 144).asImageBitmap()
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp).clickable {
                        if (k != icon) { AppIcons.set(act, k); icon = k; notify(t("Значок изменится на рабочем столе через несколько секунд")) }
                    }) {
                        bmp?.let {
                            androidx.compose.foundation.Image(it, name,
                                Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                    .border(if (icon == k) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
                        }
                        Text(t(name), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp), maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = if (icon == k) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Hint(if (Compat.themedIcons) t("С включёнными «тематическими значками» Android перекрасит значок под цвета обоев.") else Compat.themedIconHelp())
            Spacer(Modifier.height(10.dp))
            Text(t("Тема"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("system" to t("Как в системе"), "light" to t("Светлая"), "dark" to t("Тёмная")).forEach { (k, title) ->
                    FilterChip(selected = Look.mode == k, onClick = { Look.mode = k; prefs.themeMode = k }, label = { Text(title) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(t("Акцентный цвет"), style = MaterialTheme.typography.labelLarge)
            if (!Compat.dynamicColors) Hint(Compat.dynamicHelp())
            if (Look.dynamicAvailable) FilterChip(
                selected = Look.accent == "dynamic", onClick = { Look.accent = "dynamic"; prefs.accent = "dynamic" },
                label = { Text(t("Как в системе (Material You)")) },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 6.dp)) {
                Look.accents.forEach { (k, v) ->
                    val (name, color) = v
                    val on = Look.accent == k
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color)
                            .border(if (on) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape)
                            .clickable { Look.accent = k; prefs.accent = k },
                        contentAlignment = Alignment.Center,
                    ) { if (on) Icon(Icons.Filled.Check, t(name), tint = androidx.compose.ui.graphics.Color.White) }
                }
            }
            // 5.43: колонок в сетке плиток
            Spacer(Modifier.height(14.dp))
            Text(t("Сетка плиток на «Сегодня»"), style = MaterialTheme.typography.labelLarge)
            var cols by remember { mutableIntStateOf(prefs.gridCols) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                GRID_COLS_RANGE.forEach { n ->
                    FilterChip(cols == n, { cols = n; prefs.gridCols = n; Log.i("Settings", "колонок: $n") },
                        label = { Text(if (n == 4) t("%d колонки", n) else t("%d колонок", n), maxLines = 1, softWrap = false) })
                }
            }
            Hint(t("Больше колонок — мельче клетка: плитки уменьшаются и по ширине, и по высоте, на экран помещается больше. Развёрнутые плитки остаются во всю ширину."))
        }
            "rhythm" -> RhythmSettings(repo, prefs)
            "profile" -> ProfileBody(repo)
            "bells" -> BellsBody(act)
            "lang" -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("" to t("Как в системе"), "ru" to "Русский", "en" to "English").forEach { (code, title) ->
                    FilterChip(selected = prefs.lang == code, onClick = {
                        prefs.lang = code; I18n.lang = I18n.resolve(code); Reminders.createChannels(act); tick++
                    }, label = { Text(title) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("₽", "$", "€", "₸", "₴", "Br").forEach { c ->
                    FilterChip(selected = prefs.currency == c, onClick = { prefs.currency = c; I18n.currency = c; tick++ }, label = { Text(c) })
                }
            }
        }
            "hc" -> {
            val hasAccess = granted.any { it in health.permissions }
            Text(
                when {
                    sdk == HealthConnectClient.SDK_UNAVAILABLE -> t("Health Connect недоступен на этом телефоне.")
                    sdk == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> t("Нужно установить или обновить Health Connect.")
                    hasAccess -> t("Подключено: доступно %d из %d типов данных.", granted.count { it in health.permissions }, health.permissions.size)
                    else -> t("Не подключено.")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (hasAccess && status.isNotEmpty()) Hint(status)
            if (sdk != HealthConnectClient.SDK_AVAILABLE) Hint(Compat.hcHelp())
            Spacer(Modifier.height(8.dp))
            when (sdk) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    Button(onClick = {
                        Log.i("Health", "запрос доступа к Health Connect")
                        runCatching { permLauncher.launch(health.permissions + health.historyPermission) }
                            .onFailure { Log.e("Health", "запрос доступа", it); notify(t("Ошибка: %s", it.message)) }
                    }, Modifier.fillMaxWidth()) {
                        Text(if (hasAccess) t("Изменить доступ") else t("Подключить часы"))
                    }
                    if (hasAccess) {
                        OutlinedButton(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                notify(act.syncHealth(LocalDate.now().minusDays(364), LocalDate.now()))
                                busy = false
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) t("Загружаю…") else t("Загрузить историю за год")) }
                    }
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Button(onClick = {
                    act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
                }, Modifier.fillMaxWidth()) { Text(t("Открыть Google Play")) }
                else -> if (!Compat.hcBuiltIn) OutlinedButton(onClick = {
                    runCatching { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))) }
                        .onFailure { notify(t("Google Play не найден")) }
                }, Modifier.fillMaxWidth()) { Text(t("Открыть Google Play")) }
            }
            Spacer(Modifier.height(8.dp))
            Hint(
                t(
                    "Часы: в приложении Fitbit (Google Health) включите синхронизацию с Health Connect.\n" +
                        "Весы Xiaomi: вес попадает в Health Connect через Mi Fitness или Zepp Life — включите там синхронизацию " +
                        "с Health Connect. Приложение Xiaomi Home вес в Health Connect не передаёт.\n" +
                        "Если вес не появился — нажмите «Изменить доступ» и разрешите чтение веса."
                )
            )
            Spacer(Modifier.height(8.dp))
            var walk by remember { mutableStateOf(prefs.countWalking) }
            SwitchRow(t("Засчитывать ходьбу как тренировку"), walk) { walk = it; prefs.countWalking = it; repo.recountAllWorkouts() }
            Hint(t("Часы часто сами записывают прогулки как тренировки. Ходьба уже видна в шагах, поэтому по умолчанию в минуты тренировок не идёт — иначе цель «тренировки» выполняется дорогой до работы."))
        }
            "os" -> {
            val installed = remember { act.openScale.installed() }
            val pkg = prefs.osPackage
            val connected = pkg.isNotEmpty() && act.openScale.granted(pkg)
            when {
                installed.isEmpty() -> Hint(t("openScale на телефоне не найден. Это бесплатное приложение для умных весов — через него Tabita получает взвешивания."))
                !connected -> {
                    Hint(t("Будут загружаться все показатели взвешиваний: вес, жир, вода, мышцы, кости, ИМТ и другие."))
                    Spacer(Modifier.height(6.dp))
                    installed.forEach { p ->
                        Button(onClick = {
                            osPkgCandidate = p
                            Log.i("openScale", "запрос разрешения ${act.openScale.permission(p)}")
                            if (act.openScale.granted(p)) scope.launch { osAfterGrant(p) }
                            else runCatching { osPerm.launch(act.openScale.permission(p)) }
                                .onFailure { Log.e("openScale", "запрос разрешения", it); notify(t("Ошибка: %s", it.message)) }
                        }, Modifier.fillMaxWidth()) {
                            Text(if (installed.size > 1) t("Подключить %s", p.removePrefix("com.health.")) else t("Подключить openScale"))
                        }
                    }
                }
                else -> {
                    Text(t("Подключено: %s", pkg.removePrefix("com.health.")), style = MaterialTheme.typography.bodyMedium)
                    if (osUsers.size > 1) {
                        Text(t("Пользователь"), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            osUsers.forEach { u ->
                                FilterChip(selected = prefs.osUserId == u.id, onClick = { prefs.osUserId = u.id; tick++ },
                                    label = { Text(u.name.ifBlank { "#${u.id}" }) })
                            }
                        }
                    }
                    OutlinedButton(enabled = !osBusy, onClick = {
                        scope.launch { osBusy = true; notify(act.syncOpenScale(null)); osBusy = false }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (osBusy) t("Загружаю…") else t("Загрузить всю историю")) }
                    Hint(t("Новые взвешивания подтягиваются при каждом открытии приложения."))
                }
            }
            // 5.41: пошаговая инструкция; раскрыта, пока весы не подключены
            Spacer(Modifier.height(8.dp))
            OpenScaleGuideCard(open0 = !connected)
        }
            "notif" -> {
            SwitchRow(t("Напоминать заполнить день"), prefs.reminderOn) { on ->
                if (on) withNotifPermission { prefs.reminderOn = true; changed() } else { prefs.reminderOn = false; changed() }
            }
            if (prefs.reminderOn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Время"), Modifier.weight(1f))
                    TextButton(onClick = {
                        timeDialog = Triple(t("Время напоминания"), prefs.reminderTime) { prefs.reminderTime = it; changed() }
                    }) { Text(hhmm(prefs.reminderTime)) }
                }
                SwitchRow(t("Только если что-то не записано"), prefs.reminderOnlyIfEmpty) { prefs.reminderOnlyIfEmpty = it; changed() }
            }
            Spacer(Modifier.height(8.dp))
            // 5.65: воскресный обзор недели
            SwitchRow(t("Обзор недели по воскресеньям"), prefs.weeklyOn) { on ->
                if (on) withNotifPermission { prefs.weeklyOn = true; changed() } else { prefs.weeklyOn = false; changed() }
            }
            Hint(t("В 19:00: сон, активность, вес и белок за неделю, одна правка на следующую и главная находка из «Что влияет»."))
            Spacer(Modifier.height(8.dp))
            SwitchRow(t("Напоминать об отбое"), prefs.bedOn) { on ->
                if (on) withNotifPermission { prefs.bedOn = true; changed() } else { prefs.bedOn = false; changed() }
            }
            if (prefs.bedOn) {
                val (bedT, rr) = remember(tick) { Reminders.bedtimeAt(act.repo, prefs, java.time.LocalDateTime.now()) }
                Hint(t("Ближайший отбой — %s: по «Ритму дня» (подъём %s%s, минус нужный сон). Меняется сам вместе с режимом и будильником.",
                    clockOf(bedT.hour * 60 + bedT.minute), clockOf(rr.nextWake), if (rr.alarm) t(" по будильнику") else ""))
                Text(t("Предупредить заранее: %s", if (prefs.bedLead == 0) t("нет") else t("за %d мин", prefs.bedLead)))
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 30, 60, 90).forEach { m ->
                        FilterChip(prefs.bedLead == m, { prefs.bedLead = m; changed() }, label = { Text(if (m == 0) t("нет") else t("%d мин", m), maxLines = 1, softWrap = false) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow(t("Мотивирующие уведомления в случайное время"), prefs.motivOn) { on ->
                if (on) withNotifPermission { prefs.motivOn = true; changed() } else { prefs.motivOn = false; changed() }
            }
            if (prefs.motivOn) {
                Text(t("Сколько в день: %d", prefs.motivPerDay))
                Slider(prefs.motivPerDay.toFloat(), { prefs.motivPerDay = it.toInt(); tick++ }, valueRange = 1f..6f, steps = 4,
                    onValueChangeFinished = { changed() })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Когда"), Modifier.weight(1f))
                    TextButton(onClick = { timeDialog = Triple(t("С"), prefs.motivFrom) { prefs.motivFrom = it; changed() } }) {
                        Text(t("с %s", hhmm(prefs.motivFrom)))
                    }
                    TextButton(onClick = { timeDialog = Triple(t("До"), prefs.motivTo) { prefs.motivTo = it; changed() } }) {
                        Text(t("до %s", hhmm(prefs.motivTo)))
                    }
                }
                Text(t("Ваши фразы"), style = MaterialTheme.typography.labelLarge)
                prefs.motivations.forEachIndexed { i, m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("• $m", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { prefs.motivations = prefs.motivations.filterIndexed { j, _ -> j != i }; changed() }) {
                            Icon(Icons.Filled.Close, t("Удалить"))
                        }
                    }
                }
                OutlinedTextField(
                    newMotiv, { newMotiv = it }, Modifier.fillMaxWidth(),
                    placeholder = { Text(t("Новая фраза, например: «Ты справишься!»")) },
                    trailingIcon = {
                        TextButton(enabled = newMotiv.isNotBlank(), onClick = {
                            prefs.motivations = prefs.motivations + newMotiv.trim(); newMotiv = ""; changed()
                        }) { Text(t("Добавить")) }
                    },
                )
                TextButton(enabled = prefs.motivations.isNotEmpty(), onClick = {
                    withNotifPermission { Reminders.show(act, "motiv", 999, t("Мотивация"), prefs.motivations.random()) }
                }) { Text(t("Показать случайную сейчас")) }
            }
            Hint(t("Если уведомления не приходят, отключите для Tabita экономию заряда в настройках телефона."))
        }
            "ai" -> {
            // 5.41: одно короткое пояснение вместо текста + плашки про VPN; чипы короче, GigaChat первым
            Hint(
                t(
                    "Фото или описание блюда уходит в выбранный сервис ИИ, он оценивает калории, БЖУ и ГИ. " +
                        "Нужен ваш ключ этого сервиса, оценка приблизительная. Из России без VPN работает GigaChat."
                )
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val presets = listOf(
                    Triple("GigaChat", "gigachat", GigaChat.BASE) to "GigaChat-2-Pro",
                    Triple("OpenRouter", "openai", "https://openrouter.ai/api/v1") to "google/gemini-2.5-flash",
                    Triple("OpenAI", "openai", "https://api.openai.com/v1") to "gpt-4o-mini",
                    Triple("Claude", "anthropic", "https://api.anthropic.com") to "claude-haiku-5-5",
                )
                presets.forEach { (p, model) ->
                    FilterChip(selected = prefs.aiProvider == p.second && prefs.aiUrl == p.third, onClick = {
                        prefs.aiProvider = p.second; prefs.aiUrl = p.third; prefs.aiModel = model; tick++
                    }, label = { Text(p.first, maxLines = 1, softWrap = false) })
                }
                FilterChip(
                    selected = prefs.aiProvider == "openai" && prefs.aiUrl !in listOf("https://openrouter.ai/api/v1", "https://api.openai.com/v1"),
                    onClick = { prefs.aiProvider = "openai"; prefs.aiUrl = ""; tick++ },
                    label = { Text(t("Другой"), maxLines = 1, softWrap = false) },
                )
            }
            AiGuideCard(AiGuide.idFor(prefs.aiProvider, prefs.aiUrl), open0 = !prefs.aiReady)
            if (prefs.aiProvider == "openai") {
                var url by remember(tick) { mutableStateOf(prefs.aiUrl) }
                OutlinedTextField(url, { url = it; prefs.aiUrl = it.trim() }, Modifier.fillMaxWidth(),
                    label = { Text(t("Адрес API")) }, singleLine = true)
            }
            var model by remember(tick) { mutableStateOf(prefs.aiModel) }
            OutlinedTextField(model, { model = it; prefs.aiModel = it.trim() }, Modifier.fillMaxWidth(),
                label = { Text(t("Модель")) }, singleLine = true)
            var key by remember { mutableStateOf(prefs.aiKey) }
            OutlinedTextField(key, { key = it; prefs.aiKey = it.trim() }, Modifier.fillMaxWidth(),
                label = { Text(if (prefs.aiProvider == "gigachat") t("Ключ авторизации") else t("API-ключ")) },
                singleLine = true, visualTransformation = PasswordVisualTransformation())
            Hint(if (prefs.aiReady) t("Готово: на блоке «Питание» появится кнопка «Посчитать калории по фото».") else t("Ключ не задан."))
        }
            "lock" -> {
            val deviceOk = act.canUseDeviceAuth()
            SwitchRow(t("Запрашивать вход при открытии"), prefs.lockEnabled) { on ->
                if (on && !prefs.hasPin && !(prefs.biometric && deviceOk)) {
                    pinDialog = true
                } else { prefs.lockEnabled = on; tick++ }
            }
            SwitchRow(
                t("Отпечаток / блокировка экрана телефона (PIN, графический ключ, пароль)") +
                    if (!deviceOk) t(" — не настроено на телефоне") else "",
                prefs.biometric && deviceOk,
            ) { on ->
                if (!on && prefs.lockEnabled && !prefs.hasPin) notify(t("Сначала задайте PIN-код приложения"))
                else if (on && !deviceOk) notify(t("Настройте блокировку экрана в настройках телефона"))
                else { prefs.biometric = on; tick++ }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (prefs.hasPin) t("PIN-код приложения задан") else t("PIN-код приложения не задан"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { pinDialog = true }) { Text(if (prefs.hasPin) t("Сменить") else t("Задать")) }
                if (prefs.hasPin) TextButton(onClick = {
                    if (prefs.lockEnabled && !(prefs.biometric && deviceOk)) notify(t("Без PIN-кода войти будет нечем"))
                    else { prefs.setPin(null); tick++ }
                }) { Text(t("Убрать")) }
            }
            SwitchRow(t("Скрывать содержимое в списке недавних приложений"), prefs.hideInRecents) {
                prefs.hideInRecents = it; act.applySecureFlag(); tick++
            }
            Hint(t("После сворачивания больше чем на 30 секунд вход запрашивается снова."))
        }
            "data" -> {
                SubTitle(t("Экспорт"))
            Button(onClick = { csvLauncher.launch("tabita_${LocalDate.now()}.csv") }, Modifier.fillMaxWidth()) {
                Text(t("Выгрузить таблицу (CSV для Excel)"))
            }
            Hint(t("Все дни и пункты: строка — день, столбец — пункт."))
        
                Spacer(Modifier.height(16.dp))
                SubTitle(t("Резервная копия"))
            Hint(
                t(
                    "Данные и фото хранятся только на этом телефоне. Время от времени сохраняйте копию " +
                        "(например, на Google Диск), чтобы не потерять их при смене телефона."
                )
            )
            Spacer(Modifier.height(6.dp))
            Button(onClick = { backupLauncher.launch("tabita_backup_${LocalDate.now()}.zip") }, Modifier.fillMaxWidth()) {
                Text(t("Сохранить копию"))
            }
            OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/json", "*/*")) }, Modifier.fillMaxWidth()) {
                Text(t("Восстановить из копии"))
            }
        
                Spacer(Modifier.height(16.dp))
                SubTitle(t("Удаление"))
                Hint(t("Стирает все записи, заметки, чекапы, баланс, удалённые пункты и фото. Чек-лист возвращается к исходному. Настройки приложения остаются."))
                OutlinedButton(
                    onClick = { wipeAsk = true }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Icon(Icons.Filled.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text(t("Удалить все данные")) }
            }
            "meal_tpl" -> MealTplsBody(act, notify)
            "ref" -> ReferenceBody()
            "ref_rhythm" -> ReferenceBody(1)
            "about" -> {
            OutlinedButton(onClick = { open = null; act.showIntro.value = true }, modifier = Modifier.fillMaxWidth()) { Text(t("Пройти знакомство заново")) }
                AboutBody()
                Spacer(Modifier.height(16.dp))
                SubTitle(t("Журнал работы (логи)"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Log.OFF to t("Выкл"), Log.SHORT to t("Краткий"), Log.FULL to t("Полный")).forEach { (lv, title) ->
                    FilterChip(selected = prefs.logLevel == lv, onClick = {
                        prefs.logLevel = lv; Log.level = lv; Log.i("App", "уровень журнала: $lv"); tick++
                    }, label = { Text(title) })
                }
            }
            Hint(
                t("Краткий — ошибки, вылеты и важные события; полный — ещё и все действия. Файлы: %s/tabita_ДАТА.log", Log.folderTitle)
            )
            if (prefs.logLevel != Log.OFF) TextButton(onClick = {
                val u = Log.shareUri()
                if (u == null) notify(t("Журнал пока пуст"))
                else runCatching {
                    act.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, u)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), t("Отправить журнал")))
                }.onFailure { Log.e("App", "share log", it); notify(t("Ошибка: %s", it.message)) }
            }) { Text(t("Отправить сегодняшний журнал")) }
        
            }
        }
    }

    val profile = collect(repo.profile)
    val hasHc = granted.any { it in health.permissions }
    val osOn = prefs.osPackage.isNotEmpty() && act.openScale.granted(prefs.osPackage)
    val groups = listOf(
        t("Персонализация") to listOf(
            SetRow("appearance", Icons.Filled.Palette, t("Оформление"),
                listOf(when (Look.mode) { "light" -> t("Светлая"); "dark" -> t("Тёмная"); else -> t("Как в системе") },
                    if (Look.accent == "dynamic") t("цвета обоев") else t(Look.accents.firstOrNull { it.first == Look.accent }?.second?.first ?: "")).filter { it.isNotBlank() }.joinToString(" · ")),
            SetRow("rhythm", Icons.Filled.Schedule, t("Ритм дня"),
                listOfNotNull(if (prefs.rhythmView == "chart") t("дорожками") else t("кругом"),
                    if (prefs.rhythmStrip) t("полоса над плитками") else null).joinToString(" · ")),
            SetRow("bells", Icons.Filled.NotificationsActive, t("Звук таймеров"),
                Bells.of(prefs.bell)?.let { t(it.name) } ?: t("Простой сигнал")),
            SetRow("profile", Icons.Filled.Person, t("Профиль: рост и возраст"), profileSummary(profile)),
            SetRow("lang", Icons.Filled.Language, t("Язык и валюта"),
                (when (prefs.lang) { "ru" -> "Русский"; "en" -> "English"; else -> t("Как в системе") }) + " · " + prefs.currency),
        ),
        t("Синхронизация") to listOf(
            SetRow("hc", Icons.Filled.Watch, t("Часы и весы (Health Connect)"),
                when {
                    sdk != HealthConnectClient.SDK_AVAILABLE -> t("недоступно")
                    hasHc -> t("подключено: %d из %d", granted.count { it in health.permissions }, health.permissions.size)
                    else -> t("не подключено")
                }, ok = hasHc),
            SetRow("train_hc", Icons.Filled.FavoriteBorder, t("Запись тренировок в Health Connect"), org.maittaa.tabita.train.TrainSummary.hc(act.train)),
            SetRow("os", Icons.Filled.MonitorWeight, t("Весы (openScale)"),
                if (osOn) t("подключено") else t("не подключено"), ok = osOn),
        ),
        t("Тренировки") to run {
            val tm = act.train
            listOf(
                SetRow("train_set", Icons.Filled.Tune, t("Настройки тренировок"), org.maittaa.tabita.train.TrainSummary.set(tm)),
                SetRow("train_eq", Icons.Filled.Backpack, t("Инвентарь дома"), org.maittaa.tabita.train.TrainSummary.eq(tm)),
                SetRow("train_gym", Icons.Filled.FitnessCenter, t("Зал"), org.maittaa.tabita.train.TrainSummary.gym(tm)),
                SetRow("train_use", Icons.Filled.MonitorHeart, t("Что из дневника учитывать"), org.maittaa.tabita.train.TrainSummary.use(tm)),
            )
        },
        t("Настройки") to listOf(
            SetRow("meal_tpl", Icons.Filled.BookmarkAdded, t("Шаблоны еды"),
                MealTpls.all(prefs).let { l -> if (l.isEmpty()) t("нет — сохраняются из приёма пищи") else l.take(3).joinToString(", ") { it.name } + if (l.size > 3) " +" + (l.size - 3) else "" }),
            SetRow("notif", Icons.Filled.Notifications, t("Уведомления"),
                listOfNotNull(if (prefs.reminderOn) t("напоминание в %s", hhmm(prefs.reminderTime)) else null,
                    if (prefs.bedOn) t("отбой") else null,
                    if (prefs.motivOn) t("мотивация %d/день", prefs.motivPerDay) else null).joinToString(" · ").ifEmpty { t("выключены") }),
            SetRow("ai", Icons.Filled.AutoAwesome, t("Калории по фото (ИИ)"),
                if (prefs.aiReady) aiTitle(prefs) + " · " + t("готово") else t("ключ не задан"), ok = prefs.aiReady),
            SetRow("lock", Icons.Filled.Lock, t("Защита входа"),
                if (prefs.lockEnabled) t("вход по %s", listOfNotNull(if (prefs.biometric && act.canUseDeviceAuth()) t("отпечатку") else null, if (prefs.hasPin) "PIN" else null).joinToString(t(" или "))) else t("выключена")),
        ),
        t("Резервное копирование") to listOf(
            SetRow("data", Icons.Filled.Save, t("Дневник: экспорт и копия"), t("CSV для Excel, резервная копия с фото и тренировками")),
            SetRow("train_data", Icons.Filled.FitnessCenter, t("Тренировки: копия и таблица"), org.maittaa.tabita.train.TrainSummary.data(act.train)),
        ),
        t("Информация") to listOf(
            SetRow("ref", Icons.AutoMirrored.Filled.MenuBook, t("Справочник"), t("продукты: калории, БЖУ, ГИ · суточные ритмы")),
            SetRow("about", Icons.Filled.Info, t("О приложении"), "Tabita " + BuildConfig.VERSION_NAME + " · " + t("журнал работы")),
        ),
    )

    val settingsScroll = rememberScrollState()
    androidx.compose.foundation.layout.Box(modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(settingsScroll).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        groups.forEach { (title, rows) ->
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp))
            androidx.compose.material3.Card(
                Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = cardBorder(),
            ) {
                rows.forEachIndexed { i, r ->
                    if (i > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(r) { open = r.id; Log.d("Settings", "раздел ${r.id}") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    VerticalScrollbar(settingsScroll, Modifier.align(Alignment.CenterEnd))
    }

    open?.takeIf { it.startsWith("train_") }?.let { id ->
        org.maittaa.tabita.train.LocalTrainActions.current?.let { a ->
            org.maittaa.tabita.train.TrainSettings(id, act.train, a) { open = null }
        }
    }
    open?.takeIf { !it.startsWith("train_") }?.let { id ->
        val row = groups.flatMap { it.second }.firstOrNull { it.id == id.removeSuffix("_rhythm") }
        androidx.compose.ui.window.Dialog(onDismissRequest = { open = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            androidx.compose.material3.Surface(Modifier.fillMaxWidth(0.95f).padding(vertical = 24.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
                Column(Modifier.imePadding()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 4.dp, top = 8.dp)) {
                        row?.let { Icon(it.icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)) }
                        Text(row?.title ?: "", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { open = null }) { Icon(Icons.Filled.Close, t("Закрыть")) }
                    }
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionBody(id)
                    }
                }
            }
        }
    }

    if (wipeAsk) {
        var sure by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { wipeAsk = false },
            icon = { Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(t("Удалить все данные?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Все записи за все дни, заметки, чекапы самочувствия, баланс и фото будут стёрты. Вернуть их можно только из резервной копии."))
                    Text(t("Если копии нет — сначала сохраните её кнопкой «Сохранить копию» выше."), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().clickable { sure = !sure }, verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(sure, { sure = it })
                        Text(t("Понимаю, данные нельзя будет вернуть"))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = sure, onClick = {
                    repo.wipeAll(); wipeAsk = false; open = null; notify(t("Все данные удалены"))
                }) { Text(t("Удалить всё"), color = if (sure) MaterialTheme.colorScheme.error else Color.Unspecified) }
            },
            dismissButton = { TextButton(onClick = { wipeAsk = false }) { Text(t("Отмена")) } },
        )
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text(t("Восстановить из копии?")) },
            text = { Text(t("Текущие данные и шаблон будут заменены данными из файла.")) },
            confirmButton = {
                TextButton(onClick = {
                    val ok = runCatching {
                        act.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    }.getOrNull()?.let { repo.importBackup(it) } ?: false
                    confirmImport = null
                    if (ok) {
                        // 5.32: перезапуск — чтобы тренировки и настройки из копии подхватились, а не перезаписались старыми из памяти
                        android.widget.Toast.makeText(act, t("Данные восстановлены — перезапускаю"), android.widget.Toast.LENGTH_SHORT).show()
                        org.maittaa.tabita.train.restartApp(act)
                    } else notify(t("Файл не распознан"))
                }) { Text(t("Заменить")) }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text(t("Отмена")) } },
        )
    }

    timeDialog?.let { (title, value, onSet) -> TimeDialog(title, value, { onSet(it); timeDialog = null }) { timeDialog = null } }

    if (pinDialog) {
        var first by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pinDialog = false }) { Text(t("Отмена")) } },
            text = {
                val f = first
                if (f == null) {
                    PinPad(t("Новый PIN-код (4–8 цифр)")) { first = it; true }
                } else {
                    PinPad(t("Повторите PIN-код")) { pin ->
                        if (pin == f) {
                            prefs.setPin(pin); prefs.lockEnabled = true; pinDialog = false; tick++
                            notify(t("PIN-код сохранён, вход защищён"))
                            true
                        } else { first = null; notify(t("PIN-коды не совпали")); false }
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(title: String, minutes: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    val st = rememberTimePickerState(minutes / 60, minutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(st) },
        confirmButton = { TextButton(onClick = { onSet(st.hour * 60 + st.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )
}

/** CSV с разделителем «;» и BOM — корректно открывается в русском Excel. */
fun buildCsv(repo: Repo): String {
    // 5.32: плитки без значений (советы, цель, тренировка) — не столбцы
    val tmpl = repo.template.value.filter { it.type != ItemType.TIPS && it.type != ItemType.GOAL && it.type != ItemType.TRAINING && it.type != ItemType.RHYTHM }
    val entries = repo.entries.value
    fun esc(s: String) = if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    val sb = StringBuilder("﻿")
    val header = listOf(t("Дата")) + tmpl.flatMap { item ->
        val n = item.displayName + if (item.unit.isNotEmpty()) ", ${item.displayUnit}" else ""
        when (item.type) {
            ItemType.MEALS -> listOf(n, "$n, ${t("ккал")}", "$n, ${t("белки")}, ${t("г")}", "$n, ${t("жиры")}, ${t("г")}", "$n, ${t("углеводы")}, ${t("г")}",
                "$n, ${t("ГН")}", "$n, ${t("гликемическая оценка")}")
            ItemType.WELLBEING -> listOf("$n, ${t("общий балл")}")
            ItemType.DURATION -> if (item.source == Source.HC_SLEEP) listOf(n, "$n, ${t("отбой")}", "$n, ${t("подъём")}", "$n, ${t("дневной сон")}") else listOf(n)
            ItemType.NUMBER -> if (item.source == Source.HC_EXERCISE) listOf(n, "$n, ${t("виды")}") else listOf(n)
            ItemType.EXPENSES -> listOf(n, "$n, ${t("расход")}", "$n, ${t("доход")}")
            ItemType.BODY -> bodyIds(repo, item.id).map { (_, m) -> m.name + bodyUnit(m.unit).let { if (it.isEmpty()) "" else ", $it" } }
            else -> listOf(n)
        }
    }
    sb.append(header.joinToString(";") { esc(it) }).append("\r\n")
    entries.keys.sorted().forEach { d ->
        val day = entries[d] ?: return@forEach
        val date = LocalDate.parse(d)
        val cells = tmpl.flatMap { item ->
            val v = day[item.id]
            when (item.type) {
                ItemType.MEALS -> {
                    val meals = parseMeals(v)
                    listOf(
                        meals.joinToString("; ") { m -> t(m.kind) + ": " + m.text.ifBlank { "—" } + (m.kcal?.let { " (${formatNumber(it)} ${t("ккал")})" } ?: "") },
                        mealKcal(meals)?.let { formatNumber(it) } ?: "",
                        mealMacros(meals)?.protein?.let { formatNumber(Math.round(it * 10) / 10.0) } ?: "",
                        mealMacros(meals)?.fat?.let { formatNumber(Math.round(it * 10) / 10.0) } ?: "",
                        mealMacros(meals)?.carbs?.let { formatNumber(Math.round(it * 10) / 10.0) } ?: "",
                        Glycemic.day(meals)?.let { formatNumber(Math.round(it.load).toDouble()) } ?: "",
                        Glycemic.day(meals)?.score?.toString() ?: "",
                    )
                }
                ItemType.EXPENSES -> {
                    val l = parsePurchases(v)
                    listOf(
                        l.joinToString("; ") { p -> "${p.name} ${formatNumber(p.qty)} ${t(p.unit)} × ${formatNumber(p.price)}" },
                        l.filter { !it.income }.sumOf { it.total }.takeIf { it > 0 }?.let { formatNumber(it) } ?: "",
                        l.filter { it.income }.sumOf { it.total }.takeIf { it > 0 }?.let { formatNumber(it) } ?: "",
                    )
                }
                ItemType.WELLBEING -> listOf(repo.checkups.value.filter { it.date == d }.maxByOrNull { it.at }?.overall?.let { Math.round(it).toString() } ?: "")
                ItemType.DURATION -> {
                    val main = v?.toIntOrNull()?.let { "%d:%02d".format(it / 60, it % 60) } ?: (v ?: "")
                    if (item.source != Source.HC_SLEEP) listOf(main) else {
                        val sd = SleepDetail.parse(day[SleepDetail.key(item.id)])
                        val z = java.time.ZoneId.systemDefault()
                        fun hm(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(z).toLocalTime().let { "%d:%02d".format(it.hour, it.minute) }
                        val nap = day[Sleep.napKey(item.id)]?.toDoubleOrNull()?.toInt()
                        listOf(main, sd?.let { hm(it.start) } ?: "", sd?.let { hm(it.end) } ?: "", nap?.let { "%d:%02d".format(it / 60, it % 60) } ?: "")
                    }
                }
                ItemType.NUMBER -> if (item.source == Source.HC_EXERCISE) listOf(
                    v?.let { if (I18n.isEn) it else it.replace('.', ',') } ?: "",
                    Workouts.sessions(day, item.id).joinToString(", ") { ss -> Workouts.kind(ss.kind).title + " " + ss.min + " " + t("мин") },
                ) else listOf(v?.let { if (I18n.isEn) it else it.replace('.', ',') } ?: "")
                ItemType.BODY -> {
                    val d = BodyDay.parse(v)
                    bodyIds(repo, item.id).map { (id, _) -> d?.metrics?.firstOrNull { it.id == id }?.let { formatNumber(Math.round(it.value * 10) / 10.0) } ?: "" }
                }
                else -> listOf(
                    if (v == null) "" else when (item.type) {
                        ItemType.CHECK -> if (v == "2") item.displayName + "+" else item.displayName
                        ItemType.DURATION -> v.toIntOrNull()?.let { "%d:%02d".format(it / 60, it % 60) } ?: v
                        ItemType.SCALE, ItemType.NUMBER, ItemType.WATER -> if (I18n.isEn) v else v.replace('.', ',')
                        ItemType.MULTI -> parseCounts(v).entries.joinToString(", ") { (k, n) -> if (n == 1) t(k) else "${t(k)} ×$n" }
                        else -> v
                    }
                )
            }
        }
        sb.append((listOf("%02d.%02d.%d".format(date.dayOfMonth, date.monthValue, date.year)) + cells).joinToString(";") { esc(it) })
        sb.append("\r\n")
    }
    return sb.toString()
}

/** Все показатели, когда-либо встречавшиеся в блоке «Весы» (для столбцов CSV). */
private fun bodyIds(repo: Repo, itemId: String): List<Pair<String, BodyMetric>> {
    val m = LinkedHashMap<String, BodyMetric>()
    repo.entries.value.toSortedMap().values.forEach { d -> BodyDay.parse(d[itemId])?.metrics?.forEach { m.putIfAbsent(it.id, it) } }
    return m.entries.map { it.key to it.value }
}

/** 5.68: выбор колокольчика окончания таймеров; касание — выбрать и прослушать. */
@Composable
internal fun BellsBody(act: MainActivity) {
    var cur by remember { mutableStateOf(act.prefs.bell) }
    DisposableEffect(Unit) { onDispose { Bells.stop() } }
    Column {
        Text(t("Звучит, когда заканчивается отдых между подходами, упражнение на время в силовой и каждый блок аэробной тренировки и йоги."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        val options = Bells.all.map { it.id to t(it.name) } + (Bells.SIMPLE to t("Простой сигнал"))
        options.forEach { (id, name) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                    cur = id; act.prefs.bell = id
                    if (id == Bells.SIMPLE) Bells.playSystem(act) else Bells.play(act, id)
                }.padding(vertical = 2.dp).testTag("bell_$id"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = cur == id, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.PlayArrow, t("Прослушать"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
            }
        }
        Hint(t("Громкость — как у будильника: колокольчик слышно, даже если звук медиа выключен. «Простой сигнал» — короткий писк и системный звук уведомления, как было раньше."))
    }
}
