package co.topflighthealth.app.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import co.topflighthealth.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sign-in, token refresh and sign-out against the TFH auth server (OpenAuth),
 * using the same browser + PKCE flow as the iOS app's AuthManager.
 */
class AuthRepository(context: Context, private val store: TokenStore) {

    private val service = AuthorizationService(context)
    private val config = AuthorizationServiceConfiguration(
        Uri.parse(BuildConfig.AUTH_AUTHORIZE_URL),
        Uri.parse(BuildConfig.AUTH_TOKEN_URL),
    )
    private val refreshLock = Mutex()

    private val _tokens = MutableStateFlow(store.load())
    /** Current tokens, or null when signed out. */
    val tokens: StateFlow<Tokens?> = _tokens.asStateFlow()

    fun accessToken(): String? = _tokens.value?.accessToken

    /** The browser sign-in screen. Launch it and pass the result to [completeSignIn]. */
    fun signInIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            config,
            CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI),
        ).setScopes("openid", "profile").build()
        return service.getAuthorizationRequestIntent(request)
    }

    /** Swaps the code from the browser for tokens. Throws with a readable message on failure. */
    suspend fun completeSignIn(data: Intent?) {
        if (data == null) throw IllegalStateException("Sign-in was cancelled.")
        AuthorizationException.fromIntent(data)?.let { throw IllegalStateException(it.errorDescription ?: it.error ?: "Sign-in failed.") }
        val response = AuthorizationResponse.fromIntent(data) ?: throw IllegalStateException("Sign-in was cancelled.")
        val tokenResponse = performTokenRequest(response.createTokenExchangeRequest())
        saveFrom(tokenResponse, previousRefresh = null)
    }

    /**
     * Gets a new access token with the refresh token. Several API calls can
     * hit an expired token at once; the lock makes them share one refresh.
     * Returns false when the user has to sign in again.
     */
    suspend fun refresh(staleAccessToken: String?): Boolean = refreshLock.withLock {
        val current = _tokens.value ?: return false
        if (staleAccessToken != null && current.accessToken != staleAccessToken) return true
        val refreshToken = current.refreshToken ?: run { signOut(); return false }
        return try {
            val request = TokenRequest.Builder(config, CLIENT_ID)
                .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                .setRefreshToken(refreshToken)
                .build()
            saveFrom(performTokenRequest(request), previousRefresh = refreshToken)
            true
        } catch (e: AuthorizationException) {
            // The server rejected the refresh token: the session is over.
            if (e.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR) signOut()
            false
        } catch (e: Exception) {
            false
        }
    }

    fun signOut() {
        store.clear()
        _tokens.value = null
    }

    private fun saveFrom(response: TokenResponse, previousRefresh: String?) {
        val access = response.accessToken ?: throw IllegalStateException("The server sent no access token.")
        val expires = response.accessTokenExpirationTime
            ?: Jwt.claims(access)?.exp?.let { it * 1000 }
        val tokens = Tokens(access, response.refreshToken ?: previousRefresh, expires)
        store.save(tokens)
        _tokens.value = tokens
    }

    private suspend fun performTokenRequest(request: TokenRequest): TokenResponse =
        suspendCancellableCoroutine { cont ->
            service.performTokenRequest(request) { response, error ->
                when {
                    response != null -> cont.resume(response)
                    error != null -> cont.resumeWithException(error)
                    else -> cont.resumeWithException(IllegalStateException("No response from the auth server."))
                }
            }
        }

    companion object {
        /** Must match ANDROID_CLIENT_ID in TFHReact2024/packages/functions/src/auth/constants.ts. */
        const val CLIENT_ID = "android-client"
        /** Must match ANDROID_REDIRECT_URI there, and appAuthRedirectScheme in app/build.gradle.kts. */
        const val REDIRECT_URI = "co.topflighthealth.app:/callback"
    }
}
