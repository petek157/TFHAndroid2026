package co.topflighthealth.app.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.topflighthealth.app.api.Meal
import co.topflighthealth.app.domain.DayMacroSnapshot
import co.topflighthealth.app.domain.DayMacroSnapshot.Channel
import co.topflighthealth.app.domain.MacroDayStatus
import co.topflighthealth.app.domain.MacroDayStatus.Badge
import co.topflighthealth.app.domain.MacroSafeFloors
import co.topflighthealth.app.domain.macroInt
import co.topflighthealth.app.domain.MacroNutrition
import co.topflighthealth.app.api.FoodItem
import co.topflighthealth.app.ui.dashboard.AddWaterDialog
import co.topflighthealth.app.ui.dashboard.WaterBar
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Macro colors from the iOS asset catalog (system blue/green/orange/yellow). */
object MacroColors {
    val Calories = Color(0xFF007AFF)
    val Protein = Color(0xFF34C759)
    val Carbs = Color(0xFFFF9500)
    val Fats = Color(0xFFFFCC00)
    /** Darker yellow for fat text (iOS fatsText). */
    val FatsText = Color(0xFFB88700)
}

/** The Journal page (iOS FoodJournalView). */
@Composable
fun JournalScreen(
    showMacros: Boolean,
    onFlowActive: (Boolean) -> Unit = {},
    vm: JournalViewModel = viewModel(factory = JournalViewModel.Factory),
    flowVm: MealFlowViewModel = viewModel(factory = MealFlowViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val flow by flowVm.state.collectAsStateWithLifecycle()
    var addWater by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<MealPage?>(null) }

    androidx.compose.runtime.LaunchedEffect(page != null) { onFlowActive(page != null) }
    val floors = MacroSafeFloors.from(profile?.goalWeight, profile?.sex)
    val remaining = if (showMacros) remainingFor(state, MacroNutrition.ZERO, floors) else null
    fun startAdd() {
        flowVm.startNew(state.selectedDay, state.day?.meals?.size ?: 0)
        // iOS: macro tracking starts at the food search; otherwise straight to the meal form.
        page = if (showMacros) MealPage.Search else MealPage.Review
    }
    fun finish(savedDay: java.time.LocalDate?) {
        page = null
        if (savedDay != null && savedDay != state.selectedDay) vm.selectDay(savedDay) else vm.load()
    }

    when (val pg = page) {
        null -> Unit
        MealPage.Search -> return AddFoodScreen(
            flow, remaining, flowVm::setQuery,
            onPickFood = { page = MealPage.Amount(it) },
            onPickUsda = { u -> flowVm.resolveUsda(u) { page = MealPage.Amount(it) } },
            onCustom = { page = MealPage.Custom }, onRecents = { page = MealPage.Recents },
            onReview = { page = MealPage.Review }, onCancel = { page = if (flow.draft.original != null) MealPage.Review else null },
        )
        is MealPage.Amount -> return AmountScreen(pg.food, remaining, flow.draft.nutrition,
            onAdd = { flowVm.addItem(it); page = MealPage.Search }, onBack = { page = MealPage.Search })
        MealPage.Custom -> return CustomFoodScreen(flow.query, profile?.id.orEmpty(),
            onAdd = { item, food -> flowVm.addItem(item); flowVm.saveCustomFoodToPantry(food) }, onBack = { page = MealPage.Search })
        MealPage.Recents -> return RecentMealsScreen(flow, flowVm::loadRecent,
            onPick = { flowVm.useRecent(it); page = MealPage.Review }, onBack = { page = MealPage.Search })
        MealPage.Review -> return MealReviewScreen(
            flow, flowVm::updateDraft, flowVm::replaceItem, flowVm::removeItem,
            onAddMore = { page = MealPage.Search },
            onSave = { flowVm.save(state.day) { day -> if (day != null) finish(day) } },
            onDelete = if (flow.draft.original != null) ({ flowVm.delete(state.day) { ok -> if (ok) finish(null) } }) else null,
            onCancel = { page = null },
        )
    }

    Column(Modifier.fillMaxSize()) {
        HorizontalDivider()
        DayBar(state.selectedDay, vm::selectDay)
        if (showMacros) {
            val bank = state.weekBank
            val snap = state.day?.let { DayMacroSnapshot.build(it, bank, floors) }
            MacroSection(snap, bank, state.day == null) { help = true }
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            WaterBar(state.day?.effectiveWaterAmount ?: 0.0, profile?.effectiveWaterGoal ?: 80.0, state.savingWater) { addWater = true }
        }
        HorizontalDivider()
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = vm::load, modifier = Modifier.weight(1f)) {
            val meals = state.day?.meals.orEmpty().sortedBy { it.date }
            if (meals.isEmpty() && !state.loading) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No meals for ${state.selectedDay.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}", style = MaterialTheme.typography.titleMedium)
                    Text("Tap the + button to add your first meal!", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { startAdd() }) { Text("+ Add Entry") }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(meals, key = { it.id }) { m -> MealRow(m, showMacros) { flowVm.startEdit(m); page = MealPage.Review } }
                    item {
                        TextButton(onClick = { startAdd() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.AddCircle, null); Spacer(Modifier.width(6.dp)); Text("Add Meal")
                        }
                    }
                }
            }
        }
    }

    if (addWater) AddWaterDialog(onSave = { oz, done -> vm.addWater(oz, done) }) { addWater = false }
    if (help) JournalMacroHelp { help = false }
}

