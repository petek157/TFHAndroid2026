package co.topflighthealth.app.domain

import co.topflighthealth.app.api.DaysMeals
import co.topflighthealth.app.api.Meal
import co.topflighthealth.app.api.MealItem
import java.time.LocalDate
import java.time.ZoneId

/** Same fixtures as iOS NutritionEngineTestFixtures. */
object Fixtures {
    fun iso(day: LocalDate): String = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

    fun item(id: String, p: Double, c: Double, f: Double, fiber: Double = 0.0) =
        MealItem(id = id, name = id, amount = 1.0, servingAmount = 1.0, servingUnit = "serving", unit = "servings",
            calories = p * 4 + c * 4 + f * 9, protein = p, carbs = c, fats = f, fiber = fiber)

    val chicken = item("mi-chicken", 30.0, 0.0, 5.0)
    val riceHalf = item("mi-rice", 2.0, 16.0, 0.5, 1.5)
    val oil = item("mi-oil", 0.0, 0.0, 13.333)

    fun meal(id: String, day: LocalDate, vararg items: MealItem) = Meal(id = id, date = iso(day), mealItems = items.toList())

    val today: LocalDate = LocalDate.now()
    val breakfast = meal("meal-breakfast", today, chicken, oil)
    val lunch = meal("meal-lunch", today, chicken, riceHalf)

    val dayWithTwoMeals = DaysMeals(
        id = "dm-two", date = iso(today), userObjectId = "user-1", meals = listOf(breakfast, lunch),
        goalCalories = 1570.0, goalProtein = 140.0, goalCarbs = 123.0, goalFats = 56.0, goalFiber = 30.0,
    )
}
