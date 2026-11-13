package co.topflighthealth.app.domain

import java.time.LocalDate
import java.time.Period
import kotlin.math.max
import kotlin.math.min

/**
 * Macro targets, ported from the web app's macroCalculations.ts, which mirrors
 * the iOS User.swift / NutritionEngine math. All three apps must give the same
 * numbers, so change them together. Tests: MacroMathTest.
 */
object MacroMath {
    const val MIN_PROTEIN_MULTIPLIER = 0.7
    const val MAX_PROTEIN_MULTIPLIER = 1.0
    const val MIN_CARB_MULTIPLIER = 0.85
    const val MAX_CARB_MULTIPLIER = 1.0
    const val MIN_CARBS_GRAMS = 130.0
    const val MAX_DEFICIT_CAP = -500.0
    const val DEFAULT_FIBER_GOAL = 30.0

    /** JavaScript's Math.round: halves round up. */
    fun jsRound(x: Double): Double = Math.floor(x + 0.5)

    fun lbsToKg(lbs: Double) = lbs * 0.453592
    fun kgToLbs(kg: Double) = kg / 0.453592
    fun heightCm(feet: Int, inches: Int) = feet * 30.48 + inches * 2.54

    fun age(birthday: LocalDate, today: LocalDate = LocalDate.now()): Int = Period.between(birthday, today).years

    fun bmr(currentWeight: Double, goalWeight: Double, sex: String, feet: Int, inches: Int, age: Int): Double {
        val over60 = currentWeight - goalWeight > 60
        val kg = if (over60) lbsToKg(goalWeight) else lbsToKg(currentWeight)
        val sexOffset = if (sex == "Male") 5.0 else -161.0
        return 10.0 * kg + 6.25 * heightCm(feet, inches) - 5.0 * age + sexOffset
    }

    fun tdee(bmr: Double, activityLevel: String): Double {
        val multiple = when (activityLevel) {
            "Sedentary" -> 1.1
            "Active" -> 1.45
            else -> 1.8
        }
        return jsRound(bmr * multiple)
    }

    fun minFatsGrams(sex: String) = if (sex == "Female") 40.0 else 50.0

    fun clampProteinMultiplier(v: Double) =
        if (!v.isFinite()) MAX_PROTEIN_MULTIPLIER else min(MAX_PROTEIN_MULTIPLIER, max(MIN_PROTEIN_MULTIPLIER, v))

    fun clampCarbMultiplier(v: Double) =
        if (!v.isFinite()) MAX_CARB_MULTIPLIER else min(MAX_CARB_MULTIPLIER, max(MIN_CARB_MULTIPLIER, v))

    fun proteinGrams(goalWeight: Double, multiplier: Double) = jsRound(goalWeight * multiplier)

    fun planningCarbGrams(multiplier: Double) = jsRound(MIN_CARBS_GRAMS * multiplier)

    fun effectiveCarbMultiplier(stored: Double?) =
        if (stored != null && !stored.isNaN()) clampCarbMultiplier(stored) else MAX_CARB_MULTIPLIER

    fun floorCalories(proteinGrams: Double, sex: String, carbGrams: Double = MIN_CARBS_GRAMS) =
        proteinGrams * 4 + carbGrams * 4 + minFatsGrams(sex) * 9

    /** Matches iOS MacroPlanning.autoProteinMultiplier. */
    fun autoProteinMultiplier(
        goalWeight: Double,
        tdee: Double,
        sex: String,
        objective: String?,
        carbGrams: Double = MIN_CARBS_GRAMS,
    ): Double {
        if (objective != "Lose Fat") return MAX_PROTEIN_MULTIPLIER
        val atOne = floorCalories(proteinGrams(goalWeight, MAX_PROTEIN_MULTIPLIER), sex, carbGrams)
        if (atOne < tdee) return MAX_PROTEIN_MULTIPLIER
        val minAllowedFloor = tdee + MAX_DEFICIT_CAP
        for (hundredths in 70..100) {
            val m = hundredths / 100.0
            if (floorCalories(proteinGrams(goalWeight, m), sex, carbGrams) >= minAllowedFloor) return m
        }
        return MIN_PROTEIN_MULTIPLIER
    }

    fun effectiveProteinMultiplier(
        goalWeight: Double,
        tdee: Double,
        sex: String,
        objective: String?,
        stored: Double?,
        carbGrams: Double = MIN_CARBS_GRAMS,
    ): Double =
        if (stored != null && !stored.isNaN()) clampProteinMultiplier(stored)
        else autoProteinMultiplier(goalWeight, tdee, sex, objective, carbGrams)

    fun maxDeficit(tdee: Double, proteinGrams: Double, sex: String, carbGrams: Double = MIN_CARBS_GRAMS): Double {
        val d = floorCalories(proteinGrams, sex, carbGrams) - tdee
        return if (d > MAX_DEFICIT_CAP) d else MAX_DEFICIT_CAP
    }

