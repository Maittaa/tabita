package org.maittaa.tabita

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Все данные приложения хранятся в одном JSON-файле в памяти телефона. */
class Repo(context: Context) {
    /** Засчитывать ходьбу в тренировки — из настроек. */
    var countWalking: () -> Boolean = { Prefs(context).countWalking }
    private val file = File(context.filesDir, "data.json")
    private val appCtx = context.applicationContext
    /** Фото блюд хранятся отдельными файлами. */
    val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    private val _template = MutableStateFlow<List<TemplateItem>>(emptyList())
    val template: StateFlow<List<TemplateItem>> = _template

    /** дата "2026-11-12" -> (id пункта -> значение) */
    private val _entries = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val entries: StateFlow<Map<String, Map<String, String>>> = _entries

    /** месяц "2026-11" -> заметки/итог месяца */
    private val _monthNotes = MutableStateFlow<Map<String, String>>(emptyMap())
    val monthNotes: StateFlow<Map<String, String>> = _monthNotes

    /** Прохождения чекапа самочувствия. */
    private val _checkups = MutableStateFlow<List<Checkup>>(emptyList())
    val checkups: StateFlow<List<Checkup>> = _checkups

    fun addCheckup(c: Checkup) {
        _checkups.update { it + c }
        // отметка в дне: день прохождения виден в чек-листе
        _template.value.firstOrNull { it.type == ItemType.WELLBEING }?.let { setValue(c.date, it.id, "1") }
        Log.i("Repo", "чекап ${c.date}: ${c.results.keys}")
        scheduleSave()
    }

    fun deleteCheckup(at: Long) {
        _checkups.update { l -> l.filterNot { it.at == at } }
        scheduleSave()
    }

    /** Профиль: рост, год рождения, пол (5.19). Не стирается «Удалить все данные» — это настройка. */
    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = _profile

    fun setProfile(p: Profile) {
        _profile.value = p
        Log.i("Repo", "профиль: рост ${p.heightCm}, год ${p.birthYear}, пол ${p.sex}")
        scheduleSave()
    }

    /** Свои продукты справочника (5.22.2): по этикетке, с мерной ложкой или другой единицей. */
    private val _myFoods = MutableStateFlow(FoodDb.seed).also { FoodDb.mine = it.value }
    val myFoods: StateFlow<List<Food>> = _myFoods

    private val SEED_V1 = setOf("seed_f1", "seed_green")

    fun setMyFoods(l: List<Food>) {
        _myFoods.value = l; FoodDb.mine = l
        Log.i("Repo", "свои продукты: ${l.size}")
        scheduleSave()
    }

    /** Группы пунктов (см. [Group]). */
    private val _groups = MutableStateFlow(defaultGroups())
    val groups: StateFlow<List<Group>> = _groups

    /** Корзина: удалённые пункты. Их записи в днях не трогаются — пункт можно вернуть вместе с историей. */
    private val _trash = MutableStateFlow<List<TemplateItem>>(emptyList())
    val trash: StateFlow<List<TemplateItem>> = _trash

    /** Плитка экрана «День»: id пункта (или id группы отметок), размер в клетках (0 — по содержимому). */
    data class Tile(val id: String, val w: Int = 0, val h: Int = 0, val small: Boolean = false) {
        /** Ближайшее из именованных положений (для раскладки по умолчанию и подсказок). */
        val size: TileSize get() = when { !small -> TileSize.FULL; w >= 3 -> TileSize.WIDE; w == 2 -> TileSize.HALF; else -> TileSize.MINI }
        /** 5.47: уменьшенная плитка — любой размер 1…6 × 1…3 клетки; развёрнутая — по содержимому. */
        fun normalized(): Tile = if (!small) Tile(id) else Tile(id, w.coerceIn(1, MAX_W), h.coerceIn(1, MAX_H), true)
        fun snapped(): Tile = normalized()
        companion object { const val MAX_W = 6; const val MAX_H = 3 }
    }
    private val _layout = MutableStateFlow<List<Tile>>(emptyList())
    val layout: StateFlow<List<Tile>> = _layout

    /**
     * Ручные правки баланса по дням: на дату [BalanceAnchor.date] баланс был [BalanceAnchor.amount]
     * при операциях этого дня на сумму [BalanceAnchor.dayNet] (покупки, внесённые позже в тот же день, учитываются).
     * Остальные дни считаются от ближайшей правки: предыдущий день + доходы − расходы.
     */
    data class BalanceAnchor(val date: String, val amount: Double, val dayNet: Double)
    private val _balance = MutableStateFlow<Map<String, BalanceAnchor>>(emptyMap())
    val balance: StateFlow<Map<String, BalanceAnchor>> = _balance

    init {
        val text = runCatching { file.readText() }.getOrNull()
        if (text == null || !loadJson(text)) {
            if (text != null) Log.e("Repo", "data.json не распознан, длина ${text.length}")
            _template.value = defaultTemplate()
        }
        normalizeGroups()
    }

