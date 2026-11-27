package org.maittaa.tabita.train

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class LogicTest {
    private val home = setOf(Equipment.DUMBBELLS, Equipment.BACKPACK, Equipment.CHAIR, Equipment.SOFA, Equipment.STEP)

    private fun show(p: Program) {
        println("== ${p.name} rest=${p.restSec}")
        p.days.forEach { d -> println("  ${d.name} ${d.weekday}: " + d.exercises.joinToString { "${it.def!!.name} ${it.sets}×${it.repMin}-${it.repMax}" }) }
    }

    @Test fun generatorHome() {
        val p = Generator.generate(GenSettings(), home)
        show(p)
        assertEquals(3, p.days.size)
        p.days.forEach { d ->
            assertEquals(7, d.exercises.size)
            assertEquals("нет повторов в дне", d.exercises.size, d.exercises.map { it.exId }.toSet().size)
            assertTrue(d.exercises.all { it.def!!.available(home, false) })
        }
    }

    @Test fun generatorNoEquipment() {
        for (lvl in 1..3) for (min in listOf(20, 30, 45)) {
            val p = Generator.generate(GenSettings(Goal.MASS, listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), min, lvl, true), home)
            if (lvl == 2 && min == 30) show(p)
            p.days.forEach { d ->
                assertTrue(d.exercises.size >= 5)
                assertTrue(d.exercises.all { it.def!!.needs.isEmpty() })
            }
        }
        val strength = Generator.generate(GenSettings(Goal.STRENGTH, listOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), 45, 3, false), Equipment.entries.toSet())
        show(strength)
    }

    @Test fun plannerSubstitutesAndLightens() {
        val p = Generator.generate(GenSettings(), home)
        val day = p.days[0]
        val plan = Planner.build(p, day, emptySet(), emptySet(), Place.HOME, false, Intensity.NORMAL, legsLight = true, restOverride = 0)
        println(plan.notes)
        assertTrue(plan.exercises.all { it.def!!.needs.isEmpty() })
        assertTrue(plan.notes.isNotEmpty())
        val legs = plan.exercises.filter { it.def!!.pattern.legs }
        assertTrue(legs.all { it.sets == 1 })
        val light = Planner.build(p, day, home, emptySet(), Place.HOME, false, Intensity.LIGHT, false, 0)
        assertEquals(p.restSec + 30, light.restSec)
        assertTrue(light.exercises.all { it.sets == 1 })
        val noEq = Planner.build(p, day, home, emptySet(), Place.HOME, true, Intensity.NORMAL, false, 0)
        assertTrue(noEq.noEquipment && noEq.exercises.all { it.def!!.needs.isEmpty() })
    }

    @Test fun gymAndBody() {
        val gymEq = Equipment.gymList.toSet()
        val p = Generator.generate(GenSettings(), home)
        val gymPlan = Planner.build(p, p.days[0], home, gymEq, Place.GYM, false, Intensity.NORMAL, false, 0, Body(183, 72.0))
        println("ЗАЛ: " + gymPlan.exercises.joinToString { "${it.name} ${it.scheme}" } + " | разминка " + gymPlan.warmups + " | " + gymPlan.notes)
        assertTrue(gymPlan.exercises.count { it.def!!.gymOnly } >= 3)
        val gp = Generator.generate(GenSettings(place = Place.GYM, level = 2), home, gymEq, Body(183, 72.0))
        show(gp)
        assertTrue(gp.days.all { d -> d.exercises.all { it.def!!.available(gymEq, false) } })
        // дома программа для зала заменяется домашними упражнениями
        val homePlan = Planner.build(gp, gp.days[0], home, gymEq, Place.HOME, false, Intensity.NORMAL, false, 0)
        println("ДОМА: " + homePlan.exercises.joinToString { it.name } + " | " + homePlan.notes)
        assertTrue(homePlan.exercises.all { it.def!!.available(home, false) })
        val heavy = Body(175, 95.0)
        assertTrue(heavy.heavy)
        val hp = Generator.generate(GenSettings(), home, body = heavy)
        show(hp); println(hp.notes)
        assertTrue(hp.notes.isNotEmpty())
        val hplan = Planner.build(hp, hp.days[0], home, emptySet(), Place.HOME, false, Intensity.NORMAL, false, 0, heavy)
        assertTrue("wu_jumps" !in hplan.warmups)
        assertEquals("≈ 32,5 кг", startLoad(Library.get("bb_squat"), Body(183, 72.0)))
    }

    @Test fun readiness() {
        val today = LocalDate.of(2026, 11, 17)
        val days = (1..20).map { i ->
            val d = today.minusDays(i.toLong())
            TDay(d, sleepMin = 450.0, rhr = 60.0 + (i % 2), steps = 8000.0, weight = 72.0 - i * 0.05, rate = 7.0)
        } + TDay(today, sleepMin = 330.0, rhr = 69.0)
        val r = ReadinessCalc.evaluate(TabitaData(days, null), today, Goal.MASS)
        r.reasons.forEach { println("  ${it.tone} ${it.text}") }
        assertEquals(Intensity.RECOVERY, r.intensity)
        val ok = ReadinessCalc.evaluate(TabitaData(days.dropLast(1) + TDay(today, sleepMin = 460.0, rhr = 60.0), null), today, Goal.MASS)
        assertEquals(Intensity.NORMAL, ok.intensity)
        val trend = ReadinessCalc.weightTrend(days, today)!!
        assertEquals(0.35, trend, 0.01)
        val busy = ReadinessCalc.evaluate(TabitaData(days.map { if (it.date == today.minusDays(1)) it.copy(steps = 18000.0) else it }, null), today, Goal.MASS)
        assertTrue(busy.legsLight)
    }

    @Test fun libraryHasGuides() {
        Library.all.forEach { assertTrue("нет пояснения: ${it.name}", it.info != null) }
        Library.warmups.forEach { assertTrue("нет пояснения: ${it.name}", Guide.find(it.name) != null) }
    }

    @Test fun profileFromTabita() {
        // формат выдачи Tabita 5.19
        val d = TabitaClient.parse("""{"v":1,"days":[{"d":"2026-11-15","w":72.4}],"profile":{"h":183,"age":30,"sex":"m"}}""")
        assertEquals(TProfile(183.0, 30, "m"), d.profile)
        // старая Tabita без профиля
        assertEquals(null, TabitaClient.parse("""{"v":1,"days":[]}""").profile)
        val b = Body(183, 72.4, true, age = 30, sex = "m", heightFromTabita = true, ageFromTabita = true, sexFromTabita = true)
        // Миффлин: 10·72,4 + 6,25·183 − 5·30 + 5
        assertEquals(1722.75, b.bmr!!, 0.01)
        val food = b.nutrition(Goal.MASS)
        food.forEach { println("  $it") }
        assertTrue(food[0].startsWith("Белок: 116–145"))
        assertTrue(food[1].contains("2700"))   // 1722,75 × 1,4 + 300 ≈ 2712 → 2700
        assertEquals(null, Body(183, 72.0).bmr)
        assertEquals("лет", yearsWord(30)); assertEquals("год", yearsWord(41)); assertEquals("года", yearsWord(22)); assertEquals("лет", yearsWord(12))
    }

    @Test fun nutritionAndDeload() {
        val today = LocalDate.of(2026, 11, 18)
        // неделя в дефиците, белок мало, сегодня почти ничего не съедено, пульс 3 дня повышен
        val days = (30 downTo 1).map { i ->
            val d = today.minusDays(i.toLong())
            TDay(d, sleepMin = 450.0, rhr = if (i <= 2) 66.0 else 58.0, weight = 72.0,
                kcal = if (i <= 7) 1900.0 else null, tdee = if (i <= 7) 2500.0 else null, protein = if (i <= 3) 70.0 else null)
        } + TDay(today, sleepMin = 450.0, rhr = 67.0, kcal = 300.0)
        val r = ReadinessCalc.evaluate(TabitaData(days, null), today, Goal.MASS, sessions7 = 3, hour = 17)
        r.reasons.forEach { println("  ${it.tone} ${it.text}") }
        assertTrue(r.holdProgression)
        assertTrue(r.reasons.any { it.text.startsWith("Неделя в дефиците") })
        assertTrue(r.proteinTip != null)
        assertTrue(r.reasons.any { it.text.contains("перекуси") })
        assertTrue(r.deloadSuggested)
        // без тренировок разгрузку не предлагаем
        assertTrue(!ReadinessCalc.evaluate(TabitaData(days, null), today, Goal.MASS, sessions7 = 0, hour = 17).deloadSuggested)
        // выключили питание — ни дефицита, ни белка, ни перекуса
        val off = TabitaUse.apply(TabitaData(days, null), TabitaUse.default - "food")
        val r2 = ReadinessCalc.evaluate(off, today, Goal.MASS, sessions7 = 3, hour = 17)
        assertTrue(r2.proteinTip == null && r2.reasons.none { it.text.contains("дефицит") || it.text.contains("перекуси") })
        // выключили сон — разгрузки нет
        assertTrue(!ReadinessCalc.evaluate(TabitaUse.apply(TabitaData(days, null), TabitaUse.default - "sleep"), today, Goal.MASS, sessions7 = 3).deloadSuggested)
        // формат Tabita 5.20.1
        val d = TabitaClient.parse("""{"v":1,"days":[{"d":"2026-11-17","kcal":2100,"tdee":2450,"p":95,"f":60,"c":250,"ffmi":19.4}]}""").days[0]
        assertEquals(95.0, d.protein!!, 0.01); assertEquals(19.4, d.ffmi!!, 0.01); assertEquals(2450.0, d.tdee!!, 0.01)
    }

    @Test fun deloadCutsSets() {
        val p = Generator.generate(GenSettings(), home)
        val day = p.days.first()
        val normal = Planner.build(p, day, home, emptySet(), Place.HOME, false, Intensity.NORMAL, false, 0)
        val dl = Planner.build(p, day, home, emptySet(), Place.HOME, false, Intensity.NORMAL, false, 0, deloadUntil = "2026-11-24")
        assertTrue(dl.exercises.sumOf { it.sets } < normal.exercises.sumOf { it.sets })
        assertTrue(dl.notes.any { it.startsWith("Разгрузочная неделя") })
    }

    @Test fun recommend() {
        val today = LocalDate.of(2026, 11, 18)
        val days = (20 downTo 0).map { i -> TDay(today.minusDays(i.toLong()), sleepMin = 450.0, rhr = 58.0, weight = 80.0, fat = 18.0) }
        val r = Recommender.recommend(GenSettings(), TabitaData(days, null), Body(183, 80.0), emptyList(), today)
        r.why.forEach { println("  $it") }
        assertEquals(Goal.MASS, r.s.goal)          // FFMI 80·0,82/3,35 ≈ 19,6
        assertEquals(1, r.s.level)
        assertEquals(30, r.s.minutes)
        // много жира → общая форма; плохой сон → 30 мин и не больше 3 дней
        val fatDays = days.map { it.copy(fat = 28.0, sleepMin = 340.0) }
        val r2 = Recommender.recommend(GenSettings(days = Recommender.daysFor(4)), TabitaData(fatDays, null), Body(183, 95.0), emptyList(), today)
        r2.why.forEach { println("  $it") }
        assertEquals(Goal.TONE, r2.s.goal); assertEquals(30, r2.s.minutes); assertEquals(3, r2.s.days.size)
        // без Tabita — цель прежняя
        assertEquals(Goal.STRENGTH, Recommender.recommend(GenSettings(goal = Goal.STRENGTH), null, Body(null, null), emptyList(), today).s.goal)
    }

    @Test fun muscleFocus() {
        val p = Generator.generate(GenSettings(), home)
        p.days.forEach { d -> println("  ${d.name}: ${MuscleFocus.describe(d)} | ${MuscleFocus.groups(d)}") }
        assertTrue(p.days.all { MuscleFocus.describe(it)!!.startsWith("Всё тело: ноги, ягодицы, грудь, спина") })
        // неравномерно — показываем упор
        val d = p.days[0].let { it.copy(exercises = it.exercises.mapIndexed { i, e -> if (i == 0) e.copy(sets = 4) else e }) }
        assertEquals("Всё тело · упор: ноги", MuscleFocus.describe(d))
    }

    @Test fun splits() {
        for (sp in Split.entries) {
            val days = if (sp == Split.FULL) Recommender.daysFor(3) else Recommender.daysFor(4)
            val p = Generator.generate(GenSettings(split = sp, days = days), home)
            println("== ${sp.title}: ${p.name}")
            p.days.forEach { d -> println("  ${d.name}: ${MuscleFocus.describe(d)} | " + d.exercises.joinToString { it.def!!.name }) }
            assertTrue(p.days.all { it.exercises.size >= 4 })
        }
        val ul = Generator.generate(GenSettings(split = Split.UPPER_LOWER, days = Recommender.daysFor(4)), home)
        assertEquals(listOf("Верх А", "Низ А", "Верх Б", "Низ Б"), ul.days.map { it.name })
        assertTrue(ul.days[0].exercises.none { it.def!!.pattern.legs })
        assertTrue(ul.days[1].exercises.all { it.def!!.pattern.legs || it.def!!.pattern == Pattern.CORE })
        val ppl = Generator.generate(GenSettings(split = Split.PPL, days = Recommender.daysFor(3)), home)
        assertEquals(listOf("Жим А", "Тяга А", "Ноги А"), ppl.days.map { it.name })
        // 4 дня и средний уровень — автоподбор предлагает верх/низ
        assertEquals(Split.UPPER_LOWER, Recommender.recommend(GenSettings(days = Recommender.daysFor(4)), null, Body(null, null),
            List(20) { Session(it.toString(), "a", "t", LocalDate.of(2026, 11, 17).minusDays(it * 2L).toString(), 0, 0, "", "", "", emptyList(), "", "", null) },
            LocalDate.of(2026, 11, 18)).s.split)
    }

    @Test fun pausedClock() {
        val plan = Planner.build(Generator.generate(GenSettings(), home), Generator.generate(GenSettings(), home).days[0], home, emptySet(),
            Place.HOME, false, Intensity.NORMAL, false, 0)
        val d = Draft(plan, startedAt = 1_000_000, pausedMs = 60_000, pausedSince = 1_400_000)
        // 10 мин прошло, минута паузы раньше и 100 с паузы сейчас
        assertEquals(600_000L - 60_000 - 200_000, d.elapsedMs(1_600_000))  // 10 мин, минута паузы раньше, 200 с паузы сейчас
        assertEquals(0L, Draft(plan, 0).elapsedMs(5_000))
        assertEquals(d.copy(), Draft.fromJson(d.toJson()).let { it.copy(plan = d.plan) })
    }

    private fun prog(vararg wd: DayOfWeek) = Program("t", Goal.MASS, 90, false,
        wd.mapIndexed { i, d -> ProgramDay(id = "d$i", name = "Т$i", weekday = d, exercises = listOf(PlanEx("pushup", 2, 8, 12))) })

    @Test fun postponeCascade() {
        val p = prog(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        assertEquals(2, Schedule.minGap(p))
        val mon = LocalDate.of(2026, 11, 16)
        val (o, moves) = Schedule.postpone(p, emptyMap(), mon)
        // Пн→Вт, Ср→Чт, Пт→Сб; следующий Пн — на месте
        assertEquals(listOf("2026-11-17", "2026-11-19", "2026-11-21"), moves.map { it.third.toString() })
        assertEquals(null, Schedule.dayOn(p, o, mon))
        assertEquals("d0", Schedule.dayOn(p, o, mon.plusDays(1))?.id)
        assertEquals(null, Schedule.dayOn(p, o, mon.plusDays(2)))
        assertEquals("d1", Schedule.dayOn(p, o, mon.plusDays(3))?.id)
        assertEquals("d2", Schedule.dayOn(p, o, mon.plusDays(5))?.id)
        assertEquals("d0", Schedule.dayOn(p, o, mon.plusDays(7))?.id)
        // ещё раз во вторник: Вт→Ср, Чт→Пт, Сб остаётся (Пт+2 = Вс? нет: Сб < Вс → Сб→Вс), следующий Пн — на месте? Вс+2 = Вт → Пн→Вт
        val (o2, m2) = Schedule.postpone(p, o, mon.plusDays(1))
        assertTrue(m2.isNotEmpty())
        assertEquals("d0", Schedule.dayOn(p, o2, mon.plusDays(2))?.id)
        // поправки, совпадающие с расписанием, не хранятся
        val simple = Schedule.simplify(p, mapOf("2026-11-16" to "d0", "2026-11-17" to "d0"))
        assertEquals(setOf("2026-11-17"), simple.keys)
    }

    @Test fun postponeSplitGap1() {
        val p = prog(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        assertEquals(1, Schedule.minGap(p))
        val mon = LocalDate.of(2026, 11, 16)
        val (_, moves) = Schedule.postpone(p, emptyMap(), mon)
        // Пн→Вт, Вт→Ср, Чт остаётся
        assertEquals(listOf("2026-11-17", "2026-11-18"), moves.map { it.third.toString() })
    }

    @Test fun verdictAndRecovery() {
        val today = LocalDate.of(2026, 11, 18)
        val days = (0..14).map { k -> TDay(today.minusDays(k.toLong()), sleepMin = if (k == 0) 229.0 else 450.0, rhr = 58.0) }
        val r = ReadinessCalc.evaluate(TabitaData(days, null), today, Goal.MASS)
        assertEquals(Verdict.SKIP, TodayAdvice.verdict(r))
        val tips = TodayAdvice.recovery(TabitaData(days, null), r, today, 72.0, hour = 9)
        assertTrue(tips.joinToString { it.title }, tips.any { it.title == "Короткий дневной сон" } && tips.any { it.title == "Активное восстановление" })
        assertTrue(tips.any { it.text.contains("115 г") })
        val ok = ReadinessCalc.evaluate(TabitaData(days.map { it.copy(sleepMin = 450.0) }, null), today, Goal.MASS)
        assertEquals(Verdict.GO, TodayAdvice.verdict(ok))
    }
}
