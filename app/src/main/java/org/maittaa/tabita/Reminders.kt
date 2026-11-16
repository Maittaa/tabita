package org.maittaa.tabita

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

/** Ежедневное напоминание и случайные мотивирующие уведомления. */
object Reminders {
    private const val CH_REMIND = "remind"
    private const val CH_MOTIV = "motiv"
    private const val RC_DAILY = 1
    private const val RC_PLAN = 2
    private const val RC_MOTIV = 100
    private const val MAX_MOTIV = 10

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, t("Напоминание о дневнике"), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_MOTIV, t("Мотивация"), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun pi(ctx: Context, action: String, rc: Int, text: String? = null): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(action).putExtra("text", text)
        return PendingIntent.getBroadcast(ctx, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun at(ctx: Context, time: LocalDateTime, p: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val ms = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // неточный будильник: не требует особых разрешений, срабатывает с точностью до нескольких минут
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p) }
            .onSuccess { Log.d("Remind", "запланировано на $time") }
            .onFailure { Log.e("Remind", "не удалось запланировать на $time", it) }
    }

    /** Перепланировать всё по текущим настройкам. */
    fun scheduleAll(ctx: Context) {
        val prefs = Prefs(ctx)
        I18n.init(prefs)
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pi(ctx, "daily", RC_DAILY))
        am.cancel(pi(ctx, "plan", RC_PLAN))
        (0 until MAX_MOTIV).forEach { am.cancel(pi(ctx, "motiv", RC_MOTIV + it)) }

        val now = LocalDateTime.now()
        if (prefs.reminderOn) {
            var t = LocalDate.now().atStartOfDay().plusMinutes(prefs.reminderTime.toLong())
            if (!t.isAfter(now)) t = t.plusDays(1)
            at(ctx, t, pi(ctx, "daily", RC_DAILY))
        }
        if (prefs.motivOn && prefs.motivations.isNotEmpty()) {
            planMotivations(ctx, prefs, LocalDate.now())
            at(ctx, LocalDate.now().plusDays(1).atStartOfDay().plusMinutes(5), pi(ctx, "plan", RC_PLAN))
        }
    }

    /** Случайные моменты в заданном окне на день [day]; прошедшие пропускаем. */
    private fun planMotivations(ctx: Context, prefs: Prefs, day: LocalDate) {
        val from = prefs.motivFrom; val to = maxOf(prefs.motivTo, from + 1)
        val n = prefs.motivPerDay.coerceIn(1, MAX_MOTIV)
        val list = prefs.motivations
        val rnd = Random(System.nanoTime())
        val now = LocalDateTime.now()
        (0 until n).map { from + rnd.nextInt(to - from) }.sorted().forEachIndexed { i, m ->
            val t = day.atStartOfDay().plusMinutes(m.toLong())
            if (t.isAfter(now)) at(ctx, t, pi(ctx, "motiv", RC_MOTIV + i, list[rnd.nextInt(list.size)]))
        }
    }

    fun show(ctx: Context, channel: String, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) { Log.i("Remind", "нет разрешения на уведомления"); return }
        createChannels(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
            .onSuccess { Log.d("Remind", "показано: $title / $text") }
            .onFailure { Log.e("Remind", "notify", it) }
    }

    /**
     * Текст вечернего напоминания: конкретно что не записано; null — всё записано.
     * Данные с часов не считаются заполнением.
     */
    fun dailyText(repo: Repo, cfg: MonthCfg, date: LocalDate): String? {
        val d = MonthData(repo.template.value, repo.entries.value, date, cfg, today = date)
        if (!d.filled(date)) {
            val streak = d.recordStreak(date).first
            return if (streak >= 3) t("Серия записей — %d дн. Даже одна оценка её продолжит.", streak)
            else t("Пара минут на сегодняшний день? Можно начать с оценки.")
        }
        val missing = d.dayIndex(date).missing
        return if (missing.isEmpty()) null else t("Не хватает: %s — секунд десять.", missing.joinToString { it.title.lowercase() })
    }

    fun handle(ctx: Context, intent: Intent) {
        val prefs = Prefs(ctx)
        I18n.init(prefs)
        Log.i("Remind", "сработало: ${intent.action}")
        when (intent.action) {
            "daily" -> {
                val repo = Repo(ctx)
                // день чекапа — одно тихое напоминание, без повторов
                if (repo.template.value.any { it.type == ItemType.WELLBEING && !it.hidden } &&
                    repo.checkups.value.isNotEmpty() && Wellbeing.daysLeft(repo.checkups.value, prefs.wbPeriod) == 0)
                    show(ctx, CH_REMIND, 2, "Tabita", t("Сегодня день чекапа самочувствия — минут пять, когда будет удобно."))
                val text = dailyText(repo, prefs.monthCfg, LocalDate.now())
                // «только если что-то не записано»: всё записано — молчим
                if (text != null) show(ctx, CH_REMIND, 1, "Tabita", text)
                else if (!prefs.reminderOnlyIfEmpty) show(ctx, CH_REMIND, 1, "Tabita", t("День записан. Загляните в итоги месяца, если интересно."))
                scheduleAll(ctx)
            }
            "motiv" -> {
                val text = intent.getStringExtra("text") ?: prefs.motivations.randomOrNull() ?: return
                show(ctx, CH_MOTIV, 2 + Random.nextInt(1000), t("Мотивация"), text)
            }
            "plan", Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.TIME_SET", "android.intent.action.TIMEZONE_CHANGED" -> scheduleAll(ctx)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Reminders.handle(context, intent)
}