    /**
     * Удалить все данные: записи, заметки, чекапы, баланс, корзину, раскладку и фото.
     * Шаблон и группы — по умолчанию. Настройки приложения (тема, ключи, напоминания) не трогаются.
     */
    fun wipeAll() {
        _entries.value = emptyMap(); _monthNotes.value = emptyMap(); _checkups.value = emptyList()
        _balance.value = emptyMap(); _trash.value = emptyList(); _layout.value = emptyList()
        _groups.value = defaultGroups(); _template.value = defaultTemplate()
        normalizeGroups()
        photosDir.listFiles()?.forEach { it.delete() }
        Log.i("Repo", "все данные удалены")
        scheduleSave()
    }

    // ---------- значения ----------

    fun setValue(date: String, itemId: String, value: String?) {
        _entries.update { all ->
            val day = (all[date] ?: emptyMap()).toMutableMap()
            if (value.isNullOrEmpty()) day.remove(itemId) else day[itemId] = value
            val res = all.toMutableMap()
            if (day.isEmpty()) res.remove(date) else res[date] = day
            res
        }
        scheduleSave()
    }

    /** Пакетная запись (импорт с часов). Значения, исправленные вручную, не трогаются. */
    fun setValues(values: Map<String, Map<String, String>>) {
        if (values.isEmpty()) return
        _entries.update { all ->
            val res = all.toMutableMap()
            values.forEach { (date, vals) ->
                val day = res[date] ?: emptyMap()
                res[date] = recountWorkouts(day + vals.filterKeys { day[manualKey(it)] == null })
            }
            res
        }
        scheduleSave()
    }

    /** Минуты тренировок за день = засчитанные с часов + засчитанные вручную (если итог не исправлен вручную). */
    private fun recountWorkouts(day: Map<String, String>): Map<String, String> {
        var d = day
        _template.value.filter { it.source == Source.HC_EXERCISE }.forEach { item ->
            if (d[manualKey(item.id)] != null) return@forEach
            val synced = d[Workouts.syncedKey(item.id)]; val manual = d[Workouts.manualKey(item.id)]
            if (synced == null && manual == null) return@forEach
            val total = Workouts.counted(Workouts.merge(Workouts.parse(synced), Workouts.parse(manual)).first, countWalking())
            d = if (total > 0 || synced != null || manual != null) d + (item.id to total.toString()) else d - item.id
        }
        return d
    }

    /**
     * Ручные тренировки дня. Если день синхронизировался до 5.15 (минуты есть, списка с часов нет),
     * эти минуты сохраняются как основа, к ним прибавляются ручные.
     */
    fun setWorkouts(date: String, item: TemplateItem, manual: List<WorkoutSession>) {
        _entries.update { all ->
            val day = all[date] ?: emptyMap()
            val old = Workouts.parse(day[Workouts.manualKey(item.id)])
            var d = day - Workouts.manualKey(item.id)
            Workouts.toJson(manual)?.let { d = d + (Workouts.manualKey(item.id) to it) }
            if (day[Workouts.syncedKey(item.id)] == null && day[manualKey(item.id)] == null) {
                val base = ((parseNumber(day[item.id]) ?: 0.0).toInt() - Workouts.counted(old, countWalking())).coerceAtLeast(0)
                val total = base + Workouts.counted(manual, countWalking())
                d = if (total > 0) d + (item.id to total.toString()) else d - item.id
            } else d = recountWorkouts(d)
            all + (date to d)
        }
        Log.i("Repo", "тренировки вручную $date: ${manual.size}")
        scheduleSave()
    }

    /** Пересчитать минуты тренировок во всех днях (после смены «засчитывать ходьбу»). */
    fun recountAllWorkouts() {
        _entries.update { all -> all.mapValues { (_, d) -> recountWorkouts(d) } }
        scheduleSave()
    }

    /** Исправить значение с часов вручную (или снять пометку, manual=false). */
    fun setManual(date: String, itemId: String, value: String?, manual: Boolean) {
        setValue(date, manualKey(itemId), if (manual) "1" else null)
        if (manual) setValue(date, itemId, value)
    }

    // ---------- раскладка плиток ----------

    /** Группа пункта: сохранённая, если подходит по виду, иначе первая группа этого вида. */
    fun groupOf(item: TemplateItem, gs: List<Group> = _groups.value): Group =
        gs.firstOrNull { it.id == item.group && it.kind == item.type.kind } ?: gs.firstOrNull { it.kind == item.type.kind }
            ?: defaultGroups().first { it.kind == item.type.kind }

    /** Плитка, в которой показывается пункт: отметки — плитка группы, остальное — своя. */
    fun tileIdOf(item: TemplateItem, gs: List<Group> = _groups.value) = if (item.type == ItemType.CHECK) groupOf(item, gs).id else item.id

