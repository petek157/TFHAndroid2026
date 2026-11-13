package co.topflighthealth.app.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.topflighthealth.app.TfhApplication
import co.topflighthealth.app.api.DaysMeals
import co.topflighthealth.app.api.FoodItem
import co.topflighthealth.app.api.Meal
import co.topflighthealth.app.api.MealItem
import co.topflighthealth.app.api.QuickGoals
import co.topflighthealth.app.api.QuickMealRequest
import co.topflighthealth.app.api.TfhApi
import co.topflighthealth.app.api.USDAFood
import co.topflighthealth.app.data.Profile
import co.topflighthealth.app.data.UserRepository
import co.topflighthealth.app.domain.MacroHistory
import co.topflighthealth.app.domain.MacroMath
import co.topflighthealth.app.domain.MacroNutrition
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class MealDraft(
    /** The meal being edited, or null when creating. */
    val original: Meal? = null,
    val items: List<MealItem> = emptyList(),
    val name: String = "",
    val description: String = "",
    val notes: String = "",
    val at: LocalDateTime = LocalDateTime.now(),
) {
    val nutrition: MacroNutrition get() = MacroNutrition.sum(items.map { it.nutrition })
}

data class MealFlowState(
    val draft: MealDraft = MealDraft(),
    val myPantry: List<FoodItem> = emptyList(),
    val tfhPantry: List<FoodItem> = emptyList(),
    val pantryLoading: Boolean = false,
    val query: String = "",
    val usdaResults: List<USDAFood> = emptyList(),
    val usdaLoading: Boolean = false,
    val recent: List<DaysMeals> = emptyList(),
    val recentLoading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

/**
 * Adding or editing one meal (iOS CreateMealFlowView + CreateMealViewModel +
 * AddItemSheetView + EditMealView). Lives as long as the Nutrition tab.
 */
class MealFlowViewModel(private val api: TfhApi, private val users: UserRepository) : ViewModel() {
    private val _state = MutableStateFlow(MealFlowState())
    val state: StateFlow<MealFlowState> = _state.asStateFlow()
    private var searchJob: Job? = null

    private val userId get() = users.profile.value?.id

    /** Starts a new meal on [day] at the current time, named "Meal N" (iOS autoMealName). */
    fun startNew(day: LocalDate, mealsThatDay: Int) {
        _state.update {
            it.copy(draft = MealDraft(name = "Meal ${mealsThatDay + 1}", at = LocalDateTime.of(day, LocalTime.now().withSecond(0).withNano(0))),
                query = "", usdaResults = emptyList(), error = null)
        }
        loadPantry()
    }

    fun startEdit(meal: Meal) {
        val at = meal.instant?.atZone(ZoneId.systemDefault())?.toLocalDateTime() ?: LocalDateTime.now()
        _state.update {
            it.copy(draft = MealDraft(meal, meal.mealItems, meal.name.orEmpty(), meal.description.orEmpty(), meal.notes.orEmpty(), at),
                query = "", usdaResults = emptyList(), error = null)
        }
        loadPantry()
    }

    fun loadPantry() {
        val id = userId ?: return
        if (_state.value.myPantry.isNotEmpty() || _state.value.tfhPantry.isNotEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(pantryLoading = true) }
            val mine = runCatching { api.userFoodItems(id) }.getOrDefault(emptyList())
            val tfh = runCatching { api.systemFoodItems() }.getOrDefault(emptyList())
            _state.update {
                it.copy(myPantry = mine.filter { f -> f.userObjectId != null }.sortedBy { f -> f.name.lowercase() },
                    tfhPantry = tfh.sortedBy { f -> f.name.lowercase() }, pantryLoading = false)
            }
        }
    }

    /** Pantry filtering is instant; USDA waits 600 ms after typing stops and needs 2+ letters (iOS). */
    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        val trimmed = q.trim()
        if (trimmed.length < 2) {
            _state.update { it.copy(usdaResults = emptyList(), usdaLoading = false) }
            return
        }
        _state.update { it.copy(usdaLoading = true) }
        searchJob = viewModelScope.launch {
            delay(600)
            val results = runCatching { api.usdaSearch(trimmed) }.getOrDefault(emptyList())
            _state.update { it.copy(usdaResults = results, usdaLoading = false) }
        }
    }

    /** Turns a USDA hit into a FoodItem, using the detail record when it loads (iOS selectUSDAFood). */
    fun resolveUsda(food: USDAFood, onReady: (FoodItem) -> Unit) {
        val id = userId.orEmpty()
        viewModelScope.launch {
            val item = runCatching { api.usdaDetail(food.fdcId) }.map { d ->
                val fi = d.toFoodItem(id)
                // iOS saves portion-based foods to the user's pantry for next time.
                if (d.labelNutrients?.calories == null && !d.foodPortions.isNullOrEmpty()) runCatching { api.createFoodItem(fi) }
                fi
            }.getOrElse { food.toFoodItem(id) }
            onReady(item)
        }
    }

    fun addItem(item: MealItem) = _state.update { it.copy(draft = it.draft.copy(items = it.draft.items + item)) }
    fun removeItem(item: MealItem) = _state.update { it.copy(draft = it.draft.copy(items = it.draft.items - item)) }
    fun replaceItem(old: MealItem, new: MealItem) =
        _state.update { it.copy(draft = it.draft.copy(items = it.draft.items.map { i -> if (i === old) new else i })) }
    fun updateDraft(f: (MealDraft) -> MealDraft) = _state.update { it.copy(draft = f(it.draft)) }

    /** Saves a custom food to the user's pantry unless one with that name exists (iOS ManualEntryView). */
    fun saveCustomFoodToPantry(item: FoodItem) {
        if (_state.value.myPantry.any { it.name.equals(item.name, ignoreCase = true) }) return
        viewModelScope.launch {
            runCatching { api.createFoodItem(item) }.onSuccess {
                _state.update { s -> s.copy(myPantry = (s.myPantry + item).sortedBy { f -> f.name.lowercase() }) }
            }
        }
    }

    fun loadRecent() {
        val id = userId ?: return
        viewModelScope.launch {
            _state.update { it.copy(recentLoading = true) }
            val before = Profile.localMidnight(LocalDate.now()).toString()
            val r = runCatching { api.recentDaysMeals(before, 25, id) }.getOrDefault(emptyList())
            _state.update { it.copy(recent = r, recentLoading = false) }
        }
    }

    /** Fills the draft from a past meal (iOS populateFromRecentMeal). Items get new ids. */
    fun useRecent(m: Meal) = _state.update {
        it.copy(draft = it.draft.copy(
            items = m.mealItems.map { i -> i.copy(id = UUID.randomUUID().toString()) },
            name = m.name.orEmpty(), description = m.description.orEmpty(), notes = m.notes.orEmpty(),
        ))
    }

    /**
     * Creates the meal with daysmeals/quick-meal, or updates it in place. A meal
     * moved to another day is created there and removed from the old day, as iOS does.
     */
    fun save(currentDay: DaysMeals?, onDone: (LocalDate?) -> Unit) {
        val p = users.profile.value ?: return
        val d = _state.value.draft
        val instant = d.at.atZone(ZoneId.systemDefault()).toInstant()
        val meal = Meal(
            id = d.original?.id ?: UUID.randomUUID().toString(),
            date = instant.toString(),
            mealItems = d.items,
            fullImageUrl = d.original?.fullImageUrl,
            thumbImageUrl = d.original?.thumbImageUrl,
            name = d.name.trim().ifBlank { null },
            description = d.description.trim().ifBlank { null },
            notes = d.notes.trim().ifBlank { null },
        )
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val orig = d.original
                val origDay = orig?.instant?.atZone(ZoneId.systemDefault())?.toLocalDate()
                if (orig != null && origDay == d.at.toLocalDate() && currentDay != null) {
                    api.updateMeal(currentDay.id, orig.id, meal)
                } else {
                    quickCreate(p, d.at.toLocalDate(), meal.copy(id = UUID.randomUUID().toString()))
                    if (orig != null && currentDay != null) api.deleteMeal(currentDay.id, orig.id)
                }
                onDone(d.at.toLocalDate())
            } catch (e: Exception) {
                _state.update { it.copy(error = "Couldn't save the meal. Check your connection.") }
                onDone(null)
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    private suspend fun quickCreate(p: Profile, day: LocalDate, meal: Meal) {
        val g = MacroHistory.goalsForDay(p.macroHistory, MacroMath.calculate(p.macroInput)?.goals, day)
        api.quickMeal(
            QuickMealRequest(
                date = Profile.localMidnight(day).toString(),
                userObjectId = p.id,
                goals = QuickGoals(g?.calories ?: 2000.0, g?.protein ?: 150.0, g?.carbs ?: 200.0, g?.fats ?: 70.0, g?.fiber ?: MacroMath.DEFAULT_FIBER_GOAL),
                meal = meal,
            ),
        )
    }

    fun delete(currentDay: DaysMeals?, onDone: (Boolean) -> Unit) {
        val orig = _state.value.draft.original ?: return onDone(false)
        val day = currentDay ?: return onDone(false)
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val ok = runCatching { api.deleteMeal(day.id, orig.id) }.isSuccess
            _state.update { it.copy(saving = false, error = if (ok) null else "Couldn't delete the meal.") }
            onDone(ok)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TfhApplication
                MealFlowViewModel(app.container.api, app.container.users)
            }
        }
    }
}

