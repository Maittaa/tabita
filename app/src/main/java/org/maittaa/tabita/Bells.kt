package org.maittaa.tabita

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper

/**
 * Звук окончания таймеров (отдых между подходами, упражнение на время, блоки аэробной и йоги).
 * 5.69: вместо своих синтезированных колокольчиков (5.68) — любой звук Android из системного окна
 * выбора (уведомления и будильники). Prefs.bell: SIMPLE («Простой сигнал», как до 5.68) или URI звука.
 * Играет как будильник (USAGE_ALARM), не дольше [MAX_MS] — длинные мелодии обрываются.
 */
object Bells {
    const val SIMPLE = "beep"
    const val MAX_MS = 6_000L

    /** Простой сигнал: явно выбран или осталось значение от 5.68 (bell_*) / пусто. */
    fun isSimple(v: String): Boolean = v == SIMPLE || !v.contains("://")

    fun uri(v: String): Uri? = if (isSimple(v)) null else runCatching { Uri.parse(v) }.getOrNull()

    /** Название выбранного звука для подписи в настройках. */
    fun title(ctx: Context, v: String): String {
        val u = uri(v) ?: return t("Простой сигнал")
        return runCatching { RingtoneManager.getRingtone(ctx, u)?.getTitle(ctx) }.getOrNull()?.takeIf { it.isNotBlank() } ?: t("Звук системы")
    }

    /** Окно выбора системного звука: уведомления и будильники, без «Без звука». */
    fun pickerIntent(ctx: Context, current: String): Intent =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, t("Звук таймеров"))
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri(current))

    /** Результат окна выбора → значение для Prefs.bell (null — ничего не выбрано). */
    @Suppress("DEPRECATION")
    fun fromPicker(data: Intent?): String? =
        data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.toString()

    private var playing: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())
    private val cut = Runnable { stop() }

    /** Сыграть выбранный звук; для простого сигнала — системный звук уведомления. */
    fun play(ctx: Context, v: String = Prefs(ctx).bell) {
        runCatching {
            stop()
            val u = uri(v) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(ctx.applicationContext, u) ?: return
            r.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            playing = r
            r.play()
            handler.postDelayed(cut, MAX_MS)
        }.onFailure { Log.e("Bells", "не удалось сыграть $v", it) }
    }

    fun stop() {
        handler.removeCallbacks(cut)
        playing?.let { runCatching { it.stop() } }
        playing = null
    }
}