    /** Плитки в порядке показа: сохранённая раскладка + новые в конце; скрытые и удалённые — нет. */
    fun tiles(template: List<TemplateItem>, layout: List<Tile>, gs: List<Group> = _groups.value): List<Tile> {
        val ids = LinkedHashSet<String>()
        template.filter { !it.hidden }.forEach { ids += tileIdOf(it, gs) }
        val known = layout.filter { it.id in ids }.distinctBy { it.id }.map { it.normalized() }
        return known + ids.filter { id -> known.none { it.id == id } }.map { Tile(it) }
    }

    /** Порядок пунктов в шаблоне = порядок плиток на экране (отметки группы — подряд, в своём порядке). */
    private fun ordered(template: List<TemplateItem>, layout: List<Tile>): List<TemplateItem> {
        val idx = tiles(template, layout).mapIndexed { i, t -> t.id to i }.toMap()
        return template.sortedBy { idx[tileIdOf(it)] ?: Int.MAX_VALUE }
    }

    /**
     * Удобная раскладка «с нуля» (5.36): сверху цель, затем быстрые показатели парами (оценка, сон, вода, шаги),
     * совет дня, питание и отметки — во всю ширину (их заполняют чаще всего), дальше тренировка, мелкие показатели,
     * чекап и финансы. Порядок и размеры — по виду пункта, поэтому подходит любому шаблону.
     */
    fun ergonomicLayout(): List<Tile> {
        val gs = _groups.value
        fun rank(id: String): Pair<Int, TileSize> {
            if (gs.any { it.id == id }) return 50 to TileSize.FULL
            val it = _template.value.firstOrNull { t -> t.id == id } ?: return 90 to TileSize.WIDE
            return when {
                it.type == ItemType.GOAL -> 0 to TileSize.WIDE
                it.type == ItemType.RHYTHM -> 1 to TileSize.WIDE
                it.type == ItemType.SCALE -> 10 to TileSize.HALF
                it.source == Source.HC_SLEEP -> 11 to TileSize.HALF
                it.type == ItemType.WATER -> 12 to TileSize.HALF
                it.source == Source.HC_STEPS -> 13 to TileSize.HALF
                it.type == ItemType.TIPS -> 20 to TileSize.WIDE
                it.type == ItemType.MEALS -> 30 to TileSize.FULL
                it.type == ItemType.TRAINING -> 60 to TileSize.WIDE
                it.source == Source.HC_WEIGHT || it.type == ItemType.BODY -> 70 to TileSize.HALF
                it.type == ItemType.MULTI -> 71 to TileSize.HALF
                it.type == ItemType.NUMBER || it.type == ItemType.DURATION -> 72 to TileSize.MINI
                it.type == ItemType.WELLBEING -> 80 to TileSize.WIDE
                it.type == ItemType.TEXT -> 81 to TileSize.HALF
                it.type == ItemType.EXPENSES -> 82 to TileSize.WIDE
                else -> 85 to TileSize.WIDE
            }
        }
        val cur = tiles(_template.value, _layout.value, gs)
        return cur.map { t -> t.id to rank(t.id) }.sortedBy { it.second.first }.map { (id, r) -> r.second.tile(id) }
    }

    fun setLayout(l: List<Tile>) {
        _layout.value = l
        _template.update { ordered(it, l) }
        scheduleSave()
    }

    /** Каждому виду пунктов — хотя бы одна группа; у каждого пункта — подходящая группа. */
    private fun normalizeGroups() {
        _groups.update { gs ->
            val d = defaultGroups()
            gs + GroupKind.entries.filter { k -> gs.none { it.kind == k } }.map { k ->
                d.first { it.kind == k }.let { g -> if (gs.any { it.id == g.id }) g.copy(id = Group.newId()) else g }
            }
        }
        val gs = _groups.value
        _template.update { list -> ordered(list.map { it.copy(group = groupOf(it, gs).id) }, _layout.value) }
    }

    /** Группы в порядке первого пункта на экране; пустые — в конце. */
    fun groupsInOrder(template: List<TemplateItem> = _template.value): List<Pair<Group, List<TemplateItem>>> {
        val gs = _groups.value
        val byGroup = template.groupBy { groupOf(it, gs).id }
        val withItems = template.map { groupOf(it, gs).id }.distinct()
        return (withItems + gs.map { it.id }.filter { it !in withItems }).mapNotNull { id ->
            gs.firstOrNull { it.id == id }?.let { it to (byGroup[id] ?: emptyList()) }
        }
    }

    fun addGroup(name: String, kind: GroupKind): Group {
        val g = Group(Group.newId(), name.trim(), kind)
        _groups.update { it + g }
        Log.i("Repo", "новая группа ${g.name} (${kind.name})")
        scheduleSave()
        return g
    }

