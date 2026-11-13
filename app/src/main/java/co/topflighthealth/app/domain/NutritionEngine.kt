package co.topflighthealth.app.domain

import co.topflighthealth.app.api.DaysMeals
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/*
 * Ported from iOS NutritionEngine.swift and WeeklyNutritionSummary.swift.
 * Single source of truth for day/week macro status on Android. Calories are
 * never stored or trusted: always protein×4 + carbs×4 + fats×9.
 * Keep in step with iOS; tests in NutritionEngineTest mirror the iOS tests.
 */

data class MacroNutrition(val protein: Double, val carbs: Double, val fats: Double, val fiber: Double) {
    val calories: Double get() = protein * 4 + carbs * 4 + fats * 9

    companion object {
        val ZERO = MacroNutrition(0.0, 0.0, 0.0, 0.0)
        fun sum(values: List<MacroNutrition>) = values.fold(ZERO) { a, b ->
            MacroNutrition(a.protein + b.protein, a.carbs + b.carbs, a.fats + b.fats, a.fiber + b.fiber)
        }
    }
}

/** Whole-number display rule (iOS MacroDisplay.int): round half away from zero. */
fun macroInt(v: Double): Int = if (v >= 0) Math.floor(v + 0.5).toInt() else -Math.floor(-v + 0.5).toInt()

/** Payback floors (iOS MacroSafeFloors). Never stored on DaysMeals. */
data class MacroSafeFloors(val protein: Double, val carbs: Double, val fats: Double) {
    companion object {
        const val PLANNING_MIN_CARBS = 130.0
        const val CARB_DEBT_ALLOWANCE = 20.0
        const val FAT_DEBT_ALLOWANCE = 9.0
        const val PROTEIN_PER_LB_GOAL = 0.7
        val ZERO = MacroSafeFloors(0.0, 0.0, 0.0)

        fun planningMinFats(sex: String?) = if (sex == "Female") 40.0 else 50.0

        fun from(goalWeight: Double?, sex: String?) = MacroSafeFloors(
            protein = max(0.0, PROTEIN_PER_LB_GOAL * (goalWeight ?: 0.0)),
            carbs = max(0.0, PLANNING_MIN_CARBS - CARB_DEBT_ALLOWANCE),
            fats = max(0.0, planningMinFats(sex) - FAT_DEBT_ALLOWANCE),
        )
    }
}

/** Per-channel payback-adjusted goal and remaining (iOS EffectiveChannelTarget). */
data class EffectiveChannelTarget(
    val dayGoal: Double,
    val eaten: Double,
    val debt: Double,
    val bankedRoom: Double,
    val rawEffectiveGoal: Double,
    val displayGoal: Double,
    val displayRemaining: Double,
    val isBankClipped: Boolean,
    val isFloorBound: Boolean,
) {
    fun barStatus(tolerance: Double) = MacroDayStatus.evaluate(
        MacroDayStatus.ChannelInput(eaten, displayGoal, bankedRoom, null, tolerance, isBankClipped),
    )

    companion object {
        /** [lockedPrior] = Σ(eaten − goal) for days before this one (+ = surplus). */
        fun compute(dayGoal: Double, eaten: Double, lockedPrior: Double, safeFloor: Double): EffectiveChannelTarget {
            val debt = max(0.0, lockedPrior)
            val banked = max(0.0, -lockedPrior)
            val raw = dayGoal - debt
            val display = max(raw, safeFloor)
            val clipped = debt > 0.5
            return EffectiveChannelTarget(
                dayGoal, eaten, debt, banked, raw, display, display - eaten, clipped,
                isFloorBound = clipped && raw < safeFloor - 0.5,
            )
        }
    }
}

/** Locked balances before a day, for payback (the parts of iOS WeeklyBankInfo the engine reads). */
data class WeeklyBankInfo(
    val lockedCalorieBalance: Double,
    val lockedProteinBalance: Double,
    val lockedCarbsBalance: Double,
    val lockedFatsBalance: Double,
) {
    val bankedRoom: MacroDayStatus.BankedRoom
        get() = MacroDayStatus.BankedRoom(
            max(0.0, -lockedCalorieBalance), max(0.0, -lockedProteinBalance),
            max(0.0, -lockedCarbsBalance), max(0.0, -lockedFatsBalance),
        )
}

/**
 * One day's macros with payback applied (iOS DayMacroSnapshot). Bars and day
 * chips use the effective targets; DaysMeals goals are never changed.
 */
