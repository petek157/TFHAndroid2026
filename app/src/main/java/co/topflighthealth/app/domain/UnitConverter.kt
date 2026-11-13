package co.topflighthealth.app.domain

/**
 * Converts consumed amounts to the food's base unit (iOS UnitConverter):
 * grams for solids, millilitres for liquids. Unknown units count as servings.
 */
object UnitConverter {
    fun toBaseUnit(amount: Double, unit: String, baseAmount: Double, baseUnit: String): Double =
        when (unit.lowercase().trim()) {
            "servings", "serving" -> amount * baseAmount
            "g", "grams" -> amount
            "ml", "milliliters", "millilitres" -> amount
            "oz", "ounces" -> amount * 28.3495
            "lb", "lbs", "pounds" -> amount * 453.592
            else -> amount * baseAmount
        }

    fun scalingRatio(amount: Double, unit: String, baseAmount: Double, baseUnit: String): Double {
        if (baseAmount <= 0) return 0.0
        return toBaseUnit(amount, unit, baseAmount, baseUnit) / baseAmount
    }

    /** Same physical amount in another unit, e.g. 30 g -> 1.06 oz. */
    fun convert(amount: Double, fromUnit: String, toUnit: String, baseAmount: Double, baseUnit: String): Double {
        if (amount <= 0) return amount
        val inBase = toBaseUnit(amount, fromUnit, baseAmount, baseUnit)
        return when (toUnit.lowercase().trim()) {
            "servings", "serving" -> inBase / baseAmount
            "g", "grams", "ml", "milliliters", "millilitres" -> inBase
            "oz", "ounces" -> inBase / 28.3495
            "lb", "lbs", "pounds" -> inBase / 453.592
            else -> inBase / baseAmount
        }
    }
}

/** Up to two decimals, trailing zeros dropped: 1, 0.5, 0.25 (iOS PortionAmountFormat). */
fun portionString(v: Double): String {
    val r = Math.round(v * 100) / 100.0
    var s = String.format(java.util.Locale.US, "%.2f", r)
    if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
    return s
}