@Composable
private fun DayBar(day: LocalDate, onChange: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
        IconButton(onClick = { onChange(day.minusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
        Text(if (day == today) "Today" else day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(day.plusDays(1)) }, enabled = day < today) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
    }
}

/** "Remaining with bank" chips plus the four day bars (iOS macroProgressSection). */
@Composable
private fun MacroSection(snap: DayMacroSnapshot?, bank: co.topflighthealth.app.domain.WeeklyBankInfo?, noDay: Boolean, onHelp: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val bankRem = snap?.bankRemaining
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Remaining with bank", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val note = when { noDay -> "Track a meal to see for today"; bankRem == null -> "Log earlier days to unlock"; else -> null }
            note?.let { Text(" · $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) }
                ?: Spacer(Modifier.weight(1f))
            IconButton(onClick = onHelp, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.HelpOutline, "How these work", modifier = Modifier.size(18.dp)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Bank calories are derived from the banked macros, never stored.
            Chip("Cals", bankRem?.calories, -28.0, MacroColors.Calories)
            Chip("Pro", bankRem?.protein, -10.0, MacroColors.Protein)
            Chip("Carbs", bankRem?.carbs, -5.0, MacroColors.Carbs)
            Chip("Fats", bankRem?.fats, -5.0, MacroColors.FatsText)
        }
        if (snap != null) {
            val room = bank?.bankedRoom ?: MacroDayStatus.BankedRoom.ZERO
            val cal = snap.barStatus(Channel.Calories, room.calories, MacroDayStatus.Tolerance.CALORIES)
            val pro = snap.barStatus(Channel.Protein, room.protein, MacroDayStatus.Tolerance.PROTEIN)
            val carb = snap.barStatus(Channel.Carbs, room.carbs, MacroDayStatus.Tolerance.CARBS)
            val fat = snap.barStatus(Channel.Fats, room.fats, MacroDayStatus.Tolerance.FATS)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroBar("Cal", cal, MacroColors.Calories); MacroBar("Carbs", carb, MacroColors.Carbs)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroBar("Protein", pro, MacroColors.Protein); MacroBar("Fats", fat, MacroColors.Fats)
                }
            }
            val clipped = listOf(cal, pro, carb, fat).any { it.isWeekClipped }
            val floor = snap.protein.isFloorBound || snap.carbs.isFloorBound || snap.fats.isFloorBound || snap.caloriesIsFloorBound
            if (clipped) Text("* Limited by week bank (payback / no further borrowing)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (floor) Text("Safe minimums applied so you still eat enough protein, carbs, and fat.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Chip(title: String, amount: Double?, overAt: Double, normal: Color) {
    val color = when { amount == null -> MaterialTheme.colorScheme.onSurfaceVariant; amount < overAt -> Color(0xFFFF3B30); else -> normal }
    Column(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f)).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 11.sp, color = color)
        Text(amount?.let { "${macroInt(it)}" } ?: "—", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** "Protein: 45 / 150" bar with a ✓, $ or × badge (iOS MacroProgressBar). Red fill when past the ok ceiling. */
@Composable
fun MacroBar(title: String, r: MacroDayStatus.ChannelResult, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.2f))) {
        Box(Modifier.fillMaxWidth(r.fillProgress.coerceIn(0.0, 1.0).toFloat()).height(22.dp).background(if (r.isRed) Color(0xFFFF3B30) else color))
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${if (r.isWeekClipped) "$title*" else title}: ${macroInt(r.remainingLabel)} / ${macroInt(r.goalLabel)}",
                fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
            when (r.badge) {
                Badge.None -> Unit
                Badge.Checkmark -> BadgeBox { Icon(Icons.Filled.Check, "Done", tint = Color(0xFF34C759), modifier = Modifier.size(12.dp)) }
                Badge.Dollar -> BadgeBox { Text("$", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759)) }
                Badge.Multiply -> BadgeBox { Icon(Icons.Filled.Close, "Over", tint = Color(0xFFFF3B30), modifier = Modifier.size(12.dp)) }
            }
        }
    }
}

