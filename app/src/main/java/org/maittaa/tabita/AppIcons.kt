package org.maittaa.tabita

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Значки приложения: переключаются включением одного из activity-alias. */
object AppIcons {
    /** Значки на выбор: ключ (= имя алиаса Icon<Ключ>) → (название, ресурс). «Мозг» — основной (5.18.2), раньше — «Мопс». */
    val all = listOf(
        "brain" to ("Мозг" to R.mipmap.ic_brain),
        "s00" to ("Мозг-штангист" to R.mipmap.ic_s00),
        "s01" to ("Без сил" to R.mipmap.ic_s01),
        "s02" to ("Кот-качок" to R.mipmap.ic_s02),
        "s03" to ("Гиря" to R.mipmap.ic_s03),
        "s04" to ("Шейкер" to R.mipmap.ic_s04),
        "s05" to ("Скелет" to R.mipmap.ic_s05),
        "s10" to ("Огонёк" to R.mipmap.ic_s10),
        "s11" to ("Гантель" to R.mipmap.ic_s11),
        "s12" to ("Авокадо" to R.mipmap.ic_s12),
        "s13" to ("Растёкся" to R.mipmap.ic_s13),
        "s14" to ("Бицепс" to R.mipmap.ic_s14),
        "s15" to ("Дорожка" to R.mipmap.ic_s15),
        "s20" to ("Пингвин" to R.mipmap.ic_s20),
        "s22" to ("Сердце" to R.mipmap.ic_s22),
        "s23" to ("Корги" to R.mipmap.ic_s23),
        "s24" to ("Звезда" to R.mipmap.ic_s24),
        "s25" to ("Картошка" to R.mipmap.ic_s25),
        "s30" to ("Кот-силач" to R.mipmap.ic_s30),
        "s31" to ("Бутылка" to R.mipmap.ic_s31),
        "s32" to ("Брокколи" to R.mipmap.ic_s32),
        "s33" to ("Скакалка" to R.mipmap.ic_s33),
        "s34" to ("Кроссовок" to R.mipmap.ic_s34),
        "s35" to ("Чертёнок" to R.mipmap.ic_s35),
        "s40" to ("Банан" to R.mipmap.ic_s40),
        "s41" to ("Скамья" to R.mipmap.ic_s41),
        "s42" to ("Слёзы" to R.mipmap.ic_s42),
        "s43" to ("Утка-качок" to R.mipmap.ic_s43),
        "s44" to ("Тяжёлая" to R.mipmap.ic_s44),
        "s45" to ("Мяч" to R.mipmap.ic_s45),
    )

    /** Алиасы прошлых версий → на какой значок их переключить. */
    private val legacy = mapOf("man" to "s23", "chisel" to "s23", "peak" to "s04", "egg" to "s23", "diary" to "s23", "woman" to "brain", "zen" to "s04", "skull" to "s05", "s21" to "s11")
    private val aliases get() = all.map { it.first } + legacy.keys

    private fun alias(ctx: Context, key: String) = ComponentName(ctx, "org.maittaa.tabita.Icon" + key.replaceFirstChar { it.uppercase() })

    fun current(ctx: Context): String = aliases.firstOrNull { k ->
        when (ctx.packageManager.getComponentEnabledSetting(alias(ctx, k))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> k == "brain"
            else -> false
        }
    } ?: "brain"

    fun set(ctx: Context, key: String) {
        val pm = ctx.packageManager
        // сначала включаем новый, потом выключаем остальные — чтобы значок не пропадал совсем
        pm.setComponentEnabledSetting(alias(ctx, key), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        aliases.filter { it != key }.forEach {
            pm.setComponentEnabledSetting(alias(ctx, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        Log.i("Icon", "значок: $key")
    }

    /** При запуске: если включён значок прошлой версии — переключить на соответствующий новый. */
    fun normalize(ctx: Context) = runCatching {
        val cur = current(ctx)
        legacy[cur]?.let { set(ctx, it) }
    }
}
