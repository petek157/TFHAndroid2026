package co.topflighthealth.app.ui.dashboard

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.topflighthealth.app.api.Meal
import co.topflighthealth.app.api.Note
import co.topflighthealth.app.ui.profile.AddWeightDialog
import co.topflighthealth.app.ui.profile.ProfileViewModel
import co.topflighthealth.app.ui.profile.formatDateTime
import co.topflighthealth.app.ui.profile.formatWeight
import co.topflighthealth.app.ui.theme.RedMain
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val Purple = Color(0xFFAF52DE)
private val Orange = Color(0xFFFF9500)
private val Indigo = Color(0xFF5856D6)
private val PanelBg @Composable get() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

/** Note categories (iOS NoteCategory): raw value, label, icon. */
val NOTE_CATEGORIES = listOf(
    Triple("weightTraining", "Weight Training", Icons.Filled.FitnessCenter),
    Triple("cardio", "Cardio", Icons.Filled.Favorite),
    Triple("movement", "Movement", Icons.AutoMirrored.Filled.DirectionsWalk),
    Triple("nutrition", "Nutrition", Icons.Filled.Restaurant),
    Triple("symptom", "Symptom", Icons.Filled.Healing),
    Triple("general", "General", Icons.AutoMirrored.Filled.StickyNote2),
)
private fun categoryIcon(raw: String) = NOTE_CATEGORIES.firstOrNull { it.first == raw }?.third ?: Icons.AutoMirrored.Filled.StickyNote2
private fun categoryName(raw: String) = NOTE_CATEGORIES.firstOrNull { it.first == raw }?.second ?: "General"

/**
 * The Dashboard tab (iOS DashboardView): reminder banner, quick-add buttons,
 * Today's Health timeline, the weekly Nutrition Balance scorecard, water and
 * the weight mini chart. Glucose waits for Health Connect (v2), the Meal
 * button for the food journal (phase 3) and Photo for progress photos (phase 5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenWeightChart: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
    profileVm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val profileState by profileVm.state.collectAsStateWithLifecycle()
    var addWeight by remember { mutableStateOf(false) }
    var addNote by remember { mutableStateOf(false) }
    var addWater by remember { mutableStateOf(false) }
    var openNote by remember { mutableStateOf<Note?>(null) }
    var openMeal by remember { mutableStateOf<Meal?>(null) }
    var showHelp by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        state.reminders.forEach { r -> ReminderRow(r.title, r.body, formatDateTime(r.scheduledFor)) { vm.completeReminder(r) } }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickAdd("Weight", Color(0xFF007AFF)) { addWeight = true }
            QuickAdd("Note", Orange) { addNote = true }
        }
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = vm::refresh, modifier = Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Panel {
                    PanelHeader(Icons.Outlined.MonitorHeart, Color(0xFFFF3B30), "Today's Health")
                    DayNav(state.selectedDay, vm::selectDay)
                    TodaysHealth(state.day?.meals.orEmpty(), state.notesForDay, state.selectedDay, { openMeal = it }, { openNote = it })
                }
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.BarChart, null, tint = ScoreColors.Green, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nutrition Balance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showHelp = true }) { Icon(Icons.Outlined.HelpOutline, "How this works") }
                    }
                    WeekNav(state.weekStart) { vm.selectDay(it) }
                    val summary = vm.summary(state, profile)
                    WeekdayStrip(summary) { vm.selectDay(it) }
                    WeeklyBalanceBar(summary)
                    MacroBalanceBars(summary)
                }
                Panel {
                    WaterBar(state.day?.effectiveWaterAmount ?: 0.0, profile?.effectiveWaterGoal ?: 80.0, state.savingWater) { addWater = true }
                }
                Panel(Modifier.clickable(onClick = onOpenWeightChart)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MonitorWeight, null, tint = Color(0xFF007AFF), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Weight", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        profileState.weights.firstOrNull()?.let { Text("${formatWeight(it.weight)} lbs", fontWeight = FontWeight.SemiBold) }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open weight chart")
                    }
                    WeightMini(profileState.weights.mapNotNull { e -> runCatching { Instant.parse(e.date) }.getOrNull()?.let { it to e.weight } }, profile?.goalWeight)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (addWeight) AddWeightDialog(profileState.weights.firstOrNull()?.weight ?: profile?.currentWeight ?: 150.0, profileVm) { addWeight = false; vm.refresh() }
    if (addNote) AddNoteDialog(state.selectedDay, onSave = { text, cat, at, done -> vm.addNote(text, cat, at, done) }) { addNote = false }
    if (addWater) AddWaterDialog(onSave = { oz, done -> vm.addWater(oz, done) }) { addWater = false }
    if (showHelp) NutritionBalanceHelp { showHelp = false }
    openNote?.let { n ->
        AlertDialog(
            onDismissRequest = { openNote = null },
            icon = { Icon(categoryIcon(n.category), null, tint = Orange) },
            title = { Text(categoryName(n.category)) },
            text = { Column { Text(formatDateTime(n.date), style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)); Text(n.text) } },
            confirmButton = { TextButton(onClick = { openNote = null }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { vm.deleteNote(n); openNote = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        )
    }
    openMeal?.let { m ->
        AlertDialog(
            onDismissRequest = { openMeal = null },
            icon = { Icon(Icons.Filled.Restaurant, null, tint = Purple) },
            title = { Text(m.name ?: "Meal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatDateTime(m.date), style = MaterialTheme.typography.bodySmall)
                    val n = m.nutrition
                    Text("${n.calories.toInt()} cal · P ${n.protein.toInt()}g · C ${n.carbs.toInt()}g · F ${n.fats.toInt()}g", fontWeight = FontWeight.SemiBold)
                    m.mealItems.forEach { Text("• ${it.name}") }
                    m.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text("Editing meals comes with the food journal.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { openMeal = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PanelBg).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun PanelHeader(icon: ImageVector, tint: Color, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Colored "+ Label" button (iOS ColoredNavButton). */