    /** Returns carbs and fats in grams. */
    fun splitMacros(
        baseCaloricNeed: Double,
        protein: Double,
        balanceOfCalories: String,
        sex: String,
        minCarbsGrams: Double = MIN_CARBS_GRAMS,
    ): Pair<Double, Double> {
        val minFats = minFatsGrams(sex)
        val remaining = baseCaloricNeed - protein * 4
        val (carbCal, fatCal) = when (balanceOfCalories) {
            "Carbs" -> remaining * 0.6 to remaining * 0.4
            "Fats" -> remaining * 0.4 to remaining * 0.6
            else -> remaining * 0.5 to remaining * 0.5
        }
        var carbs = carbCal / 4.0
        var fats = fatCal / 9.0
        if (carbs < minCarbsGrams) {
            val carbDeficit = (minCarbsGrams - carbs) * 4.0
            fats = max(minFats, (fatCal - carbDeficit) / 9.0)
            carbs = minCarbsGrams
        } else if (fats < minFats) {
            val fatDeficit = (minFats - fats) * 9.0
            carbs = max(minCarbsGrams, (carbCal - fatDeficit) / 4.0)
            fats = minFats
        }
        return jsRound(carbs) to jsRound(fats)
    }

    fun caloriesFromMacros(protein: Double, carbs: Double, fats: Double): Double =
        jsRound(protein * 4 + carbs * 4 + fats * 9)

    /** Everything the Macros tab and the daily goals need. Null if the profile is incomplete. */
    fun calculate(input: MacroInput, today: LocalDate = LocalDate.now()): MacroResult? {
        val birthday = input.birthday ?: return null
        val sex = input.sex ?: return null
        val feet = input.feet?.takeIf { it != 0 } ?: return null
        val inches = input.inches?.takeIf { it != 0 } ?: return null
        val current = input.currentWeight?.takeIf { it != 0.0 } ?: return null
        val goal = input.goalWeight?.takeIf { it != 0.0 } ?: return null
        val activity = input.activityLevel ?: return null

        val age = age(birthday, today)
        val bmr = bmr(current, goal, sex, feet, inches, age)
        val tdee = tdee(bmr, activity)
        val objective = input.objective ?: "Lose Fat"
        val carbMultiplier = effectiveCarbMultiplier(input.carbMultiplier)
        val minCarbs = planningCarbGrams(carbMultiplier)
        val proteinMultiplier = effectiveProteinMultiplier(goal, tdee, sex, input.objective, input.proteinMultiplier, minCarbs)
        val protein = proteinGrams(goal, proteinMultiplier)
        val maxDeficit = maxDeficit(tdee, protein, sex, minCarbs)
        val objectiveDelta = when (objective) {
            "Lose Fat" -> maxDeficit
            "Maintain" -> 0.0
            else -> 500.0
        }
        val base = tdee + objectiveDelta
        val (carbs, fats) = splitMacros(base, protein, input.balanceOfCalories ?: "Both", sex, minCarbs)
        val total = protein * 4 + carbs * 4 + fats * 9
        return MacroResult(
            age = age,
            bmr = bmr,
            tdee = tdee,
            maxDeficit = maxDeficit,
            minimumCaloricNeed = tdee + maxDeficit,
            baseCaloricNeedAdjusted = base,
            calcTotalCalories = total,
            protein = protein,
            carbs = carbs,
            fats = fats,
            proteinMultiplier = proteinMultiplier,
            carbMultiplier = carbMultiplier,
            minCarbs = minCarbs,
            isProteinAutoReduced = input.proteinMultiplier == null && proteinMultiplier < MAX_PROTEIN_MULTIPLIER,
            calorieDeltaVsTdee = jsRound(total) - tdee,
            isLoseFatGoalAtOrAboveTdee = objective == "Lose Fat" && jsRound(total) >= tdee,
        )
    }
}

data class MacroInput(
    val birthday: LocalDate?,
    val sex: String?,
    val feet: Int?,
    val inches: Int?,
    val currentWeight: Double?,
    val goalWeight: Double?,
    val activityLevel: String?,
    val objective: String?,
    val proteinMultiplier: Double?,
    val carbMultiplier: Double?,
    val balanceOfCalories: String?,
)

data class MacroResult(
    val age: Int,
    val bmr: Double,
    val tdee: Double,
    val maxDeficit: Double,
    val minimumCaloricNeed: Double,
    val baseCaloricNeedAdjusted: Double,
    val calcTotalCalories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
    val proteinMultiplier: Double,
    val carbMultiplier: Double,
    val minCarbs: Double,
    val isProteinAutoReduced: Boolean,
    val calorieDeltaVsTdee: Double,
    val isLoseFatGoalAtOrAboveTdee: Boolean,
) {
    /** The daily goals written to DaysMeals (liveGoalsFromCalc on the web). */
    val goals: DailyGoals
        get() = DailyGoals(
            calories = MacroMath.jsRound(calcTotalCalories),
            protein = protein,
            carbs = carbs,
            fats = fats,
            fiber = MacroMath.DEFAULT_FIBER_GOAL,
        )
}

data class DailyGoals(val calories: Double, val protein: Double, val carbs: Double, val fats: Double, val fiber: Double)

