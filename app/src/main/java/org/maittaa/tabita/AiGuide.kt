package org.maittaa.tabita

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 5.40: инструкции по подключению каждого ИИ-провайдера для «Калорий по фото»
 * и пометка, нужен ли VPN при расчёте из России.
 */
object AiGuide {
    enum class Vpn { NO, MAYBE, YES }

    data class Guide(
        val id: String,
        val name: String,
        val vpn: Vpn,
        val vpnNote: String,
        val steps: List<String>,
        val url: String?,
        val urlLabel: String,
    )

    const val OPENROUTER = "openrouter"
    const val OPENAI = "openai"
    const val CLAUDE = "claude"
    const val GIGACHAT = "gigachat"
    const val OTHER = "other"

    /** Какой инструкции соответствуют текущие настройки провайдера. */
    fun idFor(provider: String, url: String): String = when {
        provider == "gigachat" -> GIGACHAT
        provider == "anthropic" -> CLAUDE
        url.trimEnd('/') == "https://openrouter.ai/api/v1" -> OPENROUTER
        url.trimEnd('/') == "https://api.openai.com/v1" -> OPENAI
        else -> OTHER
    }

    fun guide(id: String): Guide = when (id) {
        OPENROUTER -> Guide(
            OPENROUTER, "OpenRouter", Vpn.MAYBE,
            t("Сам OpenRouter открывается из России, но часть моделей (Google, OpenAI) может отклонять запросы с российского IP. Если пришла ошибка про регион — включите VPN и повторите расчёт."),
            listOf(
                t("Зарегистрируйтесь на openrouter.ai (Google, GitHub или почта)."),
                t("Пополните баланс в разделе Credits — без него платные модели не отвечают."),
                t("Откройте Settings → Keys и нажмите «Create Key»."),
                t("Скопируйте ключ (начинается с sk-or-v1-) и вставьте в поле «API-ключ» ниже."),
                t("Модель можно сменить на любую с распознаванием фото, например google/gemini-2.5-flash."),
            ),
            "https://openrouter.ai/settings/keys", t("Открыть страницу ключей"),
        )
        OPENAI -> Guide(
            OPENAI, "OpenAI", Vpn.YES,
            t("API OpenAI недоступен из России: и сайт, и расчёт работают только через VPN. Для оплаты нужна зарубежная банковская карта."),
            listOf(
                t("Включите VPN и войдите на platform.openai.com."),
                t("В разделе Billing добавьте карту и купите кредиты (минимум $5)."),
                t("Откройте API keys и нажмите «Create new secret key»."),
                t("Скопируйте ключ (начинается с sk-) — он показывается один раз."),
                t("Вставьте ключ ниже. Во время расчёта VPN должен быть включён."),
            ),
            "https://platform.openai.com/api-keys", t("Открыть страницу ключей"),
        )
        CLAUDE -> Guide(
            CLAUDE, "Claude", Vpn.YES,
            t("Claude API (Anthropic) недоступен из России: регистрация и расчёт — только через VPN, оплата — зарубежной картой."),
            listOf(
                t("Включите VPN и войдите в Claude Console: platform.claude.com."),
                t("В Settings → Billing пополните баланс (предоплата картой)."),
                t("Откройте Settings → API keys и нажмите «Create Key»."),
                t("Скопируйте ключ (начинается с sk-ant-) — он показывается один раз."),
                t("Вставьте ключ ниже. Во время расчёта VPN должен быть включён."),
            ),
            "https://platform.claude.com/settings/keys", t("Открыть страницу ключей"),
        )
        GIGACHAT -> Guide(
            GIGACHAT, t("GigaChat (Сбер)"), Vpn.NO,
            t("Работает из России без VPN. У физлиц есть бесплатный пакет токенов. VPN лучше выключить — запросы из-за рубежа могут не пройти."),
            listOf(
                t("Войдите на developers.sber.ru по Сбер ID и откройте Studio."),
                t("Создайте проект GigaChat API (для физлиц — GIGACHAT_API_PERS)."),
                t("В проекте откройте «Настройки API» и нажмите «Получить ключ»."),
                t("Скопируйте «Ключ авторизации» и вставьте в поле ниже."),
                t("Модели с распознаванием фото: GigaChat-2-Pro, GigaChat-2-Max."),
            ),
            "https://developers.sber.ru/studio", t("Открыть Studio"),
        )
        else -> Guide(
            OTHER, t("Другой сервис"), Vpn.MAYBE,
            t("Зависит от сервиса: если он не работает в России, расчёт пройдёт только с включённым VPN."),
            listOf(
                t("Подойдёт любой сервис с OpenAI-совместимым API (/chat/completions) и моделью, понимающей фото."),
                t("В «Адрес API» укажите базовый адрес, обычно заканчивается на /v1."),
                t("В «Модель» — точное имя модели из документации сервиса."),
                t("В «API-ключ» — ключ из личного кабинета сервиса."),
            ),
            null, "",
        )
    }

