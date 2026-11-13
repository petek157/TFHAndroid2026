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
