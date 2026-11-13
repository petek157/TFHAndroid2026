package co.topflighthealth.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.topflighthealth.app.TfhApplication
import co.topflighthealth.app.api.ApiFactory
import co.topflighthealth.app.api.DaysMeals
import co.topflighthealth.app.api.NewDaysMeals
import co.topflighthealth.app.api.NewNote
import co.topflighthealth.app.api.Note
import co.topflighthealth.app.api.ReminderOccurrence
import co.topflighthealth.app.api.TfhApi
import co.topflighthealth.app.api.WaterUpdate
import co.topflighthealth.app.data.Profile
import co.topflighthealth.app.data.UserRepository
import co.topflighthealth.app.domain.MacroHistory
import co.topflighthealth.app.domain.MacroMath
import co.topflighthealth.app.domain.MacroSafeFloors
import co.topflighthealth.app.domain.WeeklyNutritionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class DashboardState(
    /** The day the "Today's Health" chart and water bar show; drives the scorecard week too. */
    val selectedDay: LocalDate = LocalDate.now(),
    val day: DaysMeals? = null,
    val week: List<DaysMeals> = emptyList(),
    val notes: List<Note> = emptyList(),
    val reminders: List<ReminderOccurrence> = emptyList(),
    val loading: Boolean = false,
    val savingWater: Boolean = false,
    val error: String? = null,
) {
    val weekStart: LocalDate get() = selectedDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val notesForDay: List<Note> get() = notes.filter { it.localDay() == selectedDay }
}

fun Note.instant(): Instant? = runCatching { Instant.parse(date) }.getOrNull()
fun Note.localDay(): LocalDate? = instant()?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()

/** Dashboard data (iOS DashboardView plus the view models it owns). */
class DashboardViewModel(private val api: TfhApi, private val users: UserRepository) : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()
    val profile: StateFlow<Profile?> = users.profile

    private val userId: String? get() = users.profile.value?.id

    init { refresh() }

    fun refresh() {
        val id = userId ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                loadDay(); loadWeek()
                val notes = runCatching { api.notes(id) }.getOrDefault(_state.value.notes)
                val reminders = runCatching { api.reminderOccurrences(id).sortedBy { it.scheduledFor } }.getOrDefault(emptyList())
                _state.update { it.copy(notes = notes, reminders = reminders) }
                runCatching { users.loadWeights() }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Couldn't load your dashboard. Pull to refresh.") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun selectDay(day: LocalDate) {
        if (day > LocalDate.now()) return
        val oldWeek = _state.value.weekStart
        _state.update { it.copy(selectedDay = day, day = null) }
        viewModelScope.launch {
            runCatching { loadDay() }
            if (_state.value.weekStart != oldWeek) runCatching { loadWeek() }
        }
    }

    private fun midnight(d: LocalDate) = Profile.localMidnight(d).toString()

    private suspend fun loadDay() {
        val id = userId ?: return
        val d = _state.value.selectedDay
        val json = api.daysMeals(midnight(d), midnight(d.plusDays(1)), id)
        val day = (json as? JsonObject)?.let { ApiFactory.json.decodeFromJsonElement(DaysMeals.serializer(), it) }
        _state.update { if (it.selectedDay == d) it.copy(day = day) else it }
    }

    private suspend fun loadWeek() {
        val id = userId ?: return
        val start = _state.value.weekStart
        val week = api.daysMealsRange(midnight(start), midnight(start.plusDays(7)), id)
        _state.update { it.copy(week = week) }
    }

    /** Builds the weekly scorecard with the user's live plan as the fallback for days with no document. */
    fun summary(state: DashboardState, profile: Profile?): WeeklyNutritionSummary {
        val calc = profile?.let { MacroMath.calculate(it.macroInput) }
        val goal = calc?.calcTotalCalories ?: 0.0
        return WeeklyNutritionSummary.build(
            daysMeals = state.week,
            weekStart = state.weekStart,
            today = LocalDate.now(),
            goalFallback = if (goal > 0) goal else 2000.0,
            dailyTDEE = calc?.tdee ?: 0.0,
            dailyGoal = goal,
            userObjective = profile?.objective,
            goalProteinFallback = calc?.protein ?: 0.0,
            goalCarbsFallback = calc?.carbs ?: 0.0,
            goalFatsFallback = calc?.fats ?: 0.0,
            floors = MacroSafeFloors.from(profile?.goalWeight, profile?.sex),
        )
    }

    /** Adds ounces to the selected day's water, creating the day's document first if needed (iOS setWaterAmount). */
    fun addWater(ounces: Double, onDone: (String?) -> Unit) {
        val p = users.profile.value ?: return
        viewModelScope.launch {
            _state.update { it.copy(savingWater = true) }
            try {
                val day = _state.value.day ?: createDay(p, _state.value.selectedDay)
                val next = maxOf(0.0, day.effectiveWaterAmount + ounces)
                api.setWater(day.id, WaterUpdate(next))
                loadDay()
                onDone(null)
            } catch (e: Exception) {
                onDone("Couldn't save water. Check your connection.")
            } finally {
                _state.update { it.copy(savingWater = false) }
            }
        }
    }

    private suspend fun createDay(p: Profile, day: LocalDate): DaysMeals {
        val live = MacroMath.calculate(p.macroInput)?.goals
        val g = MacroHistory.goalsForDay(p.macroHistory, live, day)
        return api.createDaysMeals(
            NewDaysMeals(
                date = midnight(day), userObjectId = p.id,
                goalCalories = g?.calories ?: 2000.0, goalProtein = g?.protein ?: 150.0,
                goalCarbs = g?.carbs ?: 200.0, goalFats = g?.fats ?: 70.0, goalFiber = g?.fiber ?: MacroMath.DEFAULT_FIBER_GOAL,
            ),
        )
    }

    fun addNote(text: String, category: String, at: Instant, onDone: (String?) -> Unit) {
        val id = userId ?: return
        viewModelScope.launch {
            try {
                api.addNote(NewNote(id, at.toString(), text.trim(), category))
                _state.update { it.copy(notes = api.notes(id)) }
                onDone(null)
            } catch (e: Exception) {
                onDone("Couldn't save the note. Check your connection.")
            }
        }
    }

    fun deleteNote(note: Note) {
        val id = userId ?: return
        viewModelScope.launch {
            runCatching { api.deleteNote(note.id); _state.update { it.copy(notes = api.notes(id)) } }
        }
    }

    fun completeReminder(r: ReminderOccurrence) {
        val id = userId ?: return
        _state.update { s -> s.copy(reminders = s.reminders - r) }
        viewModelScope.launch {
            runCatching { api.completeReminder(r.id) }
            runCatching { _state.update { it.copy(reminders = api.reminderOccurrences(id).sortedBy { o -> o.scheduledFor }) } }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TfhApplication
                DashboardViewModel(app.container.api, app.container.users)
            }
        }
    }
}