    /** 5.59: группа с заданным id (для готовых наборов); есть — не трогаем. */
    fun ensureGroup(id: String, name: String, kind: GroupKind): Group {
        _groups.value.firstOrNull { it.id == id }?.let { return it }
        val g = Group(id, name, kind)
        _groups.update { it + g }
        Log.i("Repo", "группа $name ($id)")
        scheduleSave()
        return g
    }

    fun renameGroup(id: String, name: String) {
        _groups.update { gs -> gs.map { if (it.id == id) it.copy(name = name.trim()) else it } }
        scheduleSave()
    }

    /** Удалить можно только пустую группу и не последнюю своего вида. */
    fun canDeleteGroup(id: String): Boolean {
        val g = _groups.value.firstOrNull { it.id == id } ?: return false
        return _template.value.none { groupOf(it).id == id } && _groups.value.count { it.kind == g.kind } > 1
    }

    fun deleteGroup(id: String) {
        if (!canDeleteGroup(id)) return
        _groups.update { gs -> gs.filterNot { it.id == id } }
        _layout.update { l -> l.filterNot { it.id == id } }
        scheduleSave()
    }

    /**
     * Сдвинуть группу целиком на одну позицию среди групп: плитки на экране выстраиваются по группам
     * (внутри группы порядок сохраняется). Пустые группы не двигаются.
     */
    fun moveGroup(id: String, delta: Int) {
        val order = groupsInOrder().filter { (_, items) -> items.any { !it.hidden } }.map { it.first.id }.toMutableList()
        val i = order.indexOf(id); val j = i + delta
        if (i < 0 || j !in order.indices) return
        order[i] = order[j].also { order[j] = order[i] }
        val gs = _groups.value
        fun groupOfTile(tl: Tile): String = gs.firstOrNull { it.id == tl.id }?.id
            ?: _template.value.firstOrNull { it.id == tl.id }?.let { groupOf(it, gs).id } ?: ""
        val l = tiles(_template.value, _layout.value).sortedBy { tl -> order.indexOf(groupOfTile(tl)).let { if (it < 0) Int.MAX_VALUE else it } }
        Log.i("Repo", "группа $id сдвинута на $delta")
        setLayout(l)
    }

    /** Сдвинуть плитку группы отметок на экране на одну позицию. */
    fun moveTile(tileId: String, delta: Int) {
        val l = tiles(_template.value, _layout.value).toMutableList()
        val i = l.indexOfFirst { it.id == tileId }; val j = i + delta
        if (i < 0 || j !in l.indices) return
        val t0 = l[i]; l[i] = l[j]; l[j] = t0
        setLayout(l)
    }

    // ---------- openScale ----------

    /** Добавляет блок «Весы» в шаблон (если его нет) и скрывает отдельный пункт веса с Health Connect. */
    /** 5.51: пункт «Тренировки» (куда пишутся занятия с таймера и вручную); если его удалили — вернуть. */
    fun exerciseItem(): TemplateItem {
        _template.value.firstOrNull { it.source == Source.HC_EXERCISE }?.let { return it }
        val item = TemplateItem(id = if (_template.value.any { it.id == "ex" }) "ex_" + System.currentTimeMillis() else "ex",
            name = "Тренировки", type = ItemType.NUMBER, unit = "мин", source = Source.HC_EXERCISE)
        _template.update { it + item }
        Log.i("Repo", "добавлен пункт тренировок для аэробной нагрузки")
        scheduleSave()
        return item
    }

    fun ensureBodyItem(): Boolean {
        if (_template.value.any { it.type == ItemType.BODY }) return false
        _template.update { list ->
            val l = list.map { if (it.id == "weight" && it.source == Source.HC_WEIGHT) it.copy(hidden = true) else it }
            val idx = l.indexOfFirst { it.id == "weight" }
            if (idx >= 0) l.toMutableList().also { it.add(idx + 1, bodyTemplateItem()) } else l + bodyTemplateItem()
        }
        scheduleSave()
        return true
    }

    fun setBodyDays(days: Map<String, BodyDay>, srcPackage: String? = null) {
        val ids = _template.value.filter { it.type == ItemType.BODY }.map { it.id }
        if (ids.isEmpty() || days.isEmpty()) return
        setValues(days.mapValues { (_, d) ->
            ids.associateWith { d.toJson() } + (if (srcPackage != null) ids.associate { srcKey(it) to srcPackage } else emptyMap())
        })
    }

    // ---------- баланс ----------

    fun dayNet(date: String): Double {
        val ids = _template.value.filter { it.type == ItemType.EXPENSES }.map { it.id }
        val day = _entries.value[date] ?: return 0.0
        return ids.sumOf { id -> parsePurchases(day[id]).sumOf { it.signed } }
    }

    /** Баланс на конец дня [date] (null — ни одной правки до этой даты нет). */
    fun balanceOn(date: String): Double? {
        val a = _balance.value.filterKeys { it <= date }.maxByOrNull { it.key }?.value ?: return null
        val net = _entries.value.keys.filter { it > a.date && it <= date }.sumOf { dayNet(it) }
        return a.amount - a.dayNet + dayNet(a.date) + net
    }

