package org.maittaa.tabita.train

import org.maittaa.tabita.R

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TimerState(val endElapsed: Long, val totalSec: Int, val label: String) {
    fun remainingMs(now: Long = SystemClock.elapsedRealtime()) = (endElapsed - now).coerceAtLeast(0)
}

/** Таймер отдыха. Отсчёт идёт в foreground-сервисе, поэтому работает при выключенном экране. */
object RestTimer {
    private val _state = MutableStateFlow<TimerState?>(null)
    val state: StateFlow<TimerState?> = _state
    /** Счётчик срабатываний — UI показывает «Отдых окончен». */
    val finishedCount = MutableStateFlow(0)

    fun start(ctx: Context, seconds: Int, label: String) {
        _state.value = TimerState(SystemClock.elapsedRealtime() + seconds * 1000L, seconds, label)
        kick(ctx)
    }

    fun add(ctx: Context, seconds: Int) {
        val s = _state.value ?: return
        val end = (s.endElapsed + seconds * 1000L).coerceAtLeast(SystemClock.elapsedRealtime() + 1000)
        _state.value = s.copy(endElapsed = end, totalSec = (s.totalSec + seconds).coerceAtLeast(1))
        kick(ctx)
    }

    fun stop(ctx: Context) {
        _state.value = null
        ctx.stopService(Intent(ctx, TimerService::class.java))
    }

    internal fun finished() {
        _state.value = null
        finishedCount.value++
    }

    private fun kick(ctx: Context) {
        ContextCompat.startForegroundService(ctx, Intent(ctx, TimerService::class.java))
    }
}

class TimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wake: PowerManager.WakeLock? = null
    private val done = Runnable { onDone() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels(this)
        val s = RestTimer.state.value
        if (s == null) { stopSelf(); return START_NOT_STICKY }
        val remaining = s.remainingMs()
        ServiceCompat.startForeground(
            this, NOTIF_RUN, runningNotification(s, remaining),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        wake?.let { if (it.isHeld) it.release() }
        wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sink:rest").apply { acquire(remaining + 15_000) }
        handler.removeCallbacks(done)
        handler.postDelayed(done, remaining)
        return START_NOT_STICKY
    }

    private fun runningNotification(s: TimerState, remaining: Long): Notification =
        NotificationCompat.Builder(this, CH_RUN)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Отдых")
            .setContentText(s.label)
            .setWhen(System.currentTimeMillis() + remaining)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .build()

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, org.maittaa.tabita.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("openTrain", true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun onDone() {
        val label = RestTimer.state.value?.label ?: ""
        RestTimer.finished()
        vibrate()
        // звук из настроек; «Простой сигнал» — системный звук уведомления, как раньше
        org.maittaa.tabita.Bells.play(this)
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            nm.notify(
                NOTIF_DONE,
                NotificationCompat.Builder(this, CH_DONE)
                    .setSmallIcon(R.drawable.ic_timer)
                    .setContentTitle("Отдых окончен — следующий подход")
                    .setContentText(label)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setTimeoutAfter(60_000)
                    .setContentIntent(openApp())
                    .build(),
            )
        }
        stopSelf()
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= 31)
            getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 600), -1))
    }

    override fun onDestroy() {
        handler.removeCallbacks(done)
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }

    companion object {
        const val CH_RUN = "rest_running"
        const val CH_DONE = "rest_done_v2"
        const val NOTIF_RUN = 1
        const val NOTIF_DONE = 2

        fun createChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            runCatching { nm.deleteNotificationChannel("rest_done_v1") }
            nm.createNotificationChannel(
                NotificationChannel(CH_RUN, "Таймер отдыха", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
            nm.createNotificationChannel(
                // 5.68: канал без звука — звучит выбранный в настройках звук (Bells), канал v1 со звуком удаляется
                NotificationChannel(CH_DONE, "Отдых окончен", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                },
            )
        }
    }
}