data class DayMacroSnapshot(
    val goal: MacroNutrition,
    val eaten: MacroNutrition,
    val dayRemaining: MacroNutrition,
    val bankRemaining: MacroNutrition?,
    val protein: EffectiveChannelTarget,
    val carbs: EffectiveChannelTarget,
    val fats: EffectiveChannelTarget,
    val caloriesDisplayGoal: Double,
    val caloriesDisplayRemaining: Double,
    val caloriesIsBankClipped: Boolean,
    val caloriesIsFloorBound: Boolean,
) {
    enum class Channel { Calories, Protein, Carbs, Fats }

    fun barStatus(channel: Channel, bankedRoom: Double, tolerance: Double): MacroDayStatus.ChannelResult = when (channel) {
        Channel.Protein -> protein.barStatus(tolerance)
        Channel.Carbs -> carbs.barStatus(tolerance)
        Channel.Fats -> fats.barStatus(tolerance)
        Channel.Calories -> MacroDayStatus.evaluate(
            MacroDayStatus.ChannelInput(eaten.calories, caloriesDisplayGoal, bankedRoom, null, tolerance, caloriesIsBankClipped),
        )
    }

    companion object {
        fun build(day: DaysMeals, bank: WeeklyBankInfo?, floors: MacroSafeFloors = MacroSafeFloors.ZERO): DayMacroSnapshot {
            val eaten = day.dayNutrition
            val goal = MacroNutrition(day.goalProtein, day.goalCarbs, day.goalFats, day.goalFiber)
            val rem = MacroNutrition(goal.protein - eaten.protein, goal.carbs - eaten.carbs, goal.fats - eaten.fats, goal.fiber - eaten.fiber)
            val lp = bank?.lockedProteinBalance ?: 0.0
            val lc = bank?.lockedCarbsBalance ?: 0.0
            val lf = bank?.lockedFatsBalance ?: 0.0
            val lcal = bank?.lockedCalorieBalance ?: 0.0
            val bankRem = bank?.let { MacroNutrition(rem.protein - lp, rem.carbs - lc, rem.fats - lf, rem.fiber) }
            // No bank object → no debt (prior days can't be proven).
            val p = EffectiveChannelTarget.compute(day.goalProtein, eaten.protein, if (bank == null) 0.0 else lp, floors.protein)
            val c = EffectiveChannelTarget.compute(day.goalCarbs, eaten.carbs, if (bank == null) 0.0 else lc, floors.carbs)
            val f = EffectiveChannelTarget.compute(day.goalFats, eaten.fats, if (bank == null) 0.0 else lf, floors.fats)
            val calGoal = p.displayGoal * 4 + c.displayGoal * 4 + f.displayGoal * 9
            return DayMacroSnapshot(
                goal, eaten, rem, bankRem, p, c, f,
                caloriesDisplayGoal = calGoal,
                caloriesDisplayRemaining = calGoal - eaten.calories,
                // Calories clip only when the calorie bank itself is in debt.
                caloriesIsBankClipped = bank != null && lcal > 0.5,
                caloriesIsFloorBound = p.isFloorBound || c.isFloorBound || f.isFloorBound,
            )
        }
    }
}

object MacroDayStatus {
    data class BankedRoom(val calories: Double, val protein: Double, val carbs: Double, val fats: Double) {
        companion object { val ZERO = BankedRoom(0.0, 0.0, 0.0, 0.0) }
    }

    data class DayTotals(
        val day: LocalDate,
        val goalCalories: Double, val goalProtein: Double, val goalCarbs: Double, val goalFats: Double,
        val eatenCalories: Double, val eatenProtein: Double, val eatenCarbs: Double, val eatenFats: Double,
        val hasMeals: Boolean,
    )

    enum class Badge { None, Checkmark, Dollar, Multiply }

    data class ChannelInput(
        val consumed: Double,
        /** Display goal (payback-adjusted, floored). */
        val dayGoal: Double,
        val bankedRoom: Double,
        val weekRemaining: Double?,
        /** Wiggle: calories 28, protein 10, carbs/fats 5. */
        val completeTolerance: Double,
        val isPaybackClipped: Boolean = false,
    )

    data class ChannelResult(
        val remainingLabel: Double,
        val goalLabel: Double,
        val isBorrowing: Boolean,
        val isWeekClipped: Boolean,
        val badge: Badge,
        val fillProgress: Double,
        val isRed: Boolean,
        val usedBank: Boolean,
    )

    object Tolerance {
        const val CALORIES = 28.0
        const val PROTEIN = 10.0
        const val CARBS = 5.0
        const val FATS = 5.0
    }

