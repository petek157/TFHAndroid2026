package co.topflighthealth.app.api

import co.topflighthealth.app.BuildConfig
import co.topflighthealth.app.auth.AuthRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ApiFactory {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun create(auth: AuthRepository): TfhApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(BearerInterceptor(auth))
            .authenticator(RefreshingAuthenticator(auth))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    })
                }
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TfhApi::class.java)
    }
}

/** Adds the access token to every call, like APIClient on iOS. */
private class BearerInterceptor(private val auth: AuthRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = auth.accessToken() ?: return chain.proceed(chain.request())
        return chain.proceed(
            chain.request().newBuilder().header("Authorization", "Bearer $token").build(),
        )
    }
}

/**
 * On a 401, refreshes the token once and retries the call. If the refresh
 * fails the user is signed out and the 401 goes back to the caller.
 */
private class RefreshingAuthenticator(private val auth: AuthRepository) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null // already retried once
        val stale = response.request.header("Authorization")?.removePrefix("Bearer ")
        val refreshed = runBlocking { auth.refresh(stale) }
        val token = auth.accessToken()
        if (!refreshed || token == null) return null
        return response.request.newBuilder().header("Authorization", "Bearer $token").build()
    }
}