@Composable
private fun QuickAdd(label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, null, tint = Color.White)
        }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ReminderRow(title: String, body: String, time: String, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Notifications, null, tint = Orange)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onDone) { Text("Done") }
    }
    HorizontalDivider()
}

private val DAY_FMT = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
private fun DayNav(day: LocalDate, onChange: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(day.minusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
        Text(if (day == today) "Today" else day.format(DAY_FMT), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onChange(day.plusDays(1)) }, enabled = day < today) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
    }
}

@Composable
private fun WeekNav(weekStart: LocalDate, onChange: (LocalDate) -> Unit) {
    val thisWeek = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val fmt = DateTimeFormatter.ofPattern("MMM d")
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(weekStart.minusDays(7)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous week") }
        Text("${weekStart.format(fmt)} – ${weekStart.plusDays(6).format(fmt)}", fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onChange(minOf(weekStart.plusDays(7), LocalDate.now())) }, enabled = weekStart < thisWeek) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next week")
        }
    }
}

/**
 * The day's meals (purple) and notes (orange) on a timeline (iOS
 * DailyEventsChartView without the glucose line, which needs Health Connect).
 * The range hugs the events with 2 hours either side, inside the day.
 */
@Composable
private fun TodaysHealth(meals: List<Meal>, notes: List<Note>, day: LocalDate, onMeal: (Meal) -> Unit, onNote: (Note) -> Unit) {
    val zone = ZoneId.systemDefault()
    val dayStart = day.atStartOfDay(zone).toInstant()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
    val events = meals.mapNotNull { it.instant } + notes.mapNotNull { it.instant() }
    if (events.isEmpty()) {
        Text("Nothing logged for this day yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val pad = Duration.ofHours(2)
    var lo = maxOf(events.min().minus(pad), dayStart)
    var hi = minOf(events.max().plus(pad), dayEnd)
    if (lo >= hi) { lo = dayStart; hi = dayEnd }
    val span = (hi.epochSecond - lo.epochSecond).toFloat()
    fun frac(t: Instant) = ((t.epochSecond - lo.epochSecond) / span).coerceIn(0f, 1f)

    Column {
        // Icons above the lines; tap to open.
        Box(Modifier.fillMaxWidth().height(28.dp)) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth
                meals.forEach { m -> m.instant?.let { t ->
                    Icon(Icons.Filled.Restaurant, m.name ?: "Meal", tint = Purple,
                        modifier = Modifier.padding(start = (w * frac(t) - 11.dp).coerceAtLeast(0.dp)).size(22.dp).clickable { onMeal(m) })
                } }
                notes.forEach { n -> n.instant()?.let { t ->
                    Icon(categoryIcon(n.category), categoryName(n.category), tint = Orange,
                        modifier = Modifier.padding(start = (w * frac(t) - 11.dp).coerceAtLeast(0.dp)).size(22.dp).clickable { onNote(n) })
                } }
            }
        }
        val gray = MaterialTheme.colorScheme.outlineVariant
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            drawLine(gray, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            notes.forEach { n -> n.instant()?.let { t -> val x = size.width * frac(t)
                drawLine(Orange.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))) } }
            meals.forEach { m -> m.instant?.let { t -> val x = size.width * frac(t)
                drawLine(Purple.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 9f))) } }
        }
        val timeFmt = DateTimeFormatter.ofPattern("h a")
        Row(Modifier.fillMaxWidth()) {
            Text(lo.atZone(zone).format(timeFmt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(hi.atZone(zone).format(timeFmt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
            notes.take(3).forEach { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(categoryIcon(n.category), null, tint = Orange, modifier = Modifier.size(14.dp))
                    Text(" ${categoryName(n.category)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (notes.size > 3) Text("+${notes.size - 3} more", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (meals.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Restaurant, null, tint = Purple, modifier = Modifier.size(14.dp))
                Text(" ${meals.size} meal${if (meals.size == 1) "" else "s"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Water: 40 of 80oz" bar with a plus button (iOS WaterIntakeControl). */
@Composable
fun WaterBar(amount: Double, goal: Double, saving: Boolean, onAdd: () -> Unit) {
    val blue = Color(0xFF007AFF)
    val g = if (goal > 0) goal else 80.0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(6.dp)).background(blue.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth((amount / g).coerceIn(0.0, 1.0).toFloat()).height(24.dp).background(blue))
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Water: ${Math.round(amount)} of ${Math.round(g)}oz", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (amount >= g) Icon(Icons.Filled.Check, "Goal met", tint = ScoreColors.Green, modifier = Modifier.size(16.dp).background(Color.White, RoundedCornerShape(3.dp)))
            }
        }
        IconButton(onClick = onAdd, enabled = !saving) {
            if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.AddCircle, "Log water", tint = blue)
        }
    }
}

@Composable
private fun WeightMini(points: List<Pair<Instant, Double>>, goal: Double?) {
    val sorted = points.sortedBy { it.first }.takeLast(30)
    if (sorted.isEmpty()) {
        Text("No weight entries yet. Tap + Weight to add one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        val vals = sorted.map { it.second } + listOfNotNull(goal)
        val minY = vals.min() - 3; val maxY = vals.max() + 3
        fun y(v: Double) = (size.height - (v - minY) / (maxY - minY) * size.height).toFloat()
        fun x(i: Int) = if (sorted.size == 1) size.width / 2 else size.width * i / (sorted.size - 1)
        goal?.let { drawLine(RedMain, Offset(0f, y(it)), Offset(size.width, y(it)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))) }
        for (i in 1 until sorted.size) drawLine(Color(0xFF007AFF), Offset(x(i - 1), y(sorted[i - 1].second)), Offset(x(i), y(sorted[i].second)), 4f)
        sorted.indices.forEach { drawCircle(Color(0xFF007AFF), 5f, Offset(x(it), y(sorted[it].second))) }
    }
}

@Composable
fun AddWaterDialog(onSave: (Double, (String?) -> Unit) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("8") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add Water") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(8, 16, 24, 32).forEach { oz -> TextButton(onClick = { text = "$oz" }) { Text("$oz") } }
                }
                OutlinedTextField(text, { v -> text = v.filter(Char::isDigit).take(3) }, label = { Text("Amount") }, suffix = { Text("oz") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                val oz = text.toDoubleOrNull()
                if (oz == null || oz < 4 || oz > 100) { error = "Please enter an amount between 4 and 100 oz"; return@TextButton }
                saving = true
                onSave(oz) { err -> saving = false; if (err == null) onClose() else error = err }
            }) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddNoteDialog(day: LocalDate, onSave: (String, String, Instant, (String?) -> Unit) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("general") }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    // A note for a past day keeps the current time of day on that day, like iOS seeding with the viewed day.
    val at = if (day == LocalDate.now()) Instant.now()
        else day.atTime(java.time.LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(formatDateTime(at.toString()), style = MaterialTheme.typography.bodySmall)
                ExposedDropdownMenuBox(expanded, { expanded = it }) {
                    OutlinedTextField(categoryName(category), {}, readOnly = true, label = { Text("Category") },
                        leadingIcon = { Icon(categoryIcon(category), null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        NOTE_CATEGORIES.forEach { (raw, name, icon) ->
                            DropdownMenuItem(text = { Text(name) }, leadingIcon = { Icon(icon, null) }, onClick = { category = raw; expanded = false })
                        }
                    }
                }
                OutlinedTextField(text, { text = it }, label = { Text("Note") }, minLines = 3)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && text.isNotBlank(), onClick = {
                saving = true
                onSave(text, category, at) { err -> saving = false; if (err == null) onClose() else error = err }
            }) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun NutritionBalanceHelp(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Nutrition Balance") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Each dot is a day. The fill shows calories against that day's goal: green is on target or a small deficit, yellow is a little over (or well under), orange is well over. The ring shows protein.")
                Text("The bar adds up the finished days of the week. Left of center is a deficit (calories banked), right is a surplus. The blue TDEE line marks maintenance for the days so far.")
                Text("Every finished day needs at least one meal logged, or the bar stays grey. Today only counts once you go over.")
                Text("The P, C and F bars show the week's protein, carbs and fats against goal, including today.")
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Got it") } },
    )
}
