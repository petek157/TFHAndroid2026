package co.topflighthealth.app.ui.nutrition

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.topflighthealth.app.api.FoodItem
import co.topflighthealth.app.api.Meal
import co.topflighthealth.app.api.MealItem
import co.topflighthealth.app.domain.EffectiveChannelTarget
import co.topflighthealth.app.domain.MacroNutrition
import co.topflighthealth.app.domain.MacroSafeFloors
import co.topflighthealth.app.domain.UnitConverter
import co.topflighthealth.app.domain.WeeklyBankInfo
import co.topflighthealth.app.domain.macroInt
import co.topflighthealth.app.domain.portionString
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

/** What's left for the day and with the week bank, after the staged items (iOS remainingForDay / bankedRemainingForDay). */
data class Remaining(val day: MacroNutrition, val bank: MacroNutrition?)

fun remainingFor(state: JournalState, staged: MacroNutrition, floors: MacroSafeFloors): Remaining? {
    val dm = state.day ?: return null
    val eaten = dm.dayNutrition
    val pure = MacroNutrition(dm.goalProtein - eaten.protein - staged.protein, dm.goalCarbs - eaten.carbs - staged.carbs,
        dm.goalFats - eaten.fats - staged.fats, dm.goalFiber - eaten.fiber - staged.fiber)
    val bank = state.weekBank
    fun eff(goal: Double, pureRem: Double, locked: Double, floor: Double) =
        EffectiveChannelTarget.compute(goal, goal - pureRem, if (bank == null) 0.0 else locked, floor).displayRemaining
    val day = MacroNutrition(
        eff(dm.goalProtein, pure.protein, bank?.lockedProteinBalance ?: 0.0, floors.protein),
        eff(dm.goalCarbs, pure.carbs, bank?.lockedCarbsBalance ?: 0.0, floors.carbs),
        eff(dm.goalFats, pure.fats, bank?.lockedFatsBalance ?: 0.0, floors.fats),
        pure.fiber,
    )
    val withBank = bank?.let { b: WeeklyBankInfo ->
        MacroNutrition(pure.protein - b.lockedProteinBalance, pure.carbs - b.lockedCarbsBalance, pure.fats - b.lockedFatsBalance, pure.fiber)
    }
    return Remaining(day, withBank)
}

private fun MacroNutrition.minus(n: MacroNutrition) = MacroNutrition(protein - n.protein, carbs - n.carbs, fats - n.fats, fiber - n.fiber)

/** Two rows of chips: remaining for the day, and with the week bank (iOS DayRemainingChipsView). */
@Composable
fun RemainingChips(r: Remaining?, minus: MacroNutrition = MacroNutrition.ZERO, includes: Boolean = false) {
    if (r == null) return
    val day = r.day.minus(minus)
    val bank = r.bank?.minus(minus)
    val suffix = if (includes) " (includes selection)" else ""
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Remaining for the day$suffix", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow(day)
        Text("Remaining with bank$suffix" + if (bank == null) " · Log earlier days to unlock" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow(bank)
    }
}

@Composable
private fun ChipRow(n: MacroNutrition?) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("Cals", n?.calories, -28.0, MacroColors.Calories)
        Chip("Pro", n?.protein, -10.0, MacroColors.Protein)
        Chip("Carbs", n?.carbs, -5.0, MacroColors.Carbs)
        Chip("Fats", n?.fats, -5.0, MacroColors.FatsText)
    }
}