    /** Есть ли ручная правка баланса именно на этот день. */
    fun hasBalanceFix(date: String) = date in _balance.value

    /** Задать баланс на конец дня [date] (по умолчанию — сегодня); null — убрать правку этого дня. */
    fun setBalance(amount: Double?, date: String = java.time.LocalDate.now().key()) {
        _balance.update { m -> if (amount == null) m - date else m + (date to BalanceAnchor(date, amount, dayNet(date))) }
        Log.i("Money", "баланс на $date: $amount")
        scheduleSave()
    }

    fun setMonthNote(month: String, text: String) {
        _monthNotes.update { if (text.isEmpty()) it - month else it + (month to text) }
        scheduleSave()
    }

    // ---------- шаблон ----------

    fun upsertItem(item: TemplateItem) {
        // 5.34: переименовал вариант списка — переименовываем и в записанных днях, и в настройках итогов месяца,
        // иначе старое название остаётся в анализе («Что влияет на индекс дня»)
        _template.value.firstOrNull { it.id == item.id && it.type == ItemType.MULTI && item.type == ItemType.MULTI }?.let { old ->
            if (old.options.size == item.options.size) {
                val ren = old.options.zip(item.options).filter { (a, b) -> a != b && b !in old.options && a !in item.options }.toMap()
                if (ren.isNotEmpty()) renameOptions(item.id, ren)
            }
        }
        val fixed = item.copy(group = groupOf(item).id)
        _template.update { list ->
            val i = list.indexOfFirst { it.id == item.id }
            // сменил группу — встаёт в её конец
            val l = if (i >= 0 && groupOf(list[i]).id == fixed.group) list.toMutableList().also { it[i] = fixed }
            else list.filterNot { it.id == item.id } + fixed
            ordered(l, _layout.value)
        }
        scheduleSave()
    }

    /** Переименовать варианты списка [id] во всех днях и в настройках итогов месяца: старое → новое. */
    fun renameOptions(id: String, ren: Map<String, String>) {
        _entries.update { all ->
            all.mapValues { (_, day) ->
                val v = day[id] ?: return@mapValues day
                val c = parseCounts(v)
                if (c.keys.none { it in ren }) day else {
                    val n = LinkedHashMap<String, Int>()
                    c.forEach { (k, x) -> val nk = ren[k] ?: k; n[nk] = (n[nk] ?: 0) + x }
                    countsToString(n)?.let { day + (id to it) } ?: (day - id)
                }
            }
        }
        runCatching {
            val p = Prefs(appCtx); val cfg = p.monthCfg
            fun k(x: String) = ren.entries.fold(x) { acc, (a, b) -> if (acc == "$id:$a") "$id:$b" else acc }
            p.monthCfg = cfg.copy(
                index = cfg.index?.map(::k)?.toSet(), goals = cfg.goals.mapKeys { k(it.key) }, heat = cfg.heat?.map(::k)?.toSet(),
                daily = cfg.daily.map(::k).toSet(), weekly = cfg.weekly.mapKeys { k(it.key) }, negative = cfg.negative.map(::k).toSet(),
            )
        }
        Log.i("Repo", "переименованы варианты $id: $ren")
        scheduleSave()
    }

    /** Удалить пункт в корзину: из чек-листа и графиков пропадает, данные остаются. */
    fun deleteItem(id: String) {
        val item = _template.value.firstOrNull { it.id == id } ?: return
        _template.update { list -> list.filterNot { it.id == id } }
        _trash.update { l -> l.filterNot { it.id == id } + item }
        Log.i("Repo", "пункт ${item.name} ($id) в корзине")
        scheduleSave()
    }

    /** Вернуть пункт из корзины — с прежним id, поэтому вся история снова видна в днях и графиках. */
    fun restoreItem(id: String): Boolean {
        val item = _trash.value.firstOrNull { it.id == id } ?: return false
        _trash.update { l -> l.filterNot { it.id == id } }
        if (_template.value.any { it.id == id }) { scheduleSave(); return false }
        val g = _groups.value.firstOrNull { it.id == item.group && it.kind == item.type.kind }
        _template.update { it + item.copy(group = g?.id ?: "") }
        normalizeGroups()
        Log.i("Repo", "пункт ${item.name} ($id) возвращён")
        scheduleSave()
        return true
    }

    /** Удалить навсегда: пункт и все его записи. */
    fun purgeItem(id: String) {
        _trash.update { l -> l.filterNot { it.id == id } }
        _entries.update { all ->
            all.mapValues { (_, day) -> day.filterKeys { k -> k != id && !k.startsWith("$id!") && !k.startsWith("$id:") } }
                .filterValues { it.isNotEmpty() }
        }
        Log.i("Repo", "пункт $id удалён навсегда")
        scheduleSave()
    }

