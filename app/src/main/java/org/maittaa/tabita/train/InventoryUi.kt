package org.maittaa.tabita.train

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** 5.42: настройка весов инвентаря — в «Инвентарь дома» и «Зал». */

private fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
private fun kgs(v: Double) = LoadPlan.kg(v)

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** −  значение  + */
@Composable
internal fun KgStepper(label: String, value: Double, step: Double, min: Double, max: Double, onChange: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IconButton({ onChange(Inventory.r2((value - step).coerceAtLeast(min))) }, enabled = value > min + 1e-6) { Icon(Icons.Filled.Remove, "−") }
        Text(kgs(value), Modifier.widthIn(min = 56.dp), fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        IconButton({ onChange(Inventory.r2((value + step).coerceAtMost(max))) }, enabled = value < max - 1e-6) { Icon(Icons.Filled.Add, "+") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DumbbellSetup(m: AppModel) {
    val inv = m.inventory
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp, bottom = 8.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Какие гантели", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(!inv.dbAdjustable, { m.changeInventory(inv.copy(dbAdjustable = false)) }, label = { Text("Обычные") })
                FilterChip(inv.dbAdjustable, { m.changeInventory(inv.copy(dbAdjustable = true)) }, label = { Text("Разборные") })
            }
            var ask by remember { mutableStateOf<String?>(null) }   // "db" | "plate"
            if (!inv.dbAdjustable) {
                Hint("Вес одной гантели. Если пар несколько — добавь каждую.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    inv.dbFixed.sorted().forEach { w ->
                        InputChip(true, { m.changeInventory(inv.copy(dbFixed = inv.dbFixed - w)) }, label = { Text(kgs(w)) },
                            trailingIcon = { Icon(Icons.Filled.Close, "убрать", Modifier.size(16.dp)) })
                    }
                    AssistChip({ ask = "db" }, label = { Text("Добавить") }, leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
                }
            } else {
                KgStepper("Гриф одной гантели", inv.dbHandle, 0.5, 0.5, 10.0) { m.changeInventory(inv.copy(dbHandle = it)) }
                Text("Блины (всего, на обе гантели)", style = MaterialTheme.typography.labelLarge)
                inv.dbPlates.sortedByDescending { it.first }.forEach { (w, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(kgs(w), Modifier.weight(1f))
                        IconButton({ m.changeInventory(inv.copy(dbPlates = inv.dbPlates.map { if (it.first == w) w to (n - 1).coerceAtLeast(0) else it }.filter { it.second > 0 })) }) { Icon(Icons.Filled.Remove, "−") }
                        Text("$n шт.", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        IconButton({ m.changeInventory(inv.copy(dbPlates = inv.dbPlates.map { if (it.first == w) w to n + 1 else it })) }) { Icon(Icons.Filled.Add, "+") }
                    }
                }
                AssistChip({ ask = "plate" }, label = { Text("Добавить блин") }, leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
                Hint("Блины ставятся поровну: на каждую сторону каждой гантели — четверть.")
            }
            ask?.let { what ->
                KgDialog(if (what == "db") "Вес одной гантели" else "Вес блина", { ask = null }) { v ->
                    val r = Inventory.r2(v)
                    if (what == "db") m.changeInventory(inv.copy(dbFixed = (inv.dbFixed + r).distinct()))
                    else if (inv.dbPlates.none { it.first == r }) m.changeInventory(inv.copy(dbPlates = inv.dbPlates + (r to 4)))
                }
            }
            val opts = inv.dumbbells()
            Text(
                if (opts.isEmpty()) "Пока весов нет — рабочий вес подбираться не будет."
                else "Доступно: " + opts.joinToString(" · ") { kgs(it) },
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun BackpackSetup(m: AppModel) {
    val inv = m.inventory
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp, bottom = 8.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Рюкзак", style = MaterialTheme.typography.titleSmall)
            Hint("Шаг — сколько весит одна книга или бутылка (1,5 л воды ≈ 1,5 кг). Взвесить можно на весах: с рюкзаком минус без.")
            KgStepper("Шаг", inv.bpStep, 0.5, 0.5, 5.0) { m.changeInventory(inv.copy(bpStep = it)) }
            KgStepper("Максимум", inv.bpMax, 1.0, 1.0, 40.0) { m.changeInventory(inv.copy(bpMax = it)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GymSteps(m: AppModel) {
    val inv = m.inventory
    Text("Шаг весов в зале", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    Hint("По нему подбирается следующий рабочий вес.")
    @Composable
    fun row(title: String, cur: Double, opts: List<Double>, set: (Double) -> Unit) {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            opts.forEach { v -> FilterChip(cur == v, { set(v) }, label = { Text(kgs(v), maxLines = 1, softWrap = false) }) }
        }
    }
    row("Гантельный ряд", inv.gymDbStep, listOf(1.0, 2.0, 2.5)) { m.changeInventory(inv.copy(gymDbStep = it)) }
    row("Штанга (блины на обе стороны)", inv.gymBarStep, listOf(2.5, 5.0)) { m.changeInventory(inv.copy(gymBarStep = it)) }
    row("Тренажёры", inv.gymMachineStep, listOf(2.5, 5.0, 10.0)) { m.changeInventory(inv.copy(gymMachineStep = it)) }
}

/** Маленькое окно ввода веса (кг). */
@Composable
private fun KgDialog(title: String, onClose: () -> Unit, onOk: (Double) -> Unit) {
    var v by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            OutlinedTextField(v, { v = it }, singleLine = true, suffix = { Text("кг") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        },
        confirmButton = { TextButton({ num(v)?.takeIf { it in 0.1..100.0 }?.let { onOk(it) }; onClose() }, enabled = num(v)?.let { it in 0.1..100.0 } == true) { Text("Добавить") } },
        dismissButton = { TextButton(onClose) { Text("Отмена") } },
    )
}
