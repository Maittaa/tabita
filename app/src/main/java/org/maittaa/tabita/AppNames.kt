package org.maittaa.tabita

import android.content.Context

/** Человеческие названия приложений-источников данных («Google Health», «openScale», «Mi Fitness»…). */
object AppNames {
    private var ctx: Context? = null
    private val cache = HashMap<String, String>()

    /** Если система не отдаёт название (нет видимости пакета) — берём из этого списка. */
    private val known = mapOf(
        "com.fitbit.FitbitMobile" to "Fitbit",
        "com.google.android.apps.fitness" to "Google Fit",
        "com.google.android.apps.healthdata" to "Health Connect",
        "com.google.android.apps.wearables.maestro.companion" to "Pixel Watch",
        "com.xiaomi.wearable" to "Mi Fitness",
        "com.mi.health" to "Mi Fitness",
        "com.huami.watch.hmwatchmanager" to "Zepp Life",
        "com.huami.midong" to "Zepp",
        "com.sec.android.app.shealth" to "Samsung Health",
        "com.garmin.android.apps.connectmobile" to "Garmin Connect",
        "com.withings.wiscale2" to "Withings",
        "com.ouraring.oura" to "Oura",
        "com.health.openscale" to "openScale", "com.health.openscale.oss" to "openScale",
        "com.health.openscale.pro" to "openScale", "com.health.openscale.beta" to "openScale",
        "com.health.openscale.light" to "openScale", "com.health.openscale.debug" to "openScale",
    )
    val queryPackages get() = known.keys

    fun init(c: Context) { ctx = c.applicationContext }

    /** null — название не удалось определить (например, системный идентификатор-хэш вместо пакета). */
    fun label(pkg: String): String? = cache.getOrPut(pkg) {
        val pm = ctx?.packageManager
        val name = runCatching { pm!!.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != pkg }
            ?: known[pkg]
            ?: known.entries.firstOrNull { pkg.startsWith(it.key) }?.value
        if (name == null) Log.i("Source", "неизвестный источник данных: «$pkg»")
        name ?: ""
    }.ifEmpty { null }

    /** «из Google Health» / «из Fitbit + Mi Fitness»; null — источник неизвестен. */
    fun from(srcValue: String?): String? {
        val names = srcValue?.split(',')?.filter { it.isNotBlank() }?.mapNotNull { label(it) }?.distinct() ?: return null
        return if (names.isEmpty()) null else t("из %s", names.joinToString(" + "))
    }
}

/** Подпись под значением, пришедшим из другого приложения. */
fun sourceCaption(item: TemplateItem, day: Map<String, String>, hasValue: Boolean): String = when {
    day[manualKey(item.id)] != null -> t("исправлено вручную")
    !hasValue -> when (item.source) {
        Source.OPENSCALE -> t("нет данных из openScale")
        Source.HC_WEIGHT -> t("нет данных с весов")
        else -> t("нет данных из Health Connect")
    }
    else -> AppNames.from(day[srcKey(item.id)]) ?: when (item.source) {
        Source.OPENSCALE -> t("из %s", "openScale")
        else -> t("из %s", "Health Connect")
    }
}
