package co.topflighthealth.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.topflighthealth.app.domain.MacroDayStatus
import co.topflighthealth.app.domain.WeeklyNutritionSummary
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * The Nutrition Balance scorecard visuals, ported from iOS WeekdayStripView,
 * DayBalanceDot, WeeklyBalanceBar and MacroBalanceBars. The iOS bars use soft
 * gradients between zones; here each zone is a solid band, same thresholds.
 */

object ScoreColors {
    val Green = Color(0xFF34C759)
    val Yellow = Color(0xFFFFCC00)
    val Orange = Color(0xFFFF9500)
    val Red = Color(0xFFFF3B30)
    val Blue = Color(0xFF007AFF)
    val Gray = Color(0xFF8E8E93)
    /** Darker yellow for text (iOS ZoneConfig.textYellow). */
    val TextYellow = Color(0xFFB88700)
}

private val WEEKDAY_INITIALS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun WeekdayStrip(summary: WeeklyNutritionSummary, onTapDay: (java.time.LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        summary.days.forEachIndexed { i, day ->
            Column(
                Modifier.weight(1f).clickable(enabled = !day.isFuture) { onTapDay(day.day) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(WEEKDAY_INITIALS[i], fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                DayBalanceDot(day)
                Text("${day.day.dayOfMonth}", fontSize = 11.sp,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (day.isFuture) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Fill = calorie balance for the day; ring = protein progress (iOS DayBalanceDot). */
@Composable
fun DayBalanceDot(day: WeeklyNutritionSummary.DaySummary) {
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(32.dp)) {
            val dot = 26.dp.toPx(); val ring = 2.5.dp.toPx()
            val c = center
            if (day.isToday) drawCircle(accent, radius = (dot + 6.dp.toPx()) / 2, center = c, style = Stroke(1.5.dp.toPx()))
            drawCircle(ScoreColors.Gray.copy(alpha = if (day.isFuture) 0.15f else 0.25f), radius = dot / 2 - ring / 2, center = c, style = Stroke(ring))
            val showRing = !day.isFuture && (day.hasDoc || day.isToday) && day.goalProtein > 0
            if (showRing) {
                val r = dot / 2 - ring / 2
                drawArc(proteinRingColor(day), -90f, (360 * day.proteinProgress).toFloat(), false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(ring, cap = StrokeCap.Round))
            }
            val fillR = (dot - ring * 2 - 2.dp.toPx()) / 2
            when {
                day.isFuture -> Unit
                day.isCompleted && !day.accountedFor -> drawCircle(ScoreColors.Gray.copy(alpha = 0.2f), fillR, c)
                else -> drawCircle(calorieDotColor(day), fillR, c)
            }
        }
        if (day.isCompleted && !day.accountedFor) Text("?", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ScoreColors.Gray)
    }
}

fun calorieDotColor(day: WeeklyNutritionSummary.DaySummary): Color {
    val balance = day.calorieBalance
    if (day.goalCalories <= 0) return ScoreColors.Gray
    if (day.isCompleted && balance < 0 && abs(balance) > 300) return ScoreColors.Yellow
    if (balance <= 0) return ScoreColors.Green
    return if (balance / day.goalCalories <= 0.15) ScoreColors.Yellow else ScoreColors.Orange
}

fun proteinRingColor(day: WeeklyNutritionSummary.DaySummary): Color {
    if (!day.isCompleted || day.proteinTarget.displayGoal <= 0) return ScoreColors.Blue
    if (day.proteinBarStatus().isRed) return ScoreColors.Red
    return if (day.proteinTarget.displayGoal - day.totalProtein > MacroDayStatus.Tolerance.PROTEIN) ScoreColors.Red else ScoreColors.Blue
}

/** Zone thresholds for the weekly calorie bar, by objective (iOS WeeklyBalanceBar.ZoneConfig). */
class BalanceZones(private val s: WeeklyNutritionSummary) {
    /** Boundaries from center outward: (threshold, color past it). The first band is green. */
    val deficit: List<Pair<Double, Color>>
    val surplus: List<Pair<Double, Color>>

    init {
        val dip = s.daysInPlay.toDouble(); val tdee = s.dailyTDEE; val goal = s.dailyGoal; val tick = s.tdeeTickOffset
        when (s.userObjective ?: "Lose Fat") {
            "Maintain" -> {
                deficit = listOf(200.0 to ScoreColors.Yellow, 400.0 to ScoreColors.Red)
                surplus = deficit
            }
            "Build" -> {
                deficit = listOf((goal - tdee * 1.10) * dip to ScoreColors.Yellow, abs(tick) to ScoreColors.Red)
                surplus = listOf((tdee * 1.20 - goal) * dip to ScoreColors.Yellow, (tdee + 500 - goal) * dip to ScoreColors.Red)
            }
            else -> {
                deficit = listOf(500.0 to ScoreColors.Yellow, 750.0 to ScoreColors.Red)
                surplus = listOf(tick * 0.5 to ScoreColors.Yellow, tick to ScoreColors.Red)
            }
        }
    }

    val deficitMax: Double
        get() {
            var m = deficit.maxOfOrNull { it.first } ?: 800.0
            if (s.tdeeTickOffset < 0) m = max(m, abs(s.tdeeTickOffset))
            return max(m, 800.0) * 1.15
        }
    val surplusMax: Double
        get() {
            var m = surplus.maxOfOrNull { it.first } ?: 800.0
            if (s.tdeeTickOffset > 0) m = max(m, s.tdeeTickOffset)
            return max(m, 800.0) * 1.15
        }

    fun colorFor(balance: Double): Color {
        if (abs(balance) < 0.5) return ScoreColors.Gray
        val zones = if (balance < 0) deficit else surplus
        val v = abs(balance)
        zones.forEachIndexed { i, (t, _) -> if (v <= t) return if (i == 0) ScoreColors.Green else zones[i - 1].second }
        return zones.lastOrNull()?.second ?: ScoreColors.Green
    }

    /** x in [0, width] for a balance; center is goal pace. */
    fun x(balance: Double, width: Float): Float {
        val half = width / 2
        return if (balance < 0) half - half * min(abs(balance) / deficitMax, 1.0).toFloat()
        else half + half * min(balance / surplusMax, 1.0).toFloat()
    }
}

@Composable
fun WeeklyBalanceBar(s: WeeklyNutritionSummary) {
    val zones = BalanceZones(s)
    val measurer = rememberTextMeasurer()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!s.barEnabled) {
            Text("Track all days to view your nutrition balance", style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (text, color) = readout(s, zones)
                Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.weight(1f))
                if (abs(s.liveBalance - s.lockedBalance) > 0.5) {
                    val c = zones.colorFor(s.liveBalance)
                    Text("-${(s.liveBalance - s.lockedBalance).toInt()} Today", style = MaterialTheme.typography.bodySmall,
                        color = (if (c == ScoreColors.Yellow) ScoreColors.TextYellow else c).copy(alpha = 0.9f))
                }
            }
        }
        Box {
            Canvas(Modifier.fillMaxWidth().height(42.dp)) {
                val barH = 22.dp.toPx(); val top = size.height - barH - 4.dp.toPx(); val w = size.width
                val a = if (s.barEnabled) 1f else 0.18f
                // Zone backgrounds, then the locked fill from the center.
                drawZones(zones, w, top, barH, alpha = 0.12f * a)
                val locked = s.lockedBalance
                if (abs(locked) > 0.5) {
                    drawBand(zones, 0.0, locked, w, top, barH, 0.85f * a)
                }
                // Today's change, as a thinner band.
                if (abs(s.liveBalance - locked) > 0.5) {
                    val lo = min(locked, s.liveBalance); val hi = max(locked, s.liveBalance)
                    drawBand(zones, lo, hi, w, top + barH / 4, barH / 2, 0.35f * a)
                }
                drawRect(Color.White.copy(alpha = a), Offset(w / 2 - 1.5f, top - 4.dp.toPx()), Size(3f, barH + 8.dp.toPx()))
                if (abs(s.tdeeTickOffset) > 0.5 && s.barEnabled) {
                    val tx = zones.x(s.tdeeTickOffset, w)
                    if (tx > 6 && tx < w - 6) {
                        drawRect(ScoreColors.Blue.copy(alpha = 0.85f), Offset(tx - 1f, top - 4.dp.toPx()), Size(2f, barH + 8.dp.toPx()))
                        val label = measurer.measure("TDEE", TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = ScoreColors.Blue))
                        drawText(label, topLeft = Offset(tx - label.size.width / 2f, 0f))
                    }
                }
                if (!s.barEnabled) drawRoundRect(ScoreColors.Gray.copy(alpha = 0.55f), Offset(0f, top), Size(w, barH), CornerRadius(6.dp.toPx()))
            }
            if (!s.barEnabled) Text("?", fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.Center))
        }
        Row(Modifier.fillMaxWidth().alpha(if (s.barEnabled) 1f else 0.18f)) {
            listOf("Deficit", "Goal", "Surplus").forEachIndexed { i, label ->
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = listOf(TextAlign.Start, TextAlign.Center, TextAlign.End)[i], modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun readout(s: WeeklyNutritionSummary, zones: BalanceZones): Pair<String, Color> {
    val bal = s.lockedBalance
    val c = zones.colorFor(bal)
    val build = s.userObjective == "Build"
    return when {
        abs(bal) < 0.5 -> "On pace" to ScoreColors.Gray
        bal < 0 -> {
            val v = abs(bal).toInt()
            when (c) {
                ScoreColors.Green -> (if (build) "+$v - Looking good" else "+$v - Cals banked") to ScoreColors.Green
                ScoreColors.Yellow -> "+$v - Make sure you eat enough" to ScoreColors.TextYellow
                else -> "+$v - You're under-eating" to ScoreColors.Red
            }
        }
        else -> {
            val v = bal.toInt()
            when (c) {
                ScoreColors.Green -> (if (build) "-$v Looking good" else "-$v Take note") to ScoreColors.Green
                ScoreColors.Yellow -> "-$v — Ease up" to ScoreColors.TextYellow
                else -> "-$v — Too much" to ScoreColors.Red
            }
        }
    }
}

private fun DrawScope.drawZones(z: BalanceZones, w: Float, top: Float, h: Float, alpha: Float) {
    drawBand(z, -z.deficitMax * 2, z.surplusMax * 2, w, top, h, alpha)
}

/** Fills [lo, hi] (balances) using each zone's color for the part that falls in it. */
private fun DrawScope.drawBand(z: BalanceZones, a: Double, b: Double, w: Float, top: Float, h: Float, alpha: Float) {
    val lo = min(a, b); val hi = max(a, b)
    val edges = mutableListOf<Pair<Pair<Double, Double>, Color>>()
    var prev = 0.0
    z.deficit.forEachIndexed { i, (t, _) ->
        edges += (-t to -prev) to (if (i == 0) ScoreColors.Green else z.deficit[i - 1].second); prev = t
    }
    edges += (Double.NEGATIVE_INFINITY to -prev) to (z.deficit.lastOrNull()?.second ?: ScoreColors.Green)
    prev = 0.0
    z.surplus.forEachIndexed { i, (t, _) ->
        edges += (prev to t) to (if (i == 0) ScoreColors.Green else z.surplus[i - 1].second); prev = t
    }
    edges += (prev to Double.POSITIVE_INFINITY) to (z.surplus.lastOrNull()?.second ?: ScoreColors.Green)
    edges.forEach { (range, color) ->
        val sLo = max(range.first, lo); val sHi = min(range.second, hi)
        if (sHi > sLo) {
            val x0 = z.x(sLo, w); val x1 = z.x(sHi, w)
            drawRect(color.copy(alpha = alpha), Offset(min(x0, x1), top), Size(abs(x1 - x0), h))
        }
    }
}

/** Weekly protein/carb/fat balances in grams (iOS MacroBalanceBars). */
@Composable
fun MacroBalanceBars(s: WeeklyNutritionSummary) {
    val dip = s.daysInPlay.toDouble()
    val pYellow = 25.0 * dip; val pGreen = 10.0 * dip; val cfYellow = 5.0 * dip
    val all = listOf(s.lockedProteinBalance, s.lockedCarbsBalance, s.lockedFatsBalance,
        s.liveProteinBalance, s.liveCarbsBalance, s.liveFatsBalance, pYellow, pGreen, cfYellow)
    val half = max((all.maxOf { abs(it) }) * 1.2, 30.0)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.alpha(if (s.anyMacroShowable) 1f else 0.18f), verticalAlignment = Alignment.Bottom) {
            Text("Weekly Macro Balance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Text("(Including Today)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MacroBar("P", s.lockedProteinBalance, s.liveProteinBalance, pGreen, pYellow, half, s.macroBarShowable(s.liveProteinBalance))
        MacroBar("C", s.lockedCarbsBalance, s.liveCarbsBalance, 0.0, cfYellow, half, s.macroBarShowable(s.liveCarbsBalance))
        MacroBar("F", s.lockedFatsBalance, s.liveFatsBalance, 0.0, cfYellow, half, s.macroBarShowable(s.liveFatsBalance))
        if (s.macroBarsPartiallyEnabled) {
            val n = s.unaccountedCompletedDayCount
            Text("$n day${if (n == 1) "" else "s"} not tracked — surplus shown is at least this much.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun macroColor(v: Double, green: Double, yellow: Double) = when {
    v <= green -> ScoreColors.Green
    v <= yellow -> ScoreColors.Yellow
    else -> ScoreColors.Red
}

@Composable
private fun MacroBar(label: String, locked: Double, live: Double, green: Double, yellow: Double, half: Double, showable: Boolean) {
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    Row(Modifier.alpha(if (showable) 1f else 0.18f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
        Canvas(Modifier.weight(1f).height(12.dp)) {
            val w = size.width; val h = size.height; val c = w / 2
            fun x(v: Double) = c + c * max(-1.0, min(1.0, v / half)).toFloat()
            drawRoundRect(ScoreColors.Gray.copy(alpha = 0.12f), size = size, cornerRadius = CornerRadius(3.dp.toPx()))
            if (abs(locked) >= 0.3) {
                val x1 = x(locked)
                drawRect(macroColor(locked, green, yellow).copy(alpha = 0.85f), Offset(min(c, x1), 0f), Size(abs(x1 - c), h))
            }
            if (abs(live - locked) >= 0.3) {
                val sameSide = (locked < 0 && live < 0) || (locked > 0 && live > 0)
                val x0 = x(locked); val x1 = x(live)
                val color = if (sameSide && abs(live) < abs(locked)) Color.White.copy(alpha = 0.3f) else macroColor(live, green, yellow).copy(alpha = 0.35f)
                drawRect(color, Offset(min(x0, x1), 0f), Size(abs(x1 - x0), h))
            }
            drawRect(line, Offset(c - 0.75f, 0f), Size(1.5f, h))
        }
        val c = macroColor(live, green, yellow)
        Text(gramString(live), fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End,
            color = if (c == ScoreColors.Yellow) ScoreColors.TextYellow else c, modifier = Modifier.width(48.dp))
    }
}

/** Deficit shows as "+Ng" (room to eat), surplus as "-Ng" (eat less), as on iOS. */
fun gramString(v: Double): String = when {
    abs(v) < 0.3 -> "0g"
    v < 0 -> "+${abs(v).toInt()}g"
    else -> "-${abs(v).toInt()}g"
}
