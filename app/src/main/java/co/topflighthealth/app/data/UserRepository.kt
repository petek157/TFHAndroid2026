package co.topflighthealth.app.data

import co.topflighthealth.app.api.ApiFactory
import co.topflighthealth.app.api.GoalsUpdate
import co.topflighthealth.app.api.NewWeightEntry
import co.topflighthealth.app.api.TfhApi
import co.topflighthealth.app.api.WeightEntry
import co.topflighthealth.app.domain.MacroHistory
import co.topflighthealth.app.domain.MacroHistoryEntry
import co.topflighthealth.app.domain.MacroMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate

/**
 * The signed-in user's record and weight entries, shared by every screen.
 * Plays the part of the iOS UserViewModel + WeightTrackingViewModel.
 */
class UserRepository(private val api: TfhApi) {
    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _weights = MutableStateFlow<List<WeightEntry>>(emptyList())
    /** Newest first. */
    val weights: StateFlow<List<WeightEntry>> = _weights.asStateFlow()

    private val saveLock = Mutex()

    /** Loads the user. Returns null if the server says the account no longer exists. */
    suspend fun load(): Profile? {
        val p = Profile.from(api.me())
        _profile.value = p
        return p
    }

    fun clear() {
        _profile.value = null
        _weights.value = emptyList()
    }

    /**
     * Saves edited fields, the way the iOS autosave does: start from the
     * server's latest copy so a coach's edits aren't overwritten, write a
     * macroHistory row if the plan changed, then refresh the daily goals.
     */
    suspend fun save(changes: Map<String, JsonElement>) = saveLock.withLock {
        val current = _profile.value ?: return@withLock
        if (changes.isEmpty()) return@withLock
        val server = Profile.from(api.user(current.id)) ?: current
        val next = server.with(changes)

        val body = changes.toMutableMap()
        val prevCalc = MacroMath.calculate(server.macroInput)
        val nextCalc = MacroMath.calculate(next.macroInput)
        if (changes.keys.any { it in Profile.POLICY_KEYS } || server.macroHistory.isEmpty()) {
            val history = MacroHistory.applyOnSave(
                history = server.macroHistory,
                previous = server.policy,
                next = next.policy,
                previousCalc = prevCalc,
                nextCalc = nextCalc,
                createdDay = server.createdDay,
                today = LocalDate.now().toString(),
            )
            if (history != server.macroHistory) {
                body["macroHistory"] = ApiFactory.json.encodeToJsonElement(ListSerializer(MacroHistoryEntry.serializer()), history)
            }
        }
        body["_id"] = JsonPrimitive(server.id)
        // The server resets a missing birthday to today. Always send it.
        if ("birthday" !in body) body["birthday"] = Profile.value(next.birthdayInstant)

        val saved = Profile.from(api.updateUser(server.id, JsonObject(body))) ?: next
        _profile.value = saved
        syncGoals(prevCalc?.goals, saved)
    }

    suspend fun loadWeights() {
        val id = _profile.value?.id ?: return
        _weights.value = api.weightEntries(id).sortedByDescending { it.date }
    }

    suspend fun addWeight(weight: Double, at: Instant) {
        val before = _profile.value ?: return
        api.addWeightEntry(NewWeightEntry(before.id, at.toString(), MacroMath.jsRound(weight * 10) / 10))
        afterWeightChange(before)
    }

    suspend fun deleteWeight(entry: WeightEntry) {
        val before = _profile.value ?: return
        api.deleteWeightEntry(entry.id)
        afterWeightChange(before)
    }

    /** The server updates currentWeight itself; reload both and refresh goals if the plan moved. */
    private suspend fun afterWeightChange(before: Profile) {
        loadWeights()
        val after = load() ?: return
        syncGoals(MacroMath.calculate(before.macroInput)?.goals, after)
    }

    /** Pushes new daily goals to today's and future DaysMeals when they changed (iOS goal listener). */
    private suspend fun syncGoals(before: co.topflighthealth.app.domain.DailyGoals?, after: Profile) {
        val goals = MacroMath.calculate(after.macroInput)?.goals ?: return
        if (goals == before) return
        api.updateDaysMealsGoals(
            GoalsUpdate(
                userObjectId = after.id,
                startDate = Profile.localMidnight(LocalDate.now()).toString(),
                goalCalories = goals.calories,
                goalProtein = goals.protein,
                goalCarbs = goals.carbs,
                goalFats = goals.fats,
                goalFiber = goals.fiber,
            ),
        )
    }
}
