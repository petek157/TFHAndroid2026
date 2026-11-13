package co.topflighthealth.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.topflighthealth.app.TfhApplication
import co.topflighthealth.app.api.SubscriptionStatus
import co.topflighthealth.app.api.TfhApi
import co.topflighthealth.app.api.UserDto
import co.topflighthealth.app.auth.AuthRepository
import co.topflighthealth.app.auth.Jwt
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the whole app needs to know to decide which screen to show. */
data class AppState(
    val signedIn: Boolean = false,
    val signingIn: Boolean = false,
    val loading: Boolean = false,
    val user: UserDto? = null,
    val membership: SubscriptionStatus? = null,
    val hasMessaging: Boolean = false,
    val error: String? = null,
) {
    val isMember: Boolean get() = membership?.isMember == true
}

/**
 * Plays the part of AuthManager + SubscriptionManager + the top of MainView on
 * iOS: tracks the session, loads the user, and checks membership with the server.
 */
class AppViewModel(private val auth: AuthRepository, private val api: TfhApi) : ViewModel() {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            auth.tokens.collect { tokens ->
                if (tokens == null) {
                    _state.update { AppState(error = it.error) }
                } else {
                    val features = Jwt.claims(tokens.accessToken)?.properties?.membership?.features.orEmpty()
                    val wasSignedIn = _state.value.signedIn
                    _state.update { it.copy(signedIn = true, hasMessaging = "messaging" in features) }
                    if (!wasSignedIn) refresh()
                }
            }
        }
    }

    fun signInIntent(): Intent = auth.signInIntent()

    fun onSignInResult(data: Intent?) {
        _state.update { it.copy(signingIn = true, error = null) }
        viewModelScope.launch {
            try {
                auth.completeSignIn(data)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Sign-in failed.") }
            } finally {
                _state.update { it.copy(signingIn = false) }
            }
        }
    }

    /** Reloads the user and membership. Called after sign-in and whenever the app comes back to the front. */
    fun refresh() {
        if (!_state.value.signedIn) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val user = api.me()
                if (user == null) {
                    // Same as iOS: the account no longer exists, so sign out.
                    auth.signOut()
                    return@launch
                }
                val membership = api.subscriptionStatus()
                _state.update { it.copy(user = user, membership = membership, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Couldn't reach Top Flight Health. Check your connection.") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun signOut() = auth.signOut()

    fun clearError() = _state.update { it.copy(error = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TfhApplication
                AppViewModel(app.container.auth, app.container.api)
            }
        }
    }
}