@Composable
private fun RowScope.Chip(title: String, amount: Double?, overAt: Double, normal: Color) {
    val color = when { amount == null -> MaterialTheme.colorScheme.onSurfaceVariant; amount < overAt -> Color(0xFFFF3B30); else -> normal }
    Column(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f)).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 11.sp, color = color)
        Text(amount?.let { "${macroInt(it)}" } ?: "—", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** The search screen (iOS AddItemSheetView): Custom and Recents, the remaining chips, pantry and USDA results. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(
    flow: MealFlowState,
    remaining: Remaining?,
    onQuery: (String) -> Unit,
    onPickFood: (FoodItem) -> Unit,
    onPickUsda: (co.topflighthealth.app.api.USDAFood) -> Unit,
    onCustom: () -> Unit,
    onRecents: () -> Unit,
    onReview: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
        TopAppBar(windowInsets = WindowInsets(0),
            title = { Text("Add Food") },
            navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
            actions = { TextButton(onClick = onReview) { Text("Review${if (flow.draft.items.isNotEmpty()) " (${flow.draft.items.size})" else ""}") } },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickButton(Icons.Filled.Edit, "Custom", Color(0xFF007AFF), onCustom)
                QuickButton(Icons.Filled.History, "Recents", Color(0xFFFF9500), onRecents)
                QuickButton(Icons.AutoMirrored.Outlined.MenuBook, "Review", Color(0xFFAF52DE), onReview)
            }
            Box(Modifier.padding(horizontal = 16.dp)) { RemainingChips(remaining, flow.draft.nutrition) }
            OutlinedTextField(
                flow.query, onQuery, placeholder = { Text("Search foods") }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (flow.query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Clear") } },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
            )
            val q = flow.query.trim()
            val mine = flow.myPantry.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
            val tfh = flow.tfhPantry.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
            if (flow.pantryLoading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                if (mine.isNotEmpty()) {
                    item { SectionHeader("My Pantry") }
                    items(mine, key = { "m" + it.id }) { FoodRow(it.name, describe(it)) { onPickFood(it) } }
                }
                if (tfh.isNotEmpty()) {
                    item { SectionHeader("TFH Pantry") }
                    items(tfh, key = { "t" + it.id }) { FoodRow(it.name, describe(it)) { onPickFood(it) } }
                }
                if (q.isNotEmpty()) {
                    item { SectionHeader("Search Results") }
                    if (flow.usdaLoading) item { Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                    items(flow.usdaResults, key = { "u" + it.fdcId }) { u ->
                        val brand = listOfNotNull(u.brandName ?: u.brandOwner).joinToString()
                        FoodRow(u.description, listOf(brand, "${macroInt(u.calories)} cal per ${portionString(u.servingSize ?: 100.0)} ${u.servingSizeUnit ?: "g"}").filter { it.isNotBlank() }.joinToString(" · ")) { onPickUsda(u) }
                    }
                }
                if (mine.isEmpty() && tfh.isEmpty() && flow.usdaResults.isEmpty() && !flow.usdaLoading && !flow.pantryLoading) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                            Text(if (q.isEmpty()) "Search your pantry or the food database" else "No results for '$q'", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun describe(f: FoodItem): String {
    val cal = macroInt(f.nutritionFor(f.servingAmount, f.servingUnit).calories)
    val household = if ((f.commonAmount ?: 0.0) > 0 && !f.commonUnit.isNullOrEmpty()) " (${portionString(f.commonAmount!!)} ${f.commonUnit})" else ""
    return "$cal cal per ${portionString(f.servingAmount)} ${f.servingUnit}$household"
}

@Composable
private fun SectionHeader(t: String) = Text(t, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))

@Composable
private fun FoodRow(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, maxLines = 2)
        Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

@Composable
private fun RowScope.QuickButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color)
        Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

/** Choosing how much of a food (iOS AmountSelectionView + PortionSelectionView). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountScreen(food: FoodItem, remaining: Remaining?, staged: MacroNutrition, onAdd: (MealItem) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var amount by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("servings") }
    val value = amount.toDoubleOrNull() ?: 0.0
    val n = food.nutritionFor(value, unit)
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
        TopAppBar(windowInsets = WindowInsets(0),
            title = { Text("Add Item") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            actions = {
                TextButton(enabled = value > 0, onClick = {
                    onAdd(MealItem(
                        id = UUID.randomUUID().toString(), type = "food", name = food.name, amount = value,
                        servingAmount = food.servingAmount, servingUnit = food.servingUnit, unit = unit,
                        calories = n.calories, protein = n.protein, carbs = n.carbs, fats = n.fats, fiber = n.fiber,
                        image = food.image, sourceFoodItemId = food.id,
                    ))
                }) { Text("Add") }
            },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(food.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, label = { Text("Amount") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                UnitDropdown(unit, food.availableUnits, Modifier.weight(1f)) { newUnit ->
                    // Keep the same quantity when switching units (iOS convertAmount).
                    if (value > 0) amount = portionString(UnitConverter.convert(value, unit, newUnit, food.servingAmount, food.servingUnit))
                    unit = newUnit
                }
            }
            Text("Reference: ${describe(food)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (unit == "servings") {
                Text("Servings: ${portionString(value)}")
                Slider(value = value.toFloat().coerceIn(0.25f, 5f), onValueChange = { amount = portionString(Math.round(it * 4) / 4.0) }, valueRange = 0.25f..5f, steps = 18)
                listOf(listOf(0.25, 0.5, 0.75, 1.0), listOf(1.5, 2.0, 2.5, 3.0)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { v -> FilterChip(selected = kotlin.math.abs(value - v) < 0.001, onClick = { amount = portionString(v) }, label = { Text(portionString(v)) }) }
                    }
                }
            }
            NutritionPreview(n)
            RemainingChips(remaining, staged.plus(n), includes = true)
        }
    }
}

private fun MacroNutrition.plus(o: MacroNutrition) = MacroNutrition(protein + o.protein, carbs + o.carbs, fats + o.fats, fiber + o.fiber)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(value: String, options: List<String>, modifier: Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        OutlinedTextField(value, {}, readOnly = true, label = { Text("Unit") }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
        ExposedDropdownMenu(open, { open = false }) { options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(o) }) } }
    }
}

@Composable
fun NutritionPreview(n: MacroNutrition) {
    Row(Modifier.fillMaxWidth()) {
        listOf(Triple("Cal", "%.0f".format(n.calories), MacroColors.Calories), Triple("Protein", "%.1f".format(n.protein), MacroColors.Protein),
            Triple("Carbs", "%.1f".format(n.carbs), MacroColors.Carbs), Triple("Fats", "%.1f".format(n.fats), Color(0xFFFF3B30)),
            Triple("Fiber", "%.1f".format(n.fiber), Color(0xFFAF52DE))).forEach { (t, v, c) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(v, fontWeight = FontWeight.SemiBold, color = c); Text(t, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val HOUSEHOLD_UNITS = listOf("cup(s)", "tbsp", "tsp", "fl oz", "piece(s)", "slice(s)", "scoop(s)")

/**
 * A food typed in by hand (iOS ManualEntryView): macros per one serving, then
 * the amount eaten. It's also saved to My Pantry for next time. Stays open
 * for the next entry after Add.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFoodScreen(initialName: String, userId: String, onAdd: (MealItem, FoodItem) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var name by remember { mutableStateOf(initialName) }
    var servingStr by remember { mutableStateOf("100") }
    var servingUnit by remember { mutableStateOf("g") }
    var commonStr by remember { mutableStateOf("") }
    var commonUnit by remember { mutableStateOf("cup(s)") }
    var p by remember { mutableStateOf("") }; var c by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }; var fi by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }; var unit by remember { mutableStateOf("servings") }
    var added by remember { mutableStateOf<String?>(null) }

    val serving = servingStr.toDoubleOrNull() ?: 1.0
    val amt = amount.toDoubleOrNull() ?: 0.0
    // Same multiplier rules as iOS consumedMultiplier.
    val mult = if (amt <= 0 || serving <= 0) 0.0 else when (unit.lowercase()) {
        "serving", "servings" -> amt
        "oz", "ounces" -> amt * 28.3495 / serving
        else -> amt / serving
    }
    val per = MacroNutrition(p.toDoubleOrNull() ?: 0.0, c.toDoubleOrNull() ?: 0.0, f.toDoubleOrNull() ?: 0.0, fi.toDoubleOrNull() ?: 0.0)
    val eaten = MacroNutrition(per.protein * mult, per.carbs * mult, per.fats * mult, per.fiber * mult)
    val canAdd = name.isNotBlank() && amt > 0 && serving > 0

    Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
        TopAppBar(windowInsets = WindowInsets(0), title = { Text("Custom Food") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            added?.let { Text("Added $it. Add another, or go back.", color = MacroColors.Protein) }
            OutlinedTextField(name, { name = it }, label = { Text("Food name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("One serving is:", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumField(servingStr, { servingStr = it }, "Amount", Modifier.weight(1f))
                UnitDropdown(servingUnit, listOf("g", "ml"), Modifier.weight(1f)) { servingUnit = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumField(commonStr, { commonStr = it }, "Household equiv.", Modifier.weight(1f))
                UnitDropdown(commonUnit, HOUSEHOLD_UNITS, Modifier.weight(1f)) { commonUnit = it }
            }
            Text("Macros per $servingStr $servingUnit · ${macroInt(per.calories)} calories", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField(p, { p = it }, "Protein g", Modifier.weight(1f)); NumField(c, { c = it }, "Carbs g", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField(f, { f = it }, "Fats g", Modifier.weight(1f)); NumField(fi, { fi = it }, "Fiber g", Modifier.weight(1f))
            }
            Text("Amount Consumed", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumField(amount, { amount = it }, "Amount", Modifier.weight(1f))
                UnitDropdown(unit, listOf("servings", "g", "ml", "oz"), Modifier.weight(1f)) { unit = it }
            }
            NutritionPreview(eaten)
            Button(enabled = canAdd, modifier = Modifier.fillMaxWidth(), onClick = {
                val cleanName = name.trim()
                val common = commonStr.toDoubleOrNull()
                onAdd(
                    MealItem(UUID.randomUUID().toString(), "food", cleanName, amt, serving, servingUnit, unit,
                        eaten.calories, eaten.protein, eaten.carbs, eaten.fats, eaten.fiber),
                    FoodItem(UUID.randomUUID().toString(), cleanName, serving, servingUnit, calories = per.calories,
                        protein = per.protein, carbs = per.carbs, fats = per.fats, fiber = per.fiber, userObjectId = userId,
                        dataSource = "manual", commonAmount = common, commonUnit = if (common != null) commonUnit else null),
                )
                added = cleanName
                name = ""; amount = ""; servingStr = "100"; servingUnit = "g"; unit = "servings"
                p = ""; c = ""; f = ""; fi = ""; commonStr = ""; commonUnit = "cup(s)"
            }) { Text("Add Item") }
        }
    }
}

@Composable
private fun NumField(v: String, onChange: (String) -> Unit, label: String, modifier: Modifier) =
    OutlinedTextField(v, { onChange(it.filter { ch -> ch.isDigit() || ch == '.' || ch == '/' }.take(8)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier)

/** The last 25 days with meals, newest first (iOS RecentMealsView). Tap one to copy it into the draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentMealsScreen(flow: MealFlowState, onLoad: () -> Unit, onPick: (Meal) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { onLoad() }
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
        TopAppBar(windowInsets = WindowInsets(0), title = { Text("Recent Meals") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        val days = flow.recent.filter { it.meals.isNotEmpty() }.sortedByDescending { it.day }
        when {
            flow.recentLoading -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            days.isEmpty() -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { Text("No recent meals") }
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
                days.forEach { d ->
                    item { SectionHeader(d.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))) }
                    items(d.meals.sortedBy { it.date }, key = { it.id }) { m ->
                        val n = m.nutrition
                        FoodRow(m.name ?: m.description ?: "Meal",
                            "${macroInt(n.calories)} cal · P ${macroInt(n.protein)} · C ${macroInt(n.carbs)} · F ${macroInt(n.fats)} · ${m.mealItems.size} items") { onPick(m) }
                    }
                }
            }
        }
    }
}

/** Review and save (iOS EditMealView, without photos and glucose for now). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealReviewScreen(
    flow: MealFlowState,
    onChange: ((co.topflighthealth.app.ui.nutrition.MealDraft) -> co.topflighthealth.app.ui.nutrition.MealDraft) -> Unit,
    onEditItem: (MealItem, MealItem) -> Unit,
    onRemoveItem: (MealItem) -> Unit,
    onAddMore: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val d = flow.draft
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MealItem?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
        TopAppBar(windowInsets = WindowInsets(0),
            title = { Text(if (d.original == null) "New Meal" else "Edit Meal") },
            navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
            actions = { TextButton(enabled = !flow.saving, onClick = onSave) { Text(if (flow.saving) "Saving…" else "Save") } },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(d.name, { v -> onChange { it.copy(name = v) } }, label = { Text("Meal Name (Optional)") },
                placeholder = { Text("e.g., Breakfast, Lunch, Dinner") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Date & Time", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }) { Text(d.at.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) }
                OutlinedButton(onClick = { pickTime = true }) { Text(d.at.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Meal Items", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onAddMore) { Icon(Icons.Filled.Add, null); Text("Add items") }
            }
            if (d.items.isEmpty()) Text("Tap Add items to add food items and track macros", color = MaterialTheme.colorScheme.onSurfaceVariant)
            d.items.forEach { i ->
                val n = i.nutrition
                Row(Modifier.fillMaxWidth().clickable { editing = i }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(i.name)
                        Text("${portionString(i.amount)} ${i.unit} · ${macroInt(n.calories)} cal · P ${macroInt(n.protein)} C ${macroInt(n.carbs)} F ${macroInt(n.fats)}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRemoveItem(i) }) { Icon(Icons.Filled.Delete, "Remove ${i.name}") }
                }
                HorizontalDivider()
            }
            if (d.items.isNotEmpty()) {
                Text("Meal Total · ${macroInt(d.nutrition.calories)} cal", fontWeight = FontWeight.SemiBold)
                NutritionPreview(d.nutrition)
            }
            OutlinedTextField(d.description, { v -> onChange { it.copy(description = v) } }, label = { Text("Description") },
                placeholder = { Text("Describe your meal or food experience...") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(d.notes, { v -> onChange { it.copy(notes = v) } }, label = { Text("Notes") },
                placeholder = { Text("Add a note about this meal...") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            flow.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (onDelete != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete Meal", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = d.at.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = {
                st.selectedDateMillis?.let { ms -> val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    if (day <= LocalDate.now()) onChange { it.copy(at = LocalDateTime.of(day, it.at.toLocalTime())) } }
                pickDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } }) { DatePicker(st) }
    }
    if (pickTime) {
        val st = rememberTimePickerState(d.at.hour, d.at.minute)
        AlertDialog(onDismissRequest = { pickTime = false }, title = { Text("Time") }, text = { TimePicker(st) },
            confirmButton = { TextButton(onClick = { onChange { it.copy(at = LocalDateTime.of(it.at.toLocalDate(), LocalTime.of(st.hour, st.minute))) }; pickTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } })
    }
    editing?.let { item ->
        var amt by remember(item) { mutableStateOf(portionString(item.amount)) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(item.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField(amt, { amt = it }, "Amount (${item.unit})", Modifier.fillMaxWidth())
                    Text("Macros scale with the amount.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = {
                val v = amt.toDoubleOrNull()
                if (v != null && v > 0 && item.amount > 0) {
                    val r = v / item.amount
                    onEditItem(item, item.copy(amount = v, calories = item.calories * r, protein = item.protein * r,
                        carbs = item.carbs * r, fats = item.fats * r, fiber = item.fiber * r))
                }
                editing = null }) { Text("Update") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } })
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this meal?") },
            text = { Text("This removes the meal and its items from your journal.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    }
}

