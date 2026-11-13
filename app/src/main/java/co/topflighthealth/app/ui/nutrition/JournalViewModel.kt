package co.topflighthealth.app.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.topflighthealth.app.TfhApplication
import co.topflighthealth.app.api.ApiFactory
import co.topflighthealth.app.api.DaysMeals
import co.topflighthealth.app.api.NewDaysMeals
import co.topflighthealth.app.api.TfhApi
import co.topflighthealth.app.api.WaterUpdate
import co.topflighthealth.app.data.Profile
import co.topflighthealth.app.data.UserRepository
import co.topflighthealth.app.domain.MacroHistory
import co.topflighthealth.app.domain.MacroMath
import co.topflighthealth.app.domain.WeeklyBankInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class JournalState(
    val selectedDay: LocalDate = LocalDate.now(),
    val day: DaysMeals? = null,
    /** The Monday-start week around [selectedDay], for the week bank. */
    val week: List<DaysMeals> = emptyList(),
    val loading: Boolean = false,
    val savingWater: Boolean = false,
    val error: String? = null,
) {
    /**
     * Locked balances for days before the selected one this week (iOS
     * FoodJournalViewModel.weekBankAsOfSelectedDay). Null when an earlier day
     * has no meals, so payback can't be proven.
     */
    val weekBank: WeeklyBankInfo?
        get() {
            val weekStart = selectedDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val byDay = week.associateBy { it.day }.toMutableMap()
            day?.let { byDay[it.day] = it }
            var cal = 0.0; var p = 0.0; var c = 0.0; var f = 0.0
            var d = weekStart
            while (d < selectedDay) {
                val dm = byDay[d]
                if (dm == null || dm.meals.isEmpty()) return null
                val e = dm.dayNutrition
                cal += e.calories - dm.goalCalories; p += e.protein - dm.goalProtein
                c += e.carbs - dm.goalCarbs; f += e.fats - dm.goalFats
                d = d.plusDays(1)
            }
            return WeeklyBankInfo(cal, p, c, f)
        }
}

/** The Journal page (iOS FoodJournalViewModel): one day of meals, its goals, water and the week bank. */
class JournalViewModel(private val api: TfhApi, private val users: UserRepository) : ViewModel() {
    private val _state = MutableStateFlow(JournalState())
    val state: StateFlow<JournalState> = _state.asStateFlow()
    val profile = users.profile
    private var loadJob: Job? = null

    init { load() }

    fun selectDay(day: LocalDate) {
        if (day > LocalDate.now()) return
        _state.update { it.copy(selectedDay = day, day = null) }
        load()
    }

    private fun midnight(d: LocalDate) = Profile.localMidnight(d).toString()

    /** Loads the day, creating its document when missing, as iOS does (autoCreate), then the week. */
    fun load() {
        val p = users.profile.value ?: return
        val d = _state.value.selectedDay
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val json = api.daysMeals(midnight(d), midnight(d.plusDays(1)), p.id)
                val day = (json as? JsonObject)?.let { ApiFactory.json.decodeFromJsonElement(DaysMeals.serializer(), it) }
                    ?: createDay(p, d)
                _state.update { if (it.selectedDay == d) it.copy(day = day) else it }
                val start = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val week = runCatching { api.daysMealsRange(midnight(start), midnight(start.plusDays(7)), p.id) }.getOrDefault(emptyList())
                _state.update { if (it.selectedDay == d) it.copy(week = week) else it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = "Couldn't load meals. Pull to refresh.") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private suspend fun createDay(p: Profile, day: LocalDate): DaysMeals {
        val g = MacroHistory.goalsForDay(p.macroHistory, MacroMath.calculate(p.macroInput)?.goals, day)
        return api.createDaysMeals(
            NewDaysMeals(
                date = midnight(day), userObjectId = p.id,
                goalCalories = g?.calories ?: 2000.0, goalProtein = g?.protein ?: 150.0,
                goalCarbs = g?.carbs ?: 200.0, goalFats = g?.fats ?: 70.0, goalFiber = g?.fiber ?: MacroMath.DEFAULT_FIBER_GOAL,
            ),
        )
    }

    fun addWater(ounces: Double, onDone: (String?) -> Unit) {
        val day = _state.value.day ?: return onDone("The day hasn't loaded yet.")
        viewModelScope.launch {
            _state.update { it.copy(savingWater = true) }
            try {
                api.setWater(day.id, WaterUpdate(maxOf(0.0, day.effectiveWaterAmount + ounces)))
                load()
                onDone(null)
            } catch (e: Exception) {
                onDone("Couldn't save water. Check your connection.")
            } finally {
                _state.update { it.copy(savingWater = false) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TfhApplication
                JournalViewModel(app.container.api, app.container.users)
            }
        }
    }
}
