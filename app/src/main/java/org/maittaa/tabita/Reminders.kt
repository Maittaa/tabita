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
    private const val CH_SLEEP = "sleep"
    private const val RC_WIND = 3
    private const val RC_BED = 4
    private const val RC_DAILY = 1
    private const val RC_PLAN = 2
    private const val RC_WEEK = 5
    private const val RC_MOTIV = 100
    private const val MAX_MOTIV = 10

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, t("Напоминание о дневнике"), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_MOTIV, t("Мотивация"), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_SLEEP, t("Отбой"), NotificationManager.IMPORTANCE_DEFAULT))
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
        am.cancel(pi(ctx, "wind", RC_WIND)); am.cancel(pi(ctx, "bed", RC_BED))
        am.cancel(pi(ctx, "week", RC_WEEK))

        val now = LocalDateTime.now()
        if (prefs.reminderOn) {
            var t = LocalDate.now().atStartOfDay().plusMinutes(prefs.reminderTime.toLong())
            if (!t.isAfter(now)) t = t.plusDays(1)
            at(ctx, t, pi(ctx, "daily", RC_DAILY))
        }
        if (prefs.motivOn && prefs.motivations.isNotEmpty()) planMotivations(ctx, prefs, LocalDate.now())
        if (prefs.weeklyOn) at(ctx, DayInsight.nextSunday(now), pi(ctx, "week", RC_WEEK))
        if (prefs.bedOn) runCatching { planBedtime(ctx, prefs, now) }.onFailure { Log.e("Remind", "отбой", it) }
        // пересчёт на новый день: мотивации — новые моменты, отбой — по свежему режиму и будильнику
        if ((prefs.motivOn && prefs.motivations.isNotEmpty()) || prefs.bedOn)
            at(ctx, LocalDate.now().plusDays(1).atStartOfDay().plusMinutes(5), pi(ctx, "plan", RC_PLAN))
    }

    /**
     * Отбой (5.33): время — из «Ритма дня» (будильник на завтра → привычный подъём, минус нужный сон).
     * За [Prefs.bedLead] минут — «скоро отбой», в само время — «пора спать». Ночь считается по вечеру:
     * до полудня — ещё прошлая ночь.
     */
    fun bedtimeAt(repo: Repo, prefs: Prefs, now: LocalDateTime): Pair<LocalDateTime, DayRhythm> {
        var eve = if (now.hour < 12) now.toLocalDate().minusDays(1) else now.toLocalDate()
        fun calc(d: LocalDate): Pair<LocalDateTime, DayRhythm> {
            val r = DayRhythm.of(repo, d, prefs, d.atTime(20, 0), Alarms.next)
            val t = d.atStartOfDay().plusMinutes(r.bed.toLong()).let { if (r.bed < 12 * 60) it.plusDays(1) else it }
            return t to r
        }
        var res = calc(eve)
        if (!res.first.isAfter(now)) { eve = eve.plusDays(1); res = calc(eve) }
        return res
    }

    private fun planBedtime(ctx: Context, prefs: Prefs, now: LocalDateTime) {
        Alarms.refresh(ctx)
        val (bed, _) = bedtimeAt(Repo(ctx), prefs, now)
        val wind = bed.minusMinutes(prefs.bedLead.toLong())
        if (prefs.bedLead > 0 && wind.isAfter(now)) at(ctx, wind, pi(ctx, "wind", RC_WIND))
        at(ctx, bed, pi(ctx, "bed", RC_BED))
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

    /**
     * 5.65: текст воскресного обзора — неделя пн–вс по данным дневника и Health Connect плюс главная
     * надёжная находка «Что влияет». Тренировки из истории приложения здесь недоступны, поэтому правку
     * про число силовых не предлагаем.
     */
    fun weekText(repo: Repo, prefs: Prefs, today: LocalDate): String? {
        val start = today.with(java.time.DayOfWeek.MONDAY)
        val r0 = WeekReview.of(repo, prefs, start, emptyList(), today)
        val r1 = r0.copy(strength = WeekReview.Count(0, 0))
        val r = r1.copy(fixes = WeekReview.fixes(r1, Goal.of(prefs.planGoal), PlanStats.of(repo, prefs, today).weight))
        val f = DayInsight.topFactor(repo.template.value, repo.entries.value, prefs.monthCfg, today)
        return DayInsight.weekText(r, f?.let { DayInsight.factorLine(it.first, it.second) })
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
            "week" -> {
                runCatching { weekText(Repo(ctx), prefs, LocalDate.now()) }.onFailure { Log.e("Remind", "обзор недели", it) }.getOrNull()
                    ?.let { show(ctx, CH_REMIND, 5, t("Ваша неделя"), it) }
                scheduleAll(ctx)
            }
            "motiv" -> {
                val text = intent.getStringExtra("text") ?: prefs.motivations.randomOrNull() ?: return
                show(ctx, CH_MOTIV, 2 + Random.nextInt(1000), t("Мотивация"), text)
            }
            "wind", "bed" -> {
                Alarms.refresh(ctx)
                val repo = Repo(ctx)
                val now = LocalDateTime.now()
                // время отбоя на эту ночь (уведомление «пора спать» могло прийти на пару минут позже)
                val (bed, r) = bedtimeAt(repo, prefs, now.minusMinutes(if (intent.action == "bed") 30 else 0))
                val wake = clockOf(r.nextWake)
                val sleepH = formatNumber(Math.round((r.need - 15) / 6.0) / 10.0)
                if (intent.action == "wind") show(ctx, CH_SLEEP, 3, t("Через %d мин — отбой в %s", prefs.bedLead, clockOf(bed.hour * 60 + bed.minute)),
                    t("Приглушите свет, отложите экраны и работу — так проще уснуть. Подъём в %s.", wake) +
                        (if (!r.alarm) " " + t("Будильник на завтра не стоит.") else ""))
                else show(ctx, CH_SLEEP, 4, t("Пора спать"),
                    t("Чтобы спать %s ч до подъёма в %s.", sleepH, wake) + (if (!r.alarm) " " + t("Будильник на завтра не стоит — поставьте на %s.", wake) else ""))
                scheduleAll(ctx)
            }
            "plan", Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.TIME_SET", "android.intent.action.TIMEZONE_CHANGED" -> scheduleAll(ctx)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Reminders.handle(context, intent)
}