@Composable
private fun BadgeBox(content: @Composable () -> Unit) {
    Box(Modifier.size(16.dp, 14.dp).clip(RoundedCornerShape(3.dp)).background(Color.White), contentAlignment = Alignment.Center) { content() }
}

/** One meal in the list (iOS MealRowView). */
@Composable
private fun MealRow(m: Meal, showMacros: Boolean, onClick: () -> Unit) {
    val time = m.instant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)).orEmpty()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color.Gray.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Image, null, tint = Color.Gray)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(m.name ?: m.description ?: "Unnamed Meal", fontWeight = FontWeight.SemiBold)
            if (m.name != null) m.description?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showMacros && m.mealItems.isNotEmpty()) {
                val n = m.nutrition
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniChip("Cal", n.calories, MacroColors.Calories); MiniChip("P", n.protein, MacroColors.Protein)
                    MiniChip("C", n.carbs, MacroColors.Carbs); MiniChip("F", n.fats, MacroColors.FatsText)
                }
            }
            m.notes?.takeIf { it.isNotBlank() }?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.StickyNote2, null, tint = Color(0xFFFF9500), modifier = Modifier.size(12.dp))
                    Text(" $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray)
    }
}

@Composable
private fun MiniChip(label: String, v: Double, color: Color) {
    Row(Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.1f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
        Text(" ${macroInt(v)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun JournalMacroHelp(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Reading your macros") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Each bar shows what's left for the day out of the day's goal.")
                Text("✓ means you hit it. $ means you went a little over but had banked room from earlier in the week. × means you're over.")
                Text("\"Remaining with bank\" adds up the earlier days of this week. It needs every earlier day to have a meal logged.")
                Text("A * means today's target is lowered to pay back an earlier surplus. Safe minimums keep it from going too low.")
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Got it") } },
    )
}

/** Pages of the add/edit meal flow, shown in place of the Journal. */
sealed interface MealPage {
    data object Search : MealPage
    data class Amount(val food: FoodItem) : MealPage
    data object Custom : MealPage
    data object Recents : MealPage
    data object Review : MealPage
}
