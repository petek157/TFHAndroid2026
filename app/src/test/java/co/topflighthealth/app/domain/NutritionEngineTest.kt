package co.topflighthealth.app.domain

import co.topflighthealth.app.api.DaysMeals
import co.topflighthealth.app.domain.MacroDayStatus.Badge
import co.topflighthealth.app.domain.MacroDayStatus.ChannelInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Ports of iOS MacroDayStatusTests. */
class MacroDayStatusTest {
    @Test fun displayRoundingNegative() = assertEquals(-6, macroInt(-5.6))

    @Test fun paybackReducesGoalAndRemaining() {
        val t = EffectiveChannelTarget.compute(150.0, 55.0, 5.0, 0.0)
        assertEquals(5.0, t.debt, 0.0); assertEquals(145.0, t.displayGoal, 0.0); assertEquals(90.0, t.displayRemaining, 0.0)
        assertTrue(t.isBankClipped); assertFalse(t.isFloorBound)
    }

    @Test fun bankLooserDoesNotRaiseGoal() {
        val t = EffectiveChannelTarget.compute(150.0, 55.0, -10.0, 0.0)
        assertEquals(0.0, t.debt, 0.0); assertEquals(150.0, t.displayGoal, 0.0); assertEquals(95.0, t.displayRemaining, 0.0)
        assertFalse(t.isBankClipped)
    }

    @Test fun safeFloorRaisesDisplayGoalWhenDebtHuge() {
        val t = EffectiveChannelTarget.compute(150.0, 40.0, 200.0, 105.0)
        assertEquals(-50.0, t.rawEffectiveGoal, 0.0); assertEquals(105.0, t.displayGoal, 0.0)
        assertEquals(65.0, t.displayRemaining, 0.0); assertTrue(t.isFloorBound)
    }

    @Test fun evaluatePaybackUsesDisplayGoal() {
        val r = MacroDayStatus.evaluate(ChannelInput(140.0, 145.0, 0.0, null, 10.0, true))
        assertEquals(Badge.Checkmark, r.badge); assertEquals(5.0, r.remainingLabel, 0.0)
        assertEquals(145.0, r.goalLabel, 0.0); assertTrue(r.isWeekClipped)
    }

    @Test fun safeFloorConstants() {
        assertEquals(110.0, MacroSafeFloors.PLANNING_MIN_CARBS - MacroSafeFloors.CARB_DEBT_ALLOWANCE, 0.0)
        assertEquals(41.0, 50.0 - MacroSafeFloors.FAT_DEBT_ALLOWANCE, 0.0)
        assertEquals(140.0, MacroSafeFloors.PROTEIN_PER_LB_GOAL * 200, 1e-9)
    }

    @Test fun fillProgressFullAtZeroRem() = assertEquals(1.0, MacroDayStatus.fillProgress(45.0, 0.0), 0.0)

    @Test fun underDayRemaining() {
        val r = MacroDayStatus.evaluate(ChannelInput(40.0, 100.0, 0.0, null, 5.0))
        assertEquals(60.0, r.remainingLabel, 0.0); assertEquals(100.0, r.goalLabel, 0.0)
    }

    @Test fun overWiggleIntoBankIsDollarAndPastBankIsRed() {
        val dollar = MacroDayStatus.evaluate(ChannelInput(120.0, 100.0, 30.0, null, 5.0))
        assertEquals(Badge.Dollar, dollar.badge); assertFalse(dollar.isRed)
        val red = MacroDayStatus.evaluate(ChannelInput(140.0, 100.0, 30.0, null, 5.0))
        assertEquals(Badge.Multiply, red.badge); assertTrue(red.isRed)
    }

    @Test fun snapshotBuildWithBankDebt() {
        val day = Fixtures.dayWithTwoMeals
        val snap = DayMacroSnapshot.build(day, WeeklyBankInfo(0.0, 5.0, 0.0, 0.0))
        val expectedGoal = day.goalProtein - 5
        val expectedRem = expectedGoal - day.dayNutrition.protein
        assertTrue(snap.protein.isBankClipped)
        assertEquals(expectedGoal, snap.protein.displayGoal, 1e-9)
        assertEquals(expectedRem, snap.protein.displayRemaining, 1e-9)
        val bar = snap.barStatus(DayMacroSnapshot.Channel.Protein, 0.0, 10.0)
        assertEquals(expectedGoal, bar.goalLabel, 1e-9); assertEquals(expectedRem, bar.remainingLabel, 1e-9)
        assertTrue(bar.isWeekClipped)
    }

    private fun emptyDay() = DaysMeals("dm", Fixtures.iso(LocalDate.now()), "user-1", emptyList(), 1570.0, 150.0, 130.0, 50.0, 30.0, 0)

