package org.maittaa.tabita

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Значки приложения: переключаются включением одного из activity-alias. */
object AppIcons {
    /** Значки на выбор: ключ (= имя алиаса Icon<Ключ>) → (название, ресурс). Мопс — основной. */
    val all = listOf(
        "diary" to ("Мопс" to R.mipmap.ic_dog),
        "woman" to ("Девушка" to R.mipmap.ic_girl),
        "zen" to ("Протеин" to R.mipmap.ic_protein),
        "egg" to ("Яйцо" to R.mipmap.ic_egg),
        "skull" to ("Скелет" to R.mipmap.ic_skull),
    )

    /** Алиасы прошлых версий → на какой значок их переключить. */
    private val legacy = mapOf("man" to "diary", "chisel" to "diary", "peak" to "zen")
    private val aliases get() = all.map { it.first } + legacy.keys

    private fun alias(ctx: Context, key: String) = ComponentName(ctx, "org.maittaa.tabita.Icon" + key.replaceFirstChar { it.uppercase() })

    fun current(ctx: Context): String = aliases.firstOrNull { k ->
        when (ctx.packageManager.getComponentEnabledSetting(alias(ctx, k))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> k == "diary"
            else -> false
        }
    } ?: "diary"

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
