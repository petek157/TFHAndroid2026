package co.topflighthealth.app.api

import co.topflighthealth.app.domain.MacroNutrition
import co.topflighthealth.app.domain.UnitConverter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A food in a pantry (iOS FoodItem). Macros are per one serving of
 * [servingAmount] [servingUnit]. [userObjectId] null = the shared TFH pantry.
 */
@Serializable
data class FoodItem(
    @SerialName("_id") val id: String,
    val name: String,
    val servingAmount: Double,
    val servingUnit: String,
    val image: String? = null,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val fiber: Double = 0.0,
    val userObjectId: String? = null,
    val upcCode: String? = null,
    val usdaFdcId: Int? = null,
    /** manual, usda, user_created or openfoodfacts. */
    val dataSource: String = "manual",
    /** Household measure equal to one serving, e.g. 1 cup(s) or 17 piece(s). */
    val commonAmount: Double? = null,
    val commonUnit: String? = null,
) {
    /**
     * Macros for an amount in a unit (iOS FoodItem.nutrition(for:unit:)). A
     * household unit that differs from the serving unit is turned into a
     * serving count first, so 17 piece(s) of a 100 g serving isn't 17 servings.
     */
    fun nutritionFor(amount: Double, unit: String): MacroNutrition {
        val u = unit.lowercase()
        val ca = commonAmount
        val (amt, un) = if (commonUnit != null && u == commonUnit.lowercase() && u != servingUnit.lowercase() && ca != null && ca > 0) {
            amount / ca to "servings"
        } else amount to unit
        val r = UnitConverter.scalingRatio(amt, un, servingAmount, servingUnit)
        return MacroNutrition(protein * r, carbs * r, fats * r, fiber * r)
    }

    /** Units offered when choosing an amount (iOS AmountSelectionView.availableUnits). */
    val availableUnits: List<String>
        get() {
            val base = servingUnit.lowercase()
            val units = mutableListOf<String>()
            if (!commonUnit.isNullOrEmpty()) units += commonUnit
            if (base == "g" || base == "ml") units += base
            units += "servings"
            if (base == "g") units += listOf("oz", "lb")
            if (base == "ml") units += "fl oz"
            return units.distinct()
        }
}

/** A USDA search hit (iOS USDAFood). Nutrient ids: 1008 energy, 1003 protein, 1005 carbs, 1004 fat, 1079 fiber. */
@Serializable
data class USDAFood(
    val fdcId: Int,
    val description: String,
    val dataType: String = "",
    val gtinUpc: String? = null,
    val brandOwner: String? = null,
    val brandName: String? = null,
    val servingSize: Double? = null,
    val servingSizeUnit: String? = null,
    val householdServingFullText: String? = null,
    val foodNutrients: List<USDANutrient> = emptyList(),
) {
    private fun n(id: Int) = foodNutrients.firstOrNull { it.nutrientId == id }?.value ?: 0.0
    val calories get() = n(1008)
    val protein get() = n(1003)
    val carbs get() = n(1005)
    val fats get() = n(1004)
    val fiber get() = n(1079)

    fun toFoodItem(userObjectId: String): FoodItem = FoodItem(
        id = java.util.UUID.randomUUID().toString(),
        name = description,
        servingAmount = servingSize ?: 100.0,
        servingUnit = if (servingSizeUnit?.lowercase()?.contains("ml") == true) "ml" else "g",
        calories = Math.rint(calories), protein = Math.rint(protein), carbs = Math.rint(carbs),
        fats = Math.rint(fats), fiber = Math.rint(fiber),
        userObjectId = userObjectId, upcCode = gtinUpc, usdaFdcId = fdcId, dataSource = "usda",
    )
}

@Serializable
data class USDANutrient(val nutrientId: Int, val nutrientName: String = "", val unitName: String = "", val value: Double = 0.0)

@Serializable
data class USDAFoodPortion(val portionDescription: String, val gramWeight: Double, val modifier: String? = null, val measureUnit: String? = null)

@Serializable
data class USDALabelNutrients(
    val fat: Double? = null, val carbohydrates: Double? = null, val fiber: Double? = null,
    val protein: Double? = null, val calories: Double? = null,
)

/** Full USDA record (iOS USDAFoodDetail); per-100 g macros plus label or portion data. */
@Serializable
data class USDAFoodDetail(
    val fdcId: Int,
    val name: String,
    val description: String = "",
    val gtinUpc: String? = null,
    val servingSize: Double? = null,
    val servingSizeUnit: String? = null,
    val householdServingFullText: String? = null,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val fiber: Double = 0.0,
    val foodPortions: List<USDAFoodPortion>? = null,
    val labelNutrients: USDALabelNutrients? = null,
) {
    /** iOS USDAFoodDetail.toFoodItem: label nutrients when branded, else the first portion, else per 100 g. */
    fun toFoodItem(userObjectId: String): FoodItem {
        var amount = servingSize ?: 100.0
        var unit = if (servingSizeUnit?.lowercase()?.contains("ml") == true) "ml" else "g"
        var cal = calories; var pro = protein; var carb = carbs; var fat = fats; var fib = fiber
        var commonAmount: Double? = null; var commonUnit: String? = null
        val ln = labelNutrients
        val portions = foodPortions.orEmpty()
        if (ln?.calories != null) {
            unit = servingSizeUnit ?: "g"
            cal = ln.calories; pro = ln.protein ?: protein; carb = ln.carbohydrates ?: carbs
            fat = ln.fat ?: fats; fib = ln.fiber ?: fiber
            householdServingFullText?.lowercase()?.trim()?.let { text ->
                val units = listOf("cup", "tbsp", "tsp", "oz", "piece", "slice", "scoop", "fl oz", "serving", "bar", "packet", "bottle", "can")
                units.firstOrNull { text.contains(it) }?.let { u ->
                    commonUnit = u
                    Regex("^[\\d\\s./]+").find(text)?.value?.trim()?.let { num ->
                        commonAmount = if (num.contains("/")) {
                            val parts = num.split("/").mapNotNull { it.trim().toDoubleOrNull() }
                            if (parts.size == 2 && parts[1] != 0.0) parts[0] / parts[1] else null
                        } else num.toDoubleOrNull()
                    }
                }
            }
        } else if (portions.isNotEmpty()) {
            val p = portions[0]
            amount = p.gramWeight; unit = "g"
            val f = p.gramWeight / 100.0
            cal = Math.rint(calories * f); pro = Math.rint(protein * f); carb = Math.rint(carbs * f)
            fat = Math.rint(fats * f); fib = Math.rint(fiber * f)
            commonAmount = 1.0; commonUnit = p.portionDescription
        }
        return FoodItem(
            id = java.util.UUID.randomUUID().toString(), name = name, servingAmount = amount, servingUnit = unit,
            calories = Math.rint(cal), protein = Math.rint(pro), carbs = Math.rint(carb), fats = Math.rint(fat), fiber = Math.rint(fib),
            userObjectId = userObjectId, upcCode = gtinUpc, usdaFdcId = fdcId, dataSource = "usda",
            commonAmount = commonAmount, commonUnit = commonUnit,
        )
    }
}

/** POST daysmeals/quick-meal: the server get-or-creates the day and adds the meal in one call. */
@Serializable
data class QuickMealRequest(val date: String, val userObjectId: String, val goals: QuickGoals, val meal: Meal)

@Serializable
data class QuickGoals(val goalCalories: Double, val goalProtein: Double, val goalCarbs: Double, val goalFats: Double, val goalFiber: Double)
