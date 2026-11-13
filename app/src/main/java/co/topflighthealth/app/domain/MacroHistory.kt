package co.topflighthealth.app.domain

import kotlinx.serialization.Serializable

/**
 * One plan snapshot on the user (`macroHistory`), so past days keep the goals
 * they had. Ported from the web app's macroHistory.ts and iOS User.swift.
 * `effectiveFrom` is a local `yyyy-MM-dd`.
 */
@Serializable
data class MacroHistoryEntry(
    val effectiveFrom: String,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
    val fiber: Double,
    val objective: String? = null,
    val activityLevel: String? = null,
    val goalWeight: Double? = null,
    val proteinMultiplier: Double? = null,
    val carbMultiplier: Double? = null,
    val balanceOfCalories: String? = null,
)

/** The user fields that change the plan. Editing any of them writes a new snapshot. */
data class PlanPolicy(
    val objective: String?,
    val activityLevel: String?,
    val goalWeight: Double?,
    val balanceOfCalories: String?,
    val proteinMultiplier: Double?,
    val carbMultiplier: Double?,
)

object MacroHistory {
    private fun dayKey(value: String) = value.take(10)

    fun snapshot(calc: MacroResult, policy: PlanPolicy, effectiveFrom: String) = MacroHistoryEntry(
        effectiveFrom = effectiveFrom,
        protein = calc.protein,
        carbs = calc.carbs,
        fats = calc.fats,
        fiber = MacroMath.DEFAULT_FIBER_GOAL,
        objective = policy.objective,
        activityLevel = policy.activityLevel,
        goalWeight = policy.goalWeight,
        proteinMultiplier = policy.proteinMultiplier,
        carbMultiplier = policy.carbMultiplier,
        balanceOfCalories = policy.balanceOfCalories,
    )

    private fun gramsMatch(a: MacroHistoryEntry, b: MacroHistoryEntry) =
        MacroMath.jsRound(a.protein) == MacroMath.jsRound(b.protein) &&
            MacroMath.jsRound(a.carbs) == MacroMath.jsRound(b.carbs) &&
            MacroMath.jsRound(a.fats) == MacroMath.jsRound(b.fats) &&
            MacroMath.jsRound(a.fiber) == MacroMath.jsRound(b.fiber)

    private fun equivalent(a: MacroHistoryEntry, b: MacroHistoryEntry) =
        gramsMatch(a, b) && a.objective == b.objective && a.activityLevel == b.activityLevel &&
            a.goalWeight == b.goalWeight && a.proteinMultiplier == b.proteinMultiplier &&
            a.carbMultiplier == b.carbMultiplier && a.balanceOfCalories == b.balanceOfCalories

    /** One row per local day; a same-day write replaces that day's row. */
    fun upsert(history: List<MacroHistoryEntry>, next: MacroHistoryEntry): List<MacroHistoryEntry> {
        val day = dayKey(next.effectiveFrom)
        val sameDay = history.indices.filter { dayKey(history[it].effectiveFrom) == day }
        if (sameDay.isNotEmpty()) {
            val keep = sameDay.last()
            return history.mapIndexedNotNull { i, row ->
                when {
                    i == keep -> next
                    i in sameDay -> null
                    else -> row
                }
            }
        }
        val last = history.lastOrNull()
        if (last != null && equivalent(last, next)) return history
        return history + next
    }

    /**
     * The history to save with a profile edit (applyHistoryOnSave on the web).
     * Seeds the first row from the plan before the edit, dated when the account
     * was created, then adds today's row if the plan changed.
     */
    fun applyOnSave(
        history: List<MacroHistoryEntry>,
        previous: PlanPolicy,
        next: PlanPolicy,
        previousCalc: MacroResult?,
        nextCalc: MacroResult?,
        createdDay: String,
        today: String,
    ): List<MacroHistoryEntry> {
        var rows = history
        val policyChanged = previous != next
        if (rows.isEmpty()) {
            val seedCalc = if (policyChanged && previousCalc != null) previousCalc else nextCalc
            val seedPolicy = if (policyChanged && previousCalc != null) previous else next
            if (seedCalc != null) rows = listOf(snapshot(seedCalc, seedPolicy, createdDay))
        }
        if (policyChanged && nextCalc != null) rows = upsert(rows, snapshot(nextCalc, next, today))
        return rows
    }
}