    /** Сколько дней с записями у пункта. */
    fun daysWithData(id: String): Int = _entries.value.count { (_, day) -> day[id] != null }

    /**
     * Сдвиг пункта внутри его группы.
     * Отметки меняются местами внутри плитки группы, остальные пункты — местами плиток на экране.
     */
    fun moveItem(id: String, delta: Int) {
        val list = _template.value
        val item = list.firstOrNull { it.id == id } ?: return
        if (item.hidden) return
        val g = groupOf(item).id
        val mates = list.filter { !it.hidden && groupOf(it).id == g }
        val k = mates.indexOfFirst { it.id == id }; val other = mates.getOrNull(k + delta) ?: return
        if (item.type == ItemType.CHECK) {
            _template.update { l ->
                val a = l.indexOfFirst { it.id == id }; val b = l.indexOfFirst { it.id == other.id }
                l.toMutableList().also { val t0 = it[a]; it[a] = it[b]; it[b] = t0 }
            }
            scheduleSave()
        } else {
            val tl = tiles(list, _layout.value).toMutableList()
            val a = tl.indexOfFirst { it.id == id }; val b = tl.indexOfFirst { it.id == other.id }
            if (a < 0 || b < 0) return
            val t0 = tl[a]; tl[a] = tl[b]; tl[b] = t0
            setLayout(tl)
        }
        Log.d("Repo", "пункт ${item.name}: сдвиг $delta")
    }

    fun addOption(itemId: String, option: String) {
        val item = _template.value.firstOrNull { it.id == itemId } ?: return
        if (option.isBlank() || option in item.options) return
        upsertItem(item.copy(options = item.options + option.trim()))
    }

    // ---------- сохранение / резервная копия ----------

    fun toJson(): String = JSONObject().apply {
        put("version", 8)
        put("checkups", JSONArray(_checkups.value.map { it.toJson() }))
        if (!_profile.value.empty) put("profile", _profile.value.toJson())
        put("myFoods", JSONArray(_myFoods.value.map { it.toJson() }))
        put("myFoodsSeed", FoodDb.SEED_VER)
        put("template", JSONArray(_template.value.map { it.toJson() }))
        put("entries", JSONObject().apply {
            _entries.value.forEach { (d, vals) -> put(d, JSONObject(vals as Map<*, *>)) }
        })
        put("monthNotes", JSONObject(_monthNotes.value as Map<*, *>))
        put("groups", JSONArray(_groups.value.map { it.toJson() }))
        put("trash", JSONArray(_trash.value.map { it.toJson() }))
        put("layout", JSONArray(_layout.value.map { JSONObject().put("id", it.id).put("w", it.w).put("h", it.h).put("small", it.small) }))
        put("balanceFix", JSONArray(_balance.value.values.map { JSONObject().put("date", it.date).put("amount", it.amount).put("dayNet", it.dayNet) }))
    }.toString()

