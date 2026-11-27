package org.maittaa.tabita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 5.41: инструкция «Как подключить весы через openScale».
 * Факты (ноябрь 2026): openScale 3.x — F-Droid com.health.openscale, Google Play — открытая бета
 * com.health.openscale.oss; нужен Android 12+; весы ищутся в «Настройки → Bluetooth»;
 * если весы не находятся — отвязать их в приложении производителя и в Bluetooth телефона.
 */
object OsGuide {
    const val FDROID = "https://f-droid.org/packages/com.health.openscale/"
    const val PLAY = "https://play.google.com/store/apps/details?id=com.health.openscale.oss"

    fun steps(): List<String> = listOf(
        t("Установите openScale из F-Droid или Google Play (нужен Android 12 или новее)."),
        t("В openScale заполните своего пользователя: рост, дату рождения и пол — по ним считаются жир, вода и мышцы."),
        t("Откройте в openScale «Настройки → Bluetooth» и запустите поиск. Встаньте на весы, чтобы они проснулись, и выберите их в списке."),
        t("Взвесьтесь — измерение должно появиться в openScale."),
        t("Вернитесь сюда, нажмите «Подключить openScale» и разрешите Tabita читать данные."),
    )

    fun trouble(): String =
        t("Весы не находятся? Отвяжите их в приложении производителя (Xiaomi Home, Zepp Life) и в настройках Bluetooth телефона, проверьте батарейки. Доступ к местоположению openScale просит только для поиска Bluetooth.")
}

/** Сворачиваемая инструкция; раскрыта, пока openScale не подключён. */
@Composable
fun OpenScaleGuideCard(open0: Boolean) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val steps = OsGuide.steps()
    Fold(t("Как подключить весы"), t("через openScale · шагов: %d", steps.size), open0 = open0) {
        GuideSteps(steps)
        Spacer(Modifier.height(6.dp))
        Text(OsGuide.trouble(), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkButton("F-Droid", OsGuide.FDROID, ctx, Modifier.weight(1f))
            LinkButton("Google Play", OsGuide.PLAY, ctx, Modifier.weight(1f))
        }
        Text(t("Tabita подтягивает новые взвешивания при каждом открытии и берёт утреннее за день."),
            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}
