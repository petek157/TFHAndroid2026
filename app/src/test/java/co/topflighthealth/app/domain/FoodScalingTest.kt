package co.topflighthealth.app.domain

import co.topflighthealth.app.api.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ports of iOS UnitConverterTests and FoodItemScalingTests. */
class FoodScalingTest {
    private fun food(amount: Double, unit: String, p: Double, c: Double, f: Double, ca: Double?, cu: String?) =
        FoodItem("x", "x", amount, unit, protein = p, carbs = c, fats = f, commonAmount = ca, commonUnit = cu)

    private val egg = food(1.0, "piece(s)", 5.0, 0.0, 5.0, 1.0, "piece(s)")
    private val pepperoni = food(100.0, "g", 4.0, 1.0, 7.0, 17.0, "piece(s)")
    private val yogurt = food(227.0, "g", 17.0, 9.0, 4.0, 1.0, "cup(s)")

    @Test fun servingsToBase() = assertEquals(75.0, UnitConverter.toBaseUnit(0.5, "servings", 150.0, "g"), 1e-9)
    @Test fun gramsToBase() = assertEquals(200.0, UnitConverter.toBaseUnit(200.0, "g", 100.0, "g"), 1e-9)
    @Test fun mlToBase() = assertEquals(30.0, UnitConverter.toBaseUnit(30.0, "ml", 15.0, "ml"), 1e-9)
    @Test fun ouncesToGrams() = assertEquals(3.5 * 28.3495, UnitConverter.toBaseUnit(3.5, "oz", 100.0, "g"), 1e-9)
    @Test fun poundsToGrams() = assertEquals(453.592, UnitConverter.toBaseUnit(1.0, "lb", 100.0, "g"), 1e-9)
    @Test fun unknownUnitIsServings() = assertEquals(100.0, UnitConverter.toBaseUnit(1.0, "handful", 100.0, "g"), 1e-9)
    @Test fun ratioOne() = assertEquals(1.0, UnitConverter.scalingRatio(100.0, "g", 100.0, "g"), 1e-9)
    @Test fun ratioTwo() = assertEquals(2.0, UnitConverter.scalingRatio(2.0, "servings", 100.0, "g"), 1e-9)
    @Test fun ratioHalf() = assertEquals(0.5, UnitConverter.scalingRatio(0.5, "servings", 100.0, "g"), 1e-9)
    @Test fun gramsToOuncesRatio() = assertEquals(99.22325 / 100, UnitConverter.scalingRatio(3.5, "oz", 100.0, "g"), 1e-9)
    @Test fun zeroBaseIsZero() = assertEquals(0.0, UnitConverter.scalingRatio(1.0, "g", 0.0, "g"), 0.0)

    @Test fun eggTwoPieces() {
        val n = egg.nutritionFor(2.0, "piece(s)")
        assertEquals(130.0, n.calories, 1e-9); assertEquals(10.0, n.protein, 1e-9); assertEquals(10.0, n.fats, 1e-9)
    }
    @Test fun pepperoniSeventeenPieces() {
        val n = pepperoni.nutritionFor(17.0, "piece(s)")
        assertEquals(83.0, n.calories, 1e-9); assertEquals(4.0, n.protein, 1e-9); assertEquals(1.0, n.carbs, 1e-9); assertEquals(7.0, n.fats, 1e-9)
    }
    @Test fun pepperoniThirtyFourDoubles() = assertEquals(166.0, pepperoni.nutritionFor(34.0, "piece(s)").calories, 1e-9)
    @Test fun pepperoniGrams() = assertEquals(83.0, pepperoni.nutritionFor(100.0, "g").calories, 1e-9)
    @Test fun yogurtOneCup() {
        val n = yogurt.nutritionFor(1.0, "cup(s)")
        assertEquals(140.0, n.calories, 1e-9); assertEquals(17.0, n.protein, 1e-9)
    }
    @Test fun yogurtHalfCup() {
        val n = yogurt.nutritionFor(0.5, "cup(s)")
        assertEquals(70.0, n.calories, 1e-9); assertEquals(8.5, n.protein, 1e-9)
    }
    @Test fun chicken200g() {
        val n = food(100.0, "g", 30.0, 0.0, 5.0, null, null).nutritionFor(200.0, "g")
        assertEquals(330.0, n.calories, 1e-9); assertEquals(60.0, n.protein, 1e-9)
    }
    @Test fun portionKeepsQuarter() {
        assertEquals("0.25", portionString(0.25)); assertEquals("0.75", portionString(0.75)); assertEquals("1", portionString(1.0)); assertEquals("1.5", portionString(1.50))
    }
    @Test fun availableUnitsForGrams() = assertEquals(listOf("piece(s)", "g", "servings", "oz", "lb"), pepperoni.availableUnits)
}
