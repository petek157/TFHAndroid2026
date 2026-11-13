package co.topflighthealth.app.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.topflighthealth.app.api.WeightEntry
import co.topflighthealth.app.ui.theme.BlueMain
import co.topflighthealth.app.ui.theme.RedMain
import java.time.Duration
import java.time.Instant

/** Same bounds as AddWeightEntryView on iOS. */
private const val MIN_WEIGHT = 50.0
private const val MAX_WEIGHT = 700.0

@Composable
fun AddWeightDialog(start: Double, vm: ProfileViewModel, onClose: () -> Unit) {
    var text by remember { mutableStateOf(formatWeight(start)) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Weight Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' }.take(6) },
                    label = { Text("Weight") },
                    suffix = { Text("lbs") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text("Saved with today's date and time.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                val w = text.toDoubleOrNull()
                if (w == null || w < MIN_WEIGHT || w > MAX_WEIGHT) {
                    error = "Please enter a weight between ${MIN_WEIGHT.toInt()} and ${MAX_WEIGHT.toInt()} lbs"
                    return@TextButton
                }
                saving = true
                vm.addWeight(w, Instant.now()) { err ->
                    saving = false
                    if (err == null) onClose() else error = err
                }
            }) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

private enum class Range(val label: String, val days: Long?) { Week("7D", 7), Month("30D", 30), Quarter("90D", 90), All("All", null) }

/** The weight chart page (WeightChartView on iOS): entries over time against the goal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightChartScreen(onBack: () -> Unit, vm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    var range by remember { mutableStateOf(Range.Month) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<WeightEntry?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadWeights() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weight") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add entry") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Range.entries.forEach { FilterChip(selected = it == range, onClick = { range = it }, label = { Text(it.label) }) }
            }
            val cutoff = range.days?.let { Instant.now().minus(Duration.ofDays(it)) }
            val points = state.weights
                .mapNotNull { e -> runCatching { Instant.parse(e.date) }.getOrNull()?.let { it to e.weight } }
                .filter { cutoff == null || it.first >= cutoff }
                .sortedBy { it.first }
            if (points.isEmpty()) {
                Text("No weight data available", modifier = Modifier.padding(vertical = 32.dp))
            } else {
                WeightLineChart(points, state.profile?.goalWeight, Modifier.fillMaxWidth().height(220.dp).padding(vertical = 12.dp))
            }
            state.profile?.goalWeight?.let {
                Text("Goal: ${it.toInt()} lbs", color = RedMain, style = MaterialTheme.typography.labelLarge)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.weights, key = { it.id }) { e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${formatWeight(e.weight)} lbs", style = MaterialTheme.typography.titleMedium)
                            Text(formatDateTime(e.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { deleting = e }) { Icon(Icons.Filled.Delete, "Delete entry") }
                    }
                }
            }
        }
    }
    if (adding) AddWeightDialog(state.weights.firstOrNull()?.weight ?: state.profile?.currentWeight ?: 150.0, vm) { adding = false }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete entry?") },
            text = { Text("Are you sure you want to delete the weight entry from ${formatDateTime(e.date)}?") },
            confirmButton = { TextButton(onClick = { vm.deleteWeight(e); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** A simple line chart: weight in blue, goal as a dashed red line. */
@Composable
private fun WeightLineChart(points: List<Pair<Instant, Double>>, goal: Double?, modifier: Modifier) {
    Canvas(modifier) {
        val values = points.map { it.second } + listOfNotNull(goal)
        val minY = values.min() - 3
        val maxY = values.max() + 3
        val t0 = points.first().first.epochSecond.toDouble()
        val span = (points.last().first.epochSecond - t0).coerceAtLeast(1.0)
        fun x(t: Instant) = if (points.size == 1) size.width / 2 else ((t.epochSecond - t0) / span * size.width).toFloat()
        fun y(v: Double) = (size.height - (v - minY) / (maxY - minY) * size.height).toFloat()

        goal?.let {
            drawLine(RedMain, Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
        }
        val path = Path()
        points.forEachIndexed { i, (t, v) -> if (i == 0) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v)) }
        drawPath(path, BlueMain, style = Stroke(width = 5f))
        points.forEach { (t, v) -> drawCircle(BlueMain, radius = 7f, center = Offset(x(t), y(v))) }
    }
}
