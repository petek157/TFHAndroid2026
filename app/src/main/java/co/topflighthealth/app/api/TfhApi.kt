package co.topflighthealth.app.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * The TFH API (TFHReact2024, SST). Paths are relative to API_BASE_URL in
 * app/build.gradle.kts, the same as the iOS APIClient's endpoints.
 */
interface TfhApi {
    /** The signed-in user's own record. */
    @GET("users")
    suspend fun me(): UserDto?

    /** Live membership state; the single source of truth for gating, as on iOS. */
    @GET("users/subscription-status")
    suspend fun subscriptionStatus(): SubscriptionStatus
}

/** Only the fields phase 1 needs. More get added as screens arrive. */
@Serializable
data class UserDto(
    @SerialName("_id") val id: String? = null,
    val email: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val role: String? = null,
    val subscription: String? = null,
) {
    val displayName: String
        get() = listOfNotNull(firstname, lastname).joinToString(" ").ifBlank { email.orEmpty() }
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