    fun bankedRoom(prior: List<DayTotals>): BankedRoom {
        var c = 0.0; var p = 0.0; var cb = 0.0; var f = 0.0
        prior.filter { it.hasMeals }.forEach {
            c += it.eatenCalories - it.goalCalories
            p += it.eatenProtein - it.goalProtein
            cb += it.eatenCarbs - it.goalCarbs
            f += it.eatenFats - it.goalFats
        }
        return BankedRoom(max(0.0, -c), max(0.0, -p), max(0.0, -cb), max(0.0, -f))
    }

    /** Banked room on the morning of [day]; null if any day in [weekStart, day) has no meals. */
    fun bankedRoom(days: List<DayTotals>, before: LocalDate, weekStart: LocalDate): BankedRoom? {
        val byDay = days.associateBy { it.day }
        val prior = mutableListOf<DayTotals>()
        var d = weekStart
        var offset = 0
        while (d < before) {
            val doc = byDay[d]
            if (doc != null && doc.hasMeals) prior += doc else return null
            offset++
            if (offset > 7) break
            d = d.plusDays(1)
        }
        return bankedRoom(prior)
    }

    fun evaluate(input: ChannelInput): ChannelResult {
        val goal = input.dayGoal
        val consumed = input.consumed
        val banked = max(0.0, input.bankedRoom)
        val wiggle = max(0.0, input.completeTolerance)
        val star = input.isPaybackClipped
        val okCeiling = goal + max(banked, wiggle)
        val wiggleCeiling = goal + wiggle
        val remaining = goal - consumed

        if (goal > 0 && consumed > okCeiling) {
            return ChannelResult(remaining, goal, true, star, Badge.Multiply, 1.0, true, banked > 0)
        }
        if (goal > 0 && consumed > wiggleCeiling) {
            return ChannelResult(remaining, goal, false, star, Badge.Dollar, 1.0, false, true)
        }
        if (goal > 0 && consumed > goal) {
            return ChannelResult(remaining, goal, false, star, Badge.Checkmark, 1.0, false, false)
        }
        var rem = remaining
        var extraClip = false
        val w = input.weekRemaining
        if (w != null && w < rem) {
            rem = w
            extraClip = true
        }
        val progress = fillProgress(consumed, max(0.0, rem))
        val nearZero = rem <= wiggle
        val hitGoal = goal > 0 && consumed >= goal - wiggle
        val hitClipped = extraClip && nearZero && consumed > 0 && progress >= 0.99
        val badge = if ((nearZero && hitGoal) || hitClipped) Badge.Checkmark else Badge.None
        return ChannelResult(rem, goal, false, star || extraClip, badge, progress, false, false)
    }

    fun fillProgress(consumed: Double, remainingShown: Double): Double {
        val denom = consumed + max(0.0, remainingShown)
        if (denom <= 0) return 0.0
        return min(max(consumed / denom, 0.0), 1.0)
    }
}