    fun vpnLabel(v: Vpn) = when (v) {
        Vpn.NO -> t("Без VPN")
        Vpn.MAYBE -> t("VPN может понадобиться")
        Vpn.YES -> t("Нужен VPN")
    }

    /** Признаки того, что сервис отказал по региону — тогда подсказываем про VPN. */
    fun looksRegionBlocked(msg: String?): Boolean {
        val m = msg?.lowercase() ?: return false
        return listOf("unsupported_country", "country", "region", "territory", "not available in your", "location is not supported")
            .any { it in m } || m.startsWith("http 403") || m.startsWith("http 451")
    }

    fun errorText(msg: String?): String =
        if (looksRegionBlocked(msg)) t("Не удалось оценить: %s", msg) + "\n" + t("Похоже, сервис недоступен в вашем регионе — включите VPN и повторите.")
        else t("Не удалось оценить: %s", msg)
}

@Composable
private fun vpnColors(v: AiGuide.Vpn): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (v) {
        AiGuide.Vpn.NO -> cs.secondaryContainer to cs.onSecondaryContainer
        AiGuide.Vpn.MAYBE -> cs.tertiaryContainer to cs.onTertiaryContainer
        AiGuide.Vpn.YES -> cs.errorContainer to cs.onErrorContainer
    }
}

@Composable
fun VpnBadge(v: AiGuide.Vpn) {
    val (bg, fg) = vpnColors(v)
    Surface(color = bg, contentColor = fg, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (v == AiGuide.Vpn.NO) Icons.Filled.CheckCircle else Icons.Filled.VpnLock, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(AiGuide.vpnLabel(v), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

/** Сворачиваемая инструкция для выбранного сервиса; раскрыта, пока ИИ не настроен. */
@Composable
fun AiGuideCard(id: String, open0: Boolean) {
    val g = AiGuide.guide(id)
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    key(id) {
        Fold(t("Как подключить: %s", g.name), AiGuide.vpnLabel(g.vpn) + " · " + t("шагов: %d", g.steps.size), open0 = open0) {
            VpnBadge(g.vpn)
            Spacer(Modifier.height(6.dp))
            Text(g.vpnNote, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            GuideSteps(g.steps)
            if (g.url != null) {
                Spacer(Modifier.height(8.dp))
                LinkButton(g.urlLabel, g.url, ctx)
            }
        }
    }
}

/** Нумерованные шаги инструкции (ИИ, openScale). */
@Composable
fun GuideSteps(steps: List<String>) {
    val cs = MaterialTheme.colorScheme
    steps.forEachIndexed { i, s ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
            Surface(color = cs.primary, contentColor = cs.onPrimary, shape = CircleShape, modifier = Modifier.size(22.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(s, Modifier.weight(1f).padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun openUrl(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun LinkButton(label: String, url: String, ctx: android.content.Context, modifier: Modifier = Modifier.fillMaxWidth()) {
    FilledTonalButton(onClick = { openUrl(ctx, url) }, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, softWrap = false)
    }
}
