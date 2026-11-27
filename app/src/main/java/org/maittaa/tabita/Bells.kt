package org.maittaa.tabita

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/**
 * 5.68: колокольчики окончания таймеров (отдых между подходами, шаг силовой, блоки аэробной и йоги).
 * Звуки синтезированы tools/bells.py (модальный синтез), лежат в res/raw. Играют как будильник
 * (USAGE_ALARM) — слышно и при выключенном звуке медиа, громкость — «Будильник».
 * Prefs.bell: id колокольчика или SIMPLE («Простой сигнал» — как было до 5.68).
 */
object Bells {
    data class Bell(val id: String, val name: String, val res: Int)

    /** Прежнее поведение: короткий сигнал ToneGenerator, у отдыха — системный звук уведомления. */
    const val SIMPLE = "beep"
    const val DEFAULT = "bell_hand"

    val all: List<Bell> = listOf(
        Bell("bell_hand", "Колокольчик", R.raw.bell_hand),
        Bell("bell_bowl", "Поющая чаша", R.raw.bell_bowl),
        Bell("bell_chimes", "Музыка ветра", R.raw.bell_chimes),
        Bell("bell_dingdong", "Динь-дон", R.raw.bell_dingdong),
        Bell("bell_glock", "Глокеншпиль", R.raw.bell_glock),
    )

    fun of(id: String): Bell? = all.firstOrNull { it.id == id }

    private var player: MediaPlayer? = null

    /** Сыграть выбранный в настройках колокольчик (или [id] — для прослушивания). */
    fun play(ctx: Context, id: String = Prefs(ctx).bell) {
        val b = of(id) ?: return
        runCatching {
            stop()
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val mp = MediaPlayer.create(ctx.applicationContext, b.res, attrs,
                (ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).generateAudioSessionId()) ?: return
            mp.setWakeMode(ctx.applicationContext, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            mp.setOnCompletionListener { it.release(); if (player === it) player = null }
            player = mp
            mp.start()
        }.onFailure { Log.w("Bells", "не удалось сыграть $id", it) }
    }

    /** Системный звук уведомления как будильник — для «Простого сигнала» у таймера отдыха. */
    fun playSystem(ctx: Context) {
        runCatching {
            val r = android.media.RingtoneManager.getRingtone(ctx.applicationContext,
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)) ?: return
            r.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            r.play()
        }.onFailure { Log.w("Bells", "системный сигнал", it) }
    }

    fun stop() {
        player?.let { runCatching { it.stop(); it.release() } }
        player = null
    }
}
