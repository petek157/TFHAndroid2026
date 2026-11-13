package co.topflighthealth.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.topflighthealth.app.TfhApplication
import co.topflighthealth.app.api.WeightEntry
import co.topflighthealth.app.data.Profile
import co.topflighthealth.app.data.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.time.LocalDate

data class ProfileUiState(
    /** The saved profile with any unsaved edits applied on top. */
    val profile: Profile? = null,
    val weights: List<WeightEntry> = emptyList(),
    val weightsLoading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

/**
 * Profile editing. Like iOS, edits save on their own: one second after the
 * last change, or right away when the screen closes.
 */
class ProfileViewModel(private val users: UserRepository) : ViewModel() {

    private val pending = MutableStateFlow<Map<String, JsonElement>>(emptyMap())
    private val status = MutableStateFlow(ProfileUiState())
    private var saveJob: Job? = null

    val state: StateFlow<ProfileUiState> =
        combine(users.profile, users.weights, pending, status) { p, w, edits, s ->
            s.copy(profile = p?.with(edits), weights = w)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun loadWeights() {
        viewModelScope.launch {
            status.value = status.value.copy(weightsLoading = true)
            // Quiet on failure: the dashboard and weight page show what they have.
            runCatching { users.loadWeights() }
            status.value = status.value.copy(weightsLoading = false)
        }
    }

    private fun edit(key: String, value: JsonElement) {
        pending.value = pending.value + (key to value)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1_000)
            saveNow()
        }
    }

    fun setFirstName(v: String) = edit("firstname", Profile.value(v))
    fun setLastName(v: String) = edit("lastname", Profile.value(v))
    fun setPhone(v: String) = edit("phone", Profile.value(v))
    fun setBirthday(day: LocalDate) = edit("birthday", Profile.value(Profile.localMidnight(day)))
    fun setHeight(feet: Int, inches: Int) {
        edit("feet", Profile.value(feet))
        edit("inches", Profile.value(inches))
    }
    fun setSex(v: String) = edit("sex", Profile.value(v))
    fun setObjective(v: String) = edit("objective", Profile.value(v))
    fun setBalanceOfCalories(v: String) = edit("balanceOfCalories", Profile.value(v))
    fun setGoalWeight(v: Double) = edit("goalWeight", Profile.value(v.coerceIn(80.0, 500.0)))
    fun setActivityLevel(v: String) = edit("activityLevel", Profile.value(v))
    fun setWaterGoal(v: Double?) = edit("waterGoal", Profile.value(v?.let { kotlin.math.max(0.0, Math.rint(it)) }))
    fun setWrist(v: Double) = edit("wristCircumference", Profile.value(v))

    /** Saves whatever is pending. Also called when the Profile tab closes. */
    fun saveNow() {
        val changes = pending.value
        if (changes.isEmpty()) return
        saveJob?.cancel()
        viewModelScope.launch {
            status.value = status.value.copy(saving = true, error = null)
            try {
                users.save(changes)
                // Drop only what was saved; keep edits made while saving.
                pending.value = pending.value.filter { (k, v) -> changes[k] != v }
            } catch (e: Exception) {
                status.value = status.value.copy(error = "Couldn't save your profile. Check your connection.")
            } finally {
                status.value = status.value.copy(saving = false)
            }
        }
    }

    fun addWeight(weight: Double, at: Instant, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                users.addWeight(weight, at)
                onDone(null)
            } catch (e: Exception) {
                onDone("Couldn't save the weight entry. Check your connection.")
            }
        }
    }

    fun deleteWeight(entry: WeightEntry) {
        viewModelScope.launch {
            runCatching { users.deleteWeight(entry) }
                .onFailure { status.value = status.value.copy(error = "Couldn't delete that entry.") }
        }
    }

    fun clearError() {
        status.value = status.value.copy(error = null)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TfhApplication
                ProfileViewModel(app.container.users)
            }
        }
    }
}
