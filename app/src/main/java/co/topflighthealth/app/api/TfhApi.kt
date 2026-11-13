package co.topflighthealth.app.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The TFH API (TFHReact2024, SST; routes in infra/api.ts). Paths are relative
 * to API_BASE_URL in app/build.gradle.kts, the same endpoints the iOS APIClient uses.
 *
 * The user record is passed around as raw JSON (see data/Profile.kt) so fields
 * the Android app doesn't know about are never dropped.
 */
interface TfhApi {
    /** The signed-in user's own record, or JSON null if the account is gone. */
    @GET("users")
    suspend fun me(): JsonElement

    @GET("users/{id}")
    suspend fun user(@Path("id") id: String): JsonElement

    /**
     * Saves profile fields. The server applies them with Mongo `$set`, so only
     * the fields sent change. It also resets `birthday` to today when it's
     * missing, so always send the current birthday.
     */
    @PUT("users/{id}")
    suspend fun updateUser(@Path("id") id: String, @Body body: JsonObject): JsonElement

    /** Live membership state; the single source of truth for gating, as on iOS. */
    @GET("users/subscription-status")
    suspend fun subscriptionStatus(): SubscriptionStatus

    @GET("weight-entries/user/{userId}")
    suspend fun weightEntries(@Path("userId") userId: String): List<WeightEntry>

    /** The server also updates the user's currentWeight when this is the newest entry. */
    @POST("weight-entries")
    suspend fun addWeightEntry(@Body entry: NewWeightEntry): JsonElement

    @DELETE("weight-entries/{id}")
    suspend fun deleteWeightEntry(@Path("id") id: String): JsonElement

    /** One day's meals, or JSON null if the day has no document yet. Range is that local day, as ISO instants. */
    @GET("daysmeals")
    suspend fun daysMeals(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("userObjectId") userObjectId: String,
    ): JsonElement

    /** Every day document in [startDate, endDate). */
    @GET("daysmeals/range")
    suspend fun daysMealsRange(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("userObjectId") userObjectId: String,
    ): List<DaysMeals>

    @POST("daysmeals")
    suspend fun createDaysMeals(@Body body: NewDaysMeals): DaysMeals

    @PUT("daysmeals/{id}/water")
    suspend fun setWater(@Path("id") id: String, @Body body: WaterUpdate): JsonElement

    @GET("notes/user/{userId}")
    suspend fun notes(@Path("userId") userId: String): List<Note>

    @POST("notes")
    suspend fun addNote(@Body note: NewNote): JsonElement

    @PUT("notes/{id}")
    suspend fun updateNote(@Path("id") id: String, @Body note: Note): JsonElement

    @DELETE("notes/{id}")
    suspend fun deleteNote(@Path("id") id: String): JsonElement

    @GET("reminder-occurrences/user/{userId}")
    suspend fun reminderOccurrences(@Path("userId") userId: String, @Query("status") status: String = "pending"): List<ReminderOccurrence>

    @POST("reminder-occurrences/{id}/complete")
    suspend fun completeReminder(@Path("id") id: String): JsonElement

    @GET("fooditems/user/{userId}")
    suspend fun userFoodItems(@Path("userId") userId: String): List<FoodItem>

    /** The shared TFH pantry. */
    @GET("fooditems/system")
    suspend fun systemFoodItems(): List<FoodItem>

    @POST("fooditems")
    suspend fun createFoodItem(@Body item: FoodItem): JsonElement

    @GET("usda/search")
    suspend fun usdaSearch(@Query("query") query: String): List<USDAFood>

    @GET("usda/detail/{fdcId}")
    suspend fun usdaDetail(@Path("fdcId") fdcId: Int): USDAFoodDetail

    @POST("daysmeals/quick-meal")
    suspend fun quickMeal(@Body body: QuickMealRequest): DaysMeals

    @PUT("daysmeals/{dayId}/meals/{mealId}")
    suspend fun updateMeal(@Path("dayId") dayId: String, @Path("mealId") mealId: String, @Body meal: Meal): JsonElement

    @DELETE("daysmeals/{dayId}/meals/{mealId}")
    suspend fun deleteMeal(@Path("dayId") dayId: String, @Path("mealId") mealId: String): JsonElement

    /** Most recent day documents before [beforeDate]. */
    @GET("daysmeals/recent")
    suspend fun recentDaysMeals(
        @Query("beforeDate") beforeDate: String,
        @Query("limit") limit: Int,
        @Query("userObjectId") userObjectId: String,
    ): List<DaysMeals>

    /** Rewrites today's and future DaysMeals goals after the plan changes, as iOS and web do. */
    @PUT("daysMeals/batch-update-goals")
    suspend fun updateDaysMealsGoals(@Body body: GoalsUpdate): JsonElement
}

@Serializable
data class SubscriptionStatus(
    val subscription: String = "free",
    val membershipActive: Boolean = false,
    val expirationDate: String? = null,
    val scheduledMembershipEnd: String? = null,
    val subscriptionSource: String? = null,
) {
    /** Same rule as SubscriptionManager.checkServerStatus on iOS. */
    val isMember: Boolean
        get() = membershipActive && (subscription == "basic" || subscription == "pro")
}

@Serializable
data class WeightEntry(
    @kotlinx.serialization.SerialName("_id") val id: String,
    val userObjectId: String = "",
    /** ISO 8601 instant. */
    val date: String,
    val weight: Double,
)

@Serializable
data class NewWeightEntry(val userObjectId: String, val date: String, val weight: Double)

@Serializable
data class GoalsUpdate(
    val userObjectId: String,
    /** The client's local midnight today, as an ISO instant. Days from here on are updated. */
    val startDate: String,
    val goalCalories: Double,
    val goalProtein: Double,
    val goalCarbs: Double,
    val goalFats: Double,
    val goalFiber: Double,
)

@Serializable
data class NewDaysMeals(
    val date: String,
    val userObjectId: String,
    val meals: List<Meal> = emptyList(),
    val goalCalories: Double,
    val goalProtein: Double,
    val goalCarbs: Double,
    val goalFats: Double,
    val goalFiber: Double,
)

/** A dashboard note (iOS Note). Categories match NoteCategory raw values. */
@Serializable
data class Note(
    @kotlinx.serialization.SerialName("_id") val id: String,
    val userObjectId: String,
    val date: String,
    val text: String,
    val category: String = "general",
)

@Serializable
data class NewNote(val userObjectId: String, val date: String, val text: String, val category: String)

@Serializable
data class ReminderOccurrence(
    @kotlinx.serialization.SerialName("_id") val id: String,
    val reminderId: String = "",
    val userObjectId: String = "",
    val scheduledFor: String,
    val status: String = "pending",
    val title: String = "",
    val body: String = "",
)
