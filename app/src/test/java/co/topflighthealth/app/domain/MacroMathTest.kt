package co.topflighthealth.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Same cases as TFHReact2024/packages/frontend/src/utils/macroCalculations.test.ts. */
class MacroMathTest {
    private val today = LocalDate.of(2026, 11, 13)

    private fun input(
        age: Long, sex: String, feet: Int, inches: Int, current: Double, goal: Double, activity: String,
        objective: String?, protein: Double? = null, carb: Double? = null,
    ) = MacroInput(today.minusYears(age), sex, feet, inches, current, goal, activity, objective, protein, carb, "Both")

    @Test fun floorAtOneGramForA195lbFemaleIs1660() {
        assertEquals(1660.0, MacroMath.floorCalories(195.0, "Female"), 0.0)
    }

    @Test fun staysAtOneWhenFloorBelowTdee() {
        assertEquals(1.0, MacroMath.autoProteinMultiplier(195.0, 2000.0, "Female", "Lose Fat"), 0.0)
    }

    @Test fun dropsToPoint7WhenOneWouldSurplus() {
        val m = MacroMath.autoProteinMultiplier(195.0, 1600.0, "Female", "Lose Fat")
        assertEquals(0.7, m, 0.0)
        val protein = MacroMath.proteinGrams(195.0, m)
        assertEquals(137.0, protein, 0.0)
        val floor = MacroMath.floorCalories(protein, "Female")
        assertTrue(floor < 1600)
        assertTrue(1600 - floor <= 500)
    }

    @Test fun clampsAtPoint7WhenStillSurplus() {
        assertEquals(0.7, MacroMath.autoProteinMultiplier(195.0, 1400.0, "Female", "Lose Fat"), 0.0)
        assertTrue(MacroMath.floorCalories(MacroMath.proteinGrams(195.0, 0.7), "Female") >= 1400)
    }

    @Test fun neverExceedsA500kcalDeficit() {
        assertEquals(0.75, MacroMath.autoProteinMultiplier(500.0, 2880.0, "Female", "Lose Fat"), 0.0)
    }

    @Test fun doesNotAutoCutMaintainOrBuild() {
        assertEquals(1.0, MacroMath.autoProteinMultiplier(195.0, 1400.0, "Female", "Maintain"), 0.0)
        assertEquals(1.0, MacroMath.autoProteinMultiplier(195.0, 1400.0, "Female", "Build"), 0.0)
    }

    @Test fun coachOverrideIsKept() {
        val r = MacroMath.calculate(input(70, "Female", 4, 11, 208.0, 195.0, "Sedentary", "Lose Fat", protein = 1.0), today)
        assertNotNull(r)
        assertEquals(1.0, r!!.proteinMultiplier, 0.0)
        assertEquals(195.0, r.protein, 0.0)
        assertFalse(r.isProteinAutoReduced)
    }

    @Test fun unmanagedLoseFatClientAutoCutsBelowTdee() {
        val r = MacroMath.calculate(input(70, "Female", 4, 11, 208.0, 195.0, "Sedentary", "Lose Fat"), today)!!
        assertEquals(0.7, r.proteinMultiplier, 0.0)
        assertTrue(r.isProteinAutoReduced)
        assertTrue(MacroMath.jsRound(r.calcTotalCalories) < r.tdee)
        assertFalse(r.isLoseFatGoalAtOrAboveTdee)
    }

    @Test fun coachCarbOverrideLowersPlanningMin() {
        val r = MacroMath.calculate(input(35, "Female", 5, 7, 160.0, 145.0, "Active", "Lose Fat", carb = 0.85), today)!!
        assertEquals(0.85, r.carbMultiplier, 0.0)
        assertEquals(MacroMath.jsRound(130 * 0.85), r.minCarbs, 0.0)
    }

    @Test fun incompleteProfileGivesNoResult() {
        val r = MacroMath.calculate(input(35, "Female", 5, 7, 160.0, 145.0, "Active", "Lose Fat").copy(birthday = null), today)
        assertEquals(null, r)
    }
}