    /** Загружает данные из JSON; возвращает false, если файл не распознан. */
    fun loadJson(text: String): Boolean = runCatching {
        val o = JSONObject(text)
        val t = o.getJSONArray("template")
        val tmpl = (0 until t.length()).map { TemplateItem.fromJson(t.getJSONObject(it)) }
        val e = o.optJSONObject("entries") ?: JSONObject()
        val entries = e.keys().asSequence().associateWith { d ->
            val day = e.getJSONObject(d)
            day.keys().asSequence().associateWith { day.getString(it) }
        }
        val mn = o.optJSONObject("monthNotes") ?: JSONObject()
        val notes = mn.keys().asSequence().associateWith { mn.getString(it) }
        val la = o.optJSONArray("layout")
        _layout.value = if (la == null) emptyList() else (0 until la.length()).map {
            val t = la.getJSONObject(it); Tile(t.getString("id"), t.optInt("w"), t.optInt("h"), t.optBoolean("small"))
        }
        val fixes = mutableMapOf<String, BalanceAnchor>()
        // старый формат: одна опорная точка
        o.optJSONObject("balance")?.let { fixes[it.getString("date")] = BalanceAnchor(it.getString("date"), it.getDouble("amount"), it.optDouble("dayNet", 0.0)) }
        o.optJSONArray("balanceFix")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { fixes[it.getString("date")] = BalanceAnchor(it.getString("date"), it.getDouble("amount"), it.optDouble("dayNet", 0.0)) }
        }
        _balance.value = fixes
        _groups.value = o.optJSONArray("groups")?.let { a -> (0 until a.length()).map { Group.fromJson(a.getJSONObject(it)) } } ?: defaultGroups()
        val ver = o.optInt("version", 1)
        _template.value = (if (ver < 2) migrateV2(tmpl) else tmpl).let { if (ver < 3) migrateV3(it) else it }
            .let { if (ver < 4 && it.none { i -> i.type == ItemType.WELLBEING }) it + wellbeingTemplateItem() else it }
            // 5.20: плитка «Советы дня» — в самый верх
            .let { if (ver < 5 && it.none { i -> i.type == ItemType.TIPS }) listOf(tipsTemplateItem()) + it else it }
        if (ver < 5 && _layout.value.isNotEmpty() && _layout.value.none { it.id == "tips" })
            _layout.value = listOf(TileSize.WIDE.tile("tips")) + _layout.value
        // 5.24: плитка «Тренировка» — сразу после советов
        if (ver < 6 && _template.value.none { it.type == ItemType.TRAINING }) {
            val t0 = _template.value
            val at = t0.indexOfFirst { it.type == ItemType.TIPS }.let { if (it < 0) 0 else it + 1 }
            _template.value = t0.take(at) + trainingTemplateItem() + t0.drop(at)
            if (_layout.value.isNotEmpty() && _layout.value.none { it.id == "training" }) {
                val l = _layout.value; val li = l.indexOfFirst { it.id == "tips" }.let { if (it < 0) 0 else it + 1 }
                _layout.value = l.take(li) + TileSize.WIDE.tile("training") + l.drop(li)
            }
        }
        // 5.31: плитка «Цель» — сразу после советов
        if (ver < 7 && _template.value.none { it.type == ItemType.GOAL }) {
            val t0 = _template.value
            val at = t0.indexOfFirst { it.type == ItemType.TIPS }.let { if (it < 0) 0 else it + 1 }
            _template.value = t0.take(at) + goalTemplateItem() + t0.drop(at)
            if (_layout.value.isNotEmpty() && _layout.value.none { it.id == "goal" }) {
                val l = _layout.value; val li = l.indexOfFirst { it.id == "tips" }.let { if (it < 0) 0 else it + 1 }
                _layout.value = l.take(li) + TileSize.WIDE.tile("goal") + l.drop(li)
            }
        }
        // 5.43: плитка «Ритм дня» — сразу после цели (раньше ритм жил внутри советов)
        if (ver < 8 && _template.value.none { it.type == ItemType.RHYTHM }) {
            val t0 = _template.value
            val at = t0.indexOfFirst { it.type == ItemType.GOAL }.let { if (it < 0) t0.indexOfFirst { i -> i.type == ItemType.TIPS } else it }.let { if (it < 0) 0 else it + 1 }
            _template.value = t0.take(at) + rhythmTemplateItem() + t0.drop(at)
            if (_layout.value.isNotEmpty() && _layout.value.none { it.id == "rhythm" }) {
                val l = _layout.value
                val li = l.indexOfFirst { it.id == "goal" }.let { if (it < 0) l.indexOfFirst { t -> t.id == "tips" } else it }.let { if (it < 0) 0 else it + 1 }
                _layout.value = l.take(li) + TileSize.WIDE.tile("rhythm") + l.drop(li)
            }
        }
        _trash.value = o.optJSONArray("trash")?.let { a -> (0 until a.length()).mapNotNull { runCatching { TemplateItem.fromJson(a.getJSONObject(it)) }.getOrNull() } } ?: emptyList()
        _checkups.value = o.optJSONArray("checkups")?.let { a -> (0 until a.length()).mapNotNull { runCatching { Checkup.fromJson(a.getJSONObject(it)) }.getOrNull() } } ?: emptyList()
        // в старых копиях профиля нет — тогда оставляем текущий
        o.optJSONObject("profile")?.let { _profile.value = Profile.fromJson(it) }
        o.optJSONArray("myFoods")?.let { a ->
            var l = (0 until a.length()).mapNotNull { runCatching { Food.fromJson(a.getJSONObject(it)) }.getOrNull() }
            // 5.22.3: новые начальные продукты — один раз, не трогая свои и удалённые раньше
            if (o.optInt("myFoodsSeed", 1) < FoodDb.SEED_VER) l = l + FoodDb.seed.filter { s -> l.none { it.id == s.id } && s.id !in SEED_V1 }
            // 5.27: клетчатка и сахара с этикеток — в уже добавленные начальные продукты
            l = l.map { f -> FoodDb.seed.firstOrNull { it.id == f.id }?.let { s -> f.copy(fib = f.fib ?: s.fib, sug = f.sug ?: s.sug, sat = f.sat ?: s.sat) } ?: f }
            _myFoods.value = l
            FoodDb.mine = l
        }
        _entries.value = entries
        _monthNotes.value = notes
        normalizeGroups()
        true
    }.getOrDefault(false)

    /** v1 → v2: добавляем блоки «Питание» и «Финансы», вес — с весов. */
    private fun migrateV2(t: List<TemplateItem>): List<TemplateItem> {
        var res = t.map {
            if (it.id == "weight" && it.source == Source.NONE && it.type == ItemType.NUMBER) it.copy(source = Source.HC_WEIGHT) else it
        }
        val defs = defaultTemplate()
        listOf("food", "money").forEach { id ->
            if (res.none { it.id == id }) {
                val item = defs.first { it.id == id }
                val idx = res.indexOfFirst { it.id == "why" }
                res = if (idx >= 0) res.toMutableList().also { it.add(idx, item) } else res + item
            }
        }
        return res
    }

    /** v2 → v3: блок «Вода» после списка «Что использовалось». */
    private fun migrateV3(t: List<TemplateItem>): List<TemplateItem> {
        if (t.any { it.type == ItemType.WATER }) return t
        val idx = t.indexOfFirst { it.id == "used" }
        return if (idx >= 0) t.toMutableList().also { it.add(idx + 1, waterTemplateItem()) } else t + waterTemplateItem()
    }

    /** Последний известный вес (кг) на дату или раньше — из весов, Health Connect или ручного ввода. */
    fun latestWeight(upTo: String): Double? {
        val tmpl = _template.value
        _entries.value.toSortedMap(compareByDescending { it }).forEach { (d, day) ->
            if (d > upTo) return@forEach
            tmpl.filter { it.type == ItemType.BODY }.forEach { b ->
                BodyDay.parse(day[b.id])?.metrics?.firstOrNull { BodyCatalog.key(it.id) == "weight" }?.let { return it.value }
            }
            tmpl.filter { it.type == ItemType.NUMBER && (it.source == Source.HC_WEIGHT || it.id == "weight") }.forEach { w ->
                parseNumber(day[w.id])?.let { return it }
            }
        }
        return null
    }

    /** Резервная копия: ZIP с data.json и фотографиями. */
    fun writeBackup(out: java.io.OutputStream) {
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("data.json"))
            zip.write(toJson().toByteArray())
            zip.closeEntry()
            // тренировки — программа, история, настройки
            runCatching { org.maittaa.tabita.train.Backup.build(appCtx) }.getOrNull()?.let { t ->
                zip.putNextEntry(java.util.zip.ZipEntry("train.json")); zip.write(t.toByteArray()); zip.closeEntry()
            }
            // 5.32: настройки приложения — цель и план, советы, режим дня, графики, напоминания, оформление, справочник
            zip.putNextEntry(java.util.zip.ZipEntry("settings.json")); zip.write(Prefs(appCtx).backupJson().toByteArray()); zip.closeEntry()
            photosDir.listFiles()?.forEach { f ->
                zip.putNextEntry(java.util.zip.ZipEntry("photos/" + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Восстановление из ZIP (или старого JSON-файла). */
    fun importBackup(bytes: ByteArray): Boolean {
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) {
            val ok = loadJson(bytes.toString(Charsets.UTF_8))
            if (ok) saveNow()
            return ok
        }
        var json: String? = null
        var train: String? = null
        var settings: String? = null
        val photos = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                when {
                    name == "data.json" -> json = zip.readBytes().toString(Charsets.UTF_8)
                    name == "train.json" -> train = zip.readBytes().toString(Charsets.UTF_8)
                    name == "settings.json" -> settings = zip.readBytes().toString(Charsets.UTF_8)
                    name.startsWith("photos/") && !name.contains("..") && !e.isDirectory ->
                        photos[name.removePrefix("photos/")] = zip.readBytes()
                }
            }
        }
        val ok = json?.let { loadJson(it) } ?: false
        if (ok) {
            photos.forEach { (n, b) -> File(photosDir, File(n).name).writeBytes(b) }
            train?.let { t -> runCatching { org.maittaa.tabita.train.Backup.restore(appCtx, t) }.onFailure { Log.e("Repo", "тренировки из копии", it) } }
            settings?.let { st -> runCatching { Prefs(appCtx).restoreJson(st) }.onFailure { Log.e("Repo", "настройки из копии", it) } }
            saveNow()
        }
        return ok
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            writeFile()
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        scope.launch { writeFile() }
    }

    @Synchronized
    private fun writeFile() {
        runCatching {
            val tmp = File(file.parentFile, "data.json.tmp")
            tmp.writeText(toJson())
            tmp.renameTo(file)
            Log.d("Repo", "сохранено: ${file.length()} байт, дней ${_entries.value.size}")
        }.onFailure { Log.e("Repo", "ошибка сохранения", it) }
    }
}

/** Четыре положения плитки на экране «День». */
enum class TileSize(val w: Int, val h: Int, val small: Boolean, val ru: String, val en: String) {
    /** Развёрнута: всё содержимое пункта во всю ширину. */
    FULL(0, 0, false, "Полная", "Full"),
    /** Во всю ширину, две строки: значение, подпись и график за 7 дней. */
    WIDE(4, 2, true, "Широкая", "Wide"),
    /** Половина ширины: название, значение, подпись. */
    HALF(2, 2, true, "Половина", "Half"),
    /** Четверть ширины: иконка и значение. */
    MINI(1, 2, true, "Мини", "Mini");

    val title: String get() = if (I18n.isEn) en else ru
    fun tile(id: String) = Repo.Tile(id, w, h, small)
}
