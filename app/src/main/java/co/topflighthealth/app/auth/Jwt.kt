package co.topflighthealth.app.auth

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the app reads out of the access token. Mirrors CustomClaims and
 * CustomProperties in the iOS AuthManager. The token is only read here, never
 * trusted for security: the API checks it on every call.
 */
@Serializable
data class TokenClaims(
    val exp: Long? = null,
    val properties: TokenProperties? = null,
)

@Serializable
data class TokenProperties(
    val userID: String? = null,
    val role: String? = null,
    val subscription: String? = null,
    val membership: TokenMembership? = null,
)

@Serializable
data class TokenMembership(
    val tierCode: String? = null,
    val features: List<String>? = null,
)

object Jwt {
    private val json = Json { ignoreUnknownKeys = true }

    fun claims(token: String): TokenClaims? = try {
        val payload = token.split(".")[1]
        val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        json.decodeFromString(TokenClaims.serializer(), String(bytes, Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }
}