    @Test fun caloriesNotClippedWhenOnlyMacroInDebt() {
        val snap = DayMacroSnapshot.build(emptyDay(), WeeklyBankInfo(-51.0, 8.0, -14.0, -3.0))
        assertTrue(snap.protein.isBankClipped); assertEquals(142.0, snap.protein.displayGoal, 0.0)
        assertFalse(snap.caloriesIsBankClipped); assertEquals(1538.0, snap.caloriesDisplayGoal, 0.0)
        assertFalse(snap.barStatus(DayMacroSnapshot.Channel.Calories, 0.0, MacroDayStatus.Tolerance.CALORIES).isWeekClipped)
        assertTrue(snap.barStatus(DayMacroSnapshot.Channel.Protein, 0.0, MacroDayStatus.Tolerance.PROTEIN).isWeekClipped)
    }

    @Test fun caloriesClippedWhenCalorieBankInDebt() {
        val snap = DayMacroSnapshot.build(emptyDay(), WeeklyBankInfo(125.0, 10.0, 10.0, 5.0))
        assertTrue(snap.caloriesIsBankClipped); assertTrue(snap.protein.isBankClipped)
        assertTrue(snap.carbs.isBankClipped); assertTrue(snap.fats.isBankClipped)
    }
}

/** Ports of iOS WeeklyNutritionSummaryTests (Mon 2026-07-06 to Sun, today = Wed). */
class WeeklyNutritionSummaryTest {
    private val weekStart = LocalDate.of(2026, 7, 6)
    private val today = LocalDate.of(2026, 7, 8)
    private val goalFallback = 1500.0
    // The iOS breakfast fixture is 284.997 kcal (oil is 13.333 g fat), so compare with a small tolerance.
    private val eps = 0.01

    private fun day(offset: Int, meals: List<co.topflighthealth.app.api.Meal> = emptyList(), goalCal: Double = 1500.0, goalPro: Double = 150.0) =
        DaysMeals("dm-$offset", Fixtures.iso(weekStart.plusDays(offset.toLong())), "user-1", meals, goalCal, goalPro, 200.0, 70.0, 30.0)

    private fun atGoal(o: Int) = day(o, listOf(Fixtures.breakfast), 285.0, 30.0)
    private fun deficit(o: Int, d: Double) = day(o, listOf(Fixtures.breakfast), 285.0 + d, 30.0)
    private fun surplus(o: Int, s: Double) = day(o, listOf(Fixtures.breakfast), 285.0 - s, 30.0)

    private fun summarize(docs: List<DaysMeals>, tdeeTick: Double = 0.0): WeeklyNutritionSummary {
        val tdee = if (tdeeTick != 0.0) goalFallback + tdeeTick / 3.0 else goalFallback
        return WeeklyNutritionSummary.build(docs, weekStart, today, goalFallback, tdee, goalFallback, "Lose Fat")
    }

    @Test fun sevenDaysMondayToSunday() {
        val s = summarize(emptyList())
        assertEquals(7, s.days.size)
        assertTrue(s.days[0].isCompleted); assertTrue(s.days[1].isCompleted); assertTrue(s.days[2].isToday)
        assertTrue(s.days[3].isFuture); assertTrue(s.days[6].isFuture)
    }

    @Test fun emptyWeekGatesBar() {
        val s = summarize(emptyList())
        assertEquals(2, s.unaccountedCompletedDayCount); assertFalse(s.barEnabled)
        assertEquals(0.0, s.lockedBalance, 0.0); assertEquals(0.0, s.liveBalance, 0.0); assertEquals(0.0, s.spendableDeficit, 0.0)
    }

    @Test fun fullyPastUntrackedWeekGatesBar() {
        val s = WeeklyNutritionSummary.build(emptyList(), weekStart, LocalDate.of(2026, 7, 13), goalFallback, goalFallback, goalFallback, "Lose Fat")
        assertEquals(7, s.unaccountedCompletedDayCount); assertFalse(s.barEnabled)
    }

    @Test fun allAccountedAtGoalIsOnPace() {
        val s = summarize(listOf(atGoal(0), atGoal(1)))
        assertEquals(0, s.unaccountedCompletedDayCount); assertTrue(s.barEnabled)
        assertEquals(0.0, s.lockedBalance, eps); assertEquals(0.0, s.spendableDeficit, eps); assertEquals(0.0, s.liveBalance, eps)
    }

    @Test fun oneUntrackedCompletedDayGatesBar() {
        val s = summarize(listOf(atGoal(0)))
        assertEquals(1, s.unaccountedCompletedDayCount); assertFalse(s.barEnabled); assertEquals(0.0, s.lockedBalance, eps)
    }

    @Test fun emptyDocDayIsNotAccounted() {
        val s = summarize(listOf(day(0), atGoal(1)))
        assertEquals(1, s.unaccountedCompletedDayCount); assertFalse(s.barEnabled); assertFalse(s.days[0].accountedFor)
    }

    @Test fun completedDeficitsBank() {
        val s = summarize(listOf(deficit(0, 100.0), deficit(1, 100.0)))
        assertTrue(s.barEnabled); assertEquals(-200.0, s.lockedBalance, eps); assertEquals(200.0, s.spendableDeficit, eps)
    }

    @Test fun completedSurplusShowsAsOver() {
        val s = summarize(listOf(surplus(0, 100.0), surplus(1, 100.0)))
        assertEquals(200.0, s.lockedBalance, eps); assertEquals(0.0, s.spendableDeficit, eps)
    }

