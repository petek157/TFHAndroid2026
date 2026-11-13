package co.topflighthealth.app.api

import co.topflighthealth.app.domain.MacroNutrition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day of meals and that day's goals (iOS DaysMeals). Calories are always derived from macros. */
@Serializable
data class DaysMeals(
    @SerialName("_id") val id: String,
    /** ISO instant of the day's local midnight when it was created. */
    val date: String,
    val userObjectId: String = "",
    val meals: List<Meal> = emptyList(),
    val goalCalories: Double = 0.0,
    val goalProtein: Double = 0.0,
    val goalCarbs: Double = 0.0,
    val goalFats: Double = 0.0,
    val goalFiber: Double = 0.0,
    val numberOfMeals: Int? = null,
    val waterAmount: Double? = null,
) {
    val day: LocalDate get() = Instant.parse(date).atZone(ZoneId.systemDefault()).toLocalDate()
    val dayNutrition: MacroNutrition get() = MacroNutrition.sum(meals.map { it.nutrition })
    val effectiveWaterAmount: Double get() = waterAmount ?: 0.0
}

@Serializable
data class Meal(
    @SerialName("_id") val id: String,
    val date: String,
    val mealItems: List<MealItem> = emptyList(),
    val fullImageUrl: String? = null,
    val thumbImageUrl: String? = null,
    val name: String? = null,
    val description: String? = null,
    val notes: String? = null,
) {
    val nutrition: MacroNutrition get() = MacroNutrition.sum(mealItems.map { it.nutrition })
    val instant: Instant? get() = runCatching { Instant.parse(date) }.getOrNull()
}

@Serializable
data class MealItem(
    @SerialName("_id") val id: String = "",
    val type: String = "food",
    val name: String = "",
    val amount: Double = 0.0,
    val servingAmount: Double = 0.0,
    val servingUnit: String = "",
    val unit: String = "",
    /** Kept for the API only. Never use it for math; see [nutrition]. */
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val fiber: Double = 0.0,
    val image: String? = null,
    val sourceFoodItemId: String? = null,
) {
    /** Macros for the consumed amount (already scaled by the server/app). */
    val nutrition: MacroNutrition get() = MacroNutrition(protein, carbs, fats, fiber)
}

@Serializable
data class WaterUpdate(val waterAmount: Double)