/** Weekly scorecard math (iOS WeeklyNutritionSummary). balance = eaten − goal; negative is a deficit. */
data class WeeklyNutritionSummary(
    val weekStart: LocalDate,
    val days: List<DaySummary>,
    val lockedBalance: Double,
    val liveBalance: Double,
    val weekGoalTotal: Double,
    val weekEatenTotal: Double,
    val tdeeTickOffset: Double,
    val barEnabled: Boolean,
    val spendableDeficit: Double,
    val unaccountedCompletedDayCount: Int,
    val daysInPlay: Int,
    val dailyTDEE: Double,
    val dailyGoal: Double,
    val userObjective: String?,
    val lockedProteinBalance: Double,
    val liveProteinBalance: Double,
    val lockedCarbsBalance: Double,
    val liveCarbsBalance: Double,
    val lockedFatsBalance: Double,
    val liveFatsBalance: Double,
) {
    /** A macro bar shows when the week is fully tracked, or that macro is still in surplus. */
    fun macroBarShowable(liveBalance: Double) = barEnabled || liveBalance > 0.5
    val anyMacroShowable: Boolean
        get() = macroBarShowable(liveProteinBalance) || macroBarShowable(liveCarbsBalance) || macroBarShowable(liveFatsBalance)
    val macroBarsPartiallyEnabled: Boolean get() = !barEnabled && anyMacroShowable

    data class DaySummary(
        val day: LocalDate,
        val isToday: Boolean,
        val isFuture: Boolean,
        val isCompleted: Boolean,
        val hasDoc: Boolean,
        val accountedFor: Boolean,
        val totalCalories: Double, val goalCalories: Double,
        val totalProtein: Double, val goalProtein: Double,
        val totalCarbs: Double, val goalCarbs: Double,
        val totalFats: Double, val goalFats: Double,
        val proteinTarget: EffectiveChannelTarget,
    ) {
        val calorieBalance: Double get() = totalCalories - goalCalories
        val proteinProgress: Double
            get() = proteinTarget.displayGoal.let { g -> if (g > 0) min(totalProtein / g, 1.0) else 0.0 }
        val proteinProgressRaw: Double
            get() = proteinTarget.displayGoal.let { g -> if (g > 0) totalProtein / g else 0.0 }
        fun proteinBarStatus(tolerance: Double = MacroDayStatus.Tolerance.PROTEIN) = proteinTarget.barStatus(tolerance)
    }

    companion object {
        fun build(
            daysMeals: List<DaysMeals>,
            weekStart: LocalDate,
            today: LocalDate,
            goalFallback: Double,
            dailyTDEE: Double,
            dailyGoal: Double,
            userObjective: String?,
            goalProteinFallback: Double = 0.0,
            goalCarbsFallback: Double = 0.0,
            goalFatsFallback: Double = 0.0,
            floors: MacroSafeFloors = MacroSafeFloors.ZERO,
        ): WeeklyNutritionSummary {
            val byDay = daysMeals.associateBy { it.day }
            val days = mutableListOf<DaySummary>()
            var locked = 0.0; var weekGoal = 0.0; var weekEaten = 0.0
            var todayEaten = 0.0; var todayGoal = 0.0; var unaccounted = 0
            var lockedP = 0.0; var lockedC = 0.0; var lockedF = 0.0
            var todayP = 0.0; var todayPG = 0.0; var todayC = 0.0; var todayCG = 0.0; var todayF = 0.0; var todayFG = 0.0
            var priorLockedP = 0.0
            var priorBankOk = true

            for (offset in 0 until 7) {
                val d = weekStart.plusDays(offset.toLong())
                val isToday = d == today
                val isFuture = d > today
                val isCompleted = d < today
                val dm = byDay[d]
                val eaten = dm?.dayNutrition ?: MacroNutrition.ZERO
                val mealCount = dm?.meals?.size ?: 0
                val goalCal = dm?.goalCalories ?: goalFallback
                val goalP = dm?.goalProtein ?: goalProteinFallback
                val goalC = dm?.goalCarbs ?: goalCarbsFallback
                val goalF = dm?.goalFats ?: goalFatsFallback
                val accounted = isCompleted && mealCount > 0

                if (isCompleted) {
                    if (accounted) {
                        locked += eaten.calories - goalCal
                        weekEaten += eaten.calories
                        lockedP += eaten.protein - goalP
                        lockedC += eaten.carbs - goalC
                        lockedF += eaten.fats - goalF
                    } else {
                        unaccounted++
                    }
                } else if (isToday) {
                    todayEaten = eaten.calories; todayGoal = goalCal; weekEaten += eaten.calories
                    todayP = eaten.protein; todayPG = goalP
                    todayC = eaten.carbs; todayCG = goalC
                    todayF = eaten.fats; todayFG = goalF
                }
                weekGoal += goalCal

                val proteinTarget = EffectiveChannelTarget.compute(
                    dayGoal = goalP, eaten = eaten.protein,
                    lockedPrior = if (priorBankOk) priorLockedP else 0.0,
                    safeFloor = floors.protein,
                )
                days += DaySummary(
                    d, isToday, isFuture, isCompleted, dm != null, accounted,
                    eaten.calories, goalCal, eaten.protein, goalP, eaten.carbs, goalC, eaten.fats, goalF,
                    proteinTarget,
                )
                if (mealCount > 0) priorLockedP += eaten.protein - goalP else priorBankOk = false
            }

            val live = locked + max(0.0, todayEaten - todayGoal)
            var inPlay = days.count { it.isCompleted || it.isToday }
            if (inPlay == 0) inPlay = 7
            return WeeklyNutritionSummary(
                weekStart = weekStart,
                days = days,
                lockedBalance = locked,
                liveBalance = live,
                weekGoalTotal = weekGoal,
                weekEatenTotal = weekEaten,
                tdeeTickOffset = (dailyTDEE - dailyGoal) * inPlay,
                barEnabled = unaccounted == 0,
                spendableDeficit = max(0.0, -locked),
                unaccountedCompletedDayCount = unaccounted,
                daysInPlay = inPlay,
                dailyTDEE = dailyTDEE,
                dailyGoal = dailyGoal,
                userObjective = userObjective,
                lockedProteinBalance = lockedP,
                liveProteinBalance = lockedP + (todayP - todayPG),
                lockedCarbsBalance = lockedC,
                liveCarbsBalance = lockedC + (todayC - todayCG),
                lockedFatsBalance = lockedF,
                liveFatsBalance = lockedF + (todayF - todayFG),
            )
        }
    }
}