    @Test fun todaySurplusOnlyInLiveLayer() {
        val s = summarize(listOf(atGoal(0), atGoal(1), surplus(2, 100.0)))
        assertEquals(0.0, s.lockedBalance, eps); assertEquals(100.0, s.liveBalance, eps)
        assertTrue(s.days[2].isToday); assertFalse(s.days[2].accountedFor)
    }

    @Test fun todaySurplusDoesNotAddCredit() {
        val s = summarize(listOf(deficit(0, 100.0), surplus(2, 100.0)))
        assertEquals(-100.0, s.lockedBalance, eps); assertEquals(100.0, s.spendableDeficit, eps); assertEquals(0.0, s.liveBalance, eps)
    }

    @Test fun tdeeTickOffsetPassesThrough() = assertEquals(3500.0, summarize(emptyList(), 3500.0).tdeeTickOffset, 1e-6)

    @Test fun weekGoalTotalUsesFallback() =
        assertEquals(285.0 + 285 + 1500 * 5, summarize(listOf(atGoal(0), atGoal(1))).weekGoalTotal, 0.0)

    private fun proteinDay(o: Int, eaten: Double, goal: Double = 150.0) =
        day(o, listOf(Fixtures.meal("m-$o", weekStart.plusDays(o.toLong()), Fixtures.item("p", eaten, 0.0, 0.0))), 1500.0, goal)

    @Test fun proteinTargetUsesPriorLockedDebt() {
        val tue = summarize(listOf(proteinDay(0, 156.0), proteinDay(1, 135.0))).days[1]
        assertEquals(6.0, tue.proteinTarget.debt, 1e-9); assertEquals(144.0, tue.proteinTarget.displayGoal, 1e-9)
        assertEquals(9.0, tue.proteinTarget.displayRemaining, 1e-9); assertTrue(tue.proteinTarget.isBankClipped)
        assertEquals(135.0 / 144.0, tue.proteinProgress, 0.001)
        val st = tue.proteinBarStatus(); assertFalse(st.isRed); assertEquals(144.0, st.goalLabel, 1e-9)
    }

    @Test fun proteinTargetIgnoresBankWhenPriorDayUntracked() {
        val tue = summarize(listOf(day(0), proteinDay(1, 135.0))).days[1]
        assertEquals(0.0, tue.proteinTarget.debt, 0.0); assertEquals(150.0, tue.proteinTarget.displayGoal, 0.0)
        assertFalse(tue.proteinTarget.isBankClipped)
    }

    @Test fun summaryAndJournalShareProteinMath() {
        val mon = proteinDay(0, 156.0); val tue = proteinDay(1, 135.0)
        val s = summarize(listOf(mon, tue))
        val e = mon.dayNutrition
        val bank = WeeklyBankInfo(e.calories - mon.goalCalories, e.protein - mon.goalProtein, e.carbs - mon.goalCarbs, e.fats - mon.goalFats)
        val snap = DayMacroSnapshot.build(tue, bank)
        assertEquals(snap.protein, s.days[1].proteinTarget)
        assertEquals(snap.barStatus(DayMacroSnapshot.Channel.Protein, bank.bankedRoom.protein, MacroDayStatus.Tolerance.PROTEIN), s.days[1].proteinBarStatus())
    }

    private fun fatDay(o: Int, fat: Double) =
        day(o, listOf(Fixtures.meal("f-$o", weekStart.plusDays(o.toLong()), Fixtures.item("f", 0.0, 0.0, fat))), 1500.0, 150.0)

    @Test fun allMacrosShowableWhenTracked() {
        val s = summarize(listOf(atGoal(0), atGoal(1)))
        assertTrue(s.macroBarShowable(s.liveProteinBalance)); assertTrue(s.anyMacroShowable); assertFalse(s.macroBarsPartiallyEnabled)
    }

    @Test fun surplusMacroShowsDespiteHoles() {
        val s = summarize(listOf(fatDay(0, 100.0)))
        assertFalse(s.barEnabled); assertEquals(30.0, s.liveFatsBalance, 1e-9)
        assertTrue(s.macroBarShowable(s.liveFatsBalance)); assertTrue(s.macroBarsPartiallyEnabled)
        assertFalse(s.macroBarShowable(s.liveProteinBalance)); assertFalse(s.macroBarShowable(s.liveCarbsBalance))
    }

    @Test fun allDeficitsWithHolesShowNothing() {
        val s = summarize(listOf(fatDay(0, 50.0)))
        assertEquals(-20.0, s.liveFatsBalance, 1e-9); assertFalse(s.anyMacroShowable)
    }

    @Test fun gateLogic() {
        val tracked = summarize(listOf(atGoal(0), atGoal(1)))
        assertTrue(tracked.macroBarShowable(-100.0)); assertTrue(tracked.macroBarShowable(0.0))
        val holes = summarize(listOf(atGoal(0)))
        assertFalse(holes.macroBarShowable(-100.0)); assertFalse(holes.macroBarShowable(0.3)); assertTrue(holes.macroBarShowable(1.0))
    }
}
