package org.maittaa.tabita

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.EggAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Описание показателя весов: название RU/EN, иконка и «хорошее» направление изменения
 * (+1 — рост хорошо, −1 — снижение хорошо, 0 — нейтрально).
 */
data class BodyInfo(val ru: String, val en: String, val icon: ImageVector, val good: Int, val order: Int) {
    val title get() = if (I18n.isEn) en else ru
}

object BodyCatalog {
    private val map: Map<String, BodyInfo> = listOf(
        "weight" to BodyInfo("Вес", "Weight", Icons.Filled.MonitorWeight, 0, 0),
        "body_fat" to BodyInfo("Жир", "Body fat", Icons.Filled.Opacity, -1, 1),
        "water" to BodyInfo("Вода", "Water", Icons.Filled.WaterDrop, 1, 2),
        "muscle" to BodyInfo("Мышцы", "Muscle", Icons.Filled.FitnessCenter, 1, 3),
        "bmi" to BodyInfo("ИМТ", "BMI", Icons.Filled.Calculate, -1, 4),
        "lbm" to BodyInfo("Безжировая масса", "Lean body mass", Icons.Filled.AccessibilityNew, 1, 5),
        "bone" to BodyInfo("Кости", "Bone", Icons.Filled.Accessibility, 1, 6),
        "visceral_fat" to BodyInfo("Висцеральный жир", "Visceral fat", Icons.Filled.BubbleChart, -1, 7),
        "protein" to BodyInfo("Белок", "Protein", Icons.Filled.EggAlt, 1, 8),
        "bmr" to BodyInfo("Базовый обмен", "BMR", Icons.Filled.LocalFireDepartment, 0, 9),
        "tdee" to BodyInfo("Расход энергии", "TDEE", Icons.Filled.Bolt, 0, 10),
        "calories" to BodyInfo("Калории", "Calories", Icons.Filled.Restaurant, 0, 11),
        "heart_rate" to BodyInfo("Пульс", "Heart rate", Icons.Filled.Favorite, 0, 12),
        "waist" to BodyInfo("Талия", "Waist", Icons.Filled.Straighten, -1, 13),
        "hips" to BodyInfo("Бёдра", "Hips", Icons.Filled.Straighten, 0, 14),
        "chest" to BodyInfo("Грудь", "Chest", Icons.Filled.Straighten, 0, 15),
        "biceps" to BodyInfo("Бицепс", "Biceps", Icons.Filled.Straighten, 1, 16),
        "thigh" to BodyInfo("Бедро", "Thigh", Icons.Filled.Straighten, 0, 17),
        "neck" to BodyInfo("Шея", "Neck", Icons.Filled.Straighten, 0, 18),
        "whr" to BodyInfo("Талия/бёдра", "Waist-to-hip", Icons.Filled.Straighten, -1, 19),
        "whtr" to BodyInfo("Талия/рост", "Waist-to-height", Icons.Filled.Straighten, -1, 20),
        "caliper" to BodyInfo("Жир (калипер)", "Fat (caliper)", Icons.Filled.Opacity, -1, 21),
        "impedance" to BodyInfo("Импеданс", "Impedance", Icons.Filled.ElectricBolt, 0, 22),
        "impedance_low" to BodyInfo("Импеданс (низк.)", "Impedance (low)", Icons.Filled.ElectricBolt, 0, 23),
    ).toMap()

    /** Ключ без пространства имён: "builtin.body_fat" → "body_fat", "legacy:fat" → "body_fat". */
    fun key(id: String): String {
        val k = id.substringAfterLast(':').substringAfterLast('.')
        return when (k) { "fat" -> "body_fat"; else -> k }
    }

    fun info(id: String): BodyInfo? = map[key(id)]

    fun title(m: BodyMetric) = info(m.id)?.title ?: m.name
    fun icon(id: String): ImageVector = info(id)?.icon ?: Icons.Filled.Timeline
    fun good(id: String) = info(id)?.good ?: 0
    fun order(id: String) = info(id)?.order ?: 100
}
