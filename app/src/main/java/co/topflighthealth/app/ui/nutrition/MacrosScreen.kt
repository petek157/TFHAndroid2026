package co.topflighthealth.app.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.topflighthealth.app.domain.MacroMath
import co.topflighthealth.app.ui.profile.PillSegments
import co.topflighthealth.app.ui.profile.ProfileViewModel
import co.topflighthealth.app.ui.theme.BlueMain
import co.topflighthealth.app.ui.theme.BlueSecondary

/**
 * The Macros page (iOS MacroView): objective and balance of calories, which
 * the client can change, and the resulting BMR, TDEE, calories and macros.
 * The coach-only protein/carb multipliers stay off, as on iOS for clients.
 */
@Composable
fun MacrosScreen(vm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val p = state.profile ?: return Text("Loading…", modifier = Modifier.padding(16.dp))
    val calc = MacroMath.calculate(p.macroInput)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Section("Your Objective") {
            PillSegments(listOf("Lose Fat", "Maintain", "Build"), p.objective ?: "Lose Fat", vm::setObjective,
                primary = BlueMain, secondary = BlueSecondary.copy(alpha = 0.25f))
        }
        if (calc == null) {
            Text("Finish your profile (birthday, height, sex, weight, goal weight and activity level) to see your macros.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Section("Caloric Need") {
                Row(Modifier.fillMaxWidth()) {
                    Stat("BMR", "${calc.bmr.toInt()}"); Stat("TDEE", "${calc.tdee.toInt()}"); Stat("Calories", "${calc.calcTotalCalories.toInt()}")
                }
                val delta = calc.calorieDeltaVsTdee.toInt()
                Text(when { delta < 0 -> "Deficit −${-delta}"; delta > 0 -> "Surplus +$delta"; else -> "At TDEE" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            }
            if (calc.isProteinAutoReduced) {
                Text("Protein is ${"%.2f".format(calc.proteinMultiplier)} g per lb of goal weight so your calorie target stays below TDEE.", fontSize = 13.sp)
            }
            if (calc.isLoseFatGoalAtOrAboveTdee) {
                Text("You're already at the minimum safe protein, carbs, and fats. Creating a calorie deficit requires being more physically active.", fontSize = 13.sp)
            }
        }
        Section("Balance of Calories") {
            PillSegments(listOf("Carbs", "Fats", "Both"), p.balanceOfCalories ?: "Both", vm::setBalanceOfCalories,
                primary = BlueMain, secondary = BlueSecondary.copy(alpha = 0.25f))
        }
        if (calc != null) {
            Section("Macros") {
                Row(Modifier.fillMaxWidth()) {
                    Stat("Protein", "${calc.protein.toInt()} g", "${calc.protein.toInt() * 4} cals",
                        if (calc.proteinMultiplier < 1.0) "${"%.2f".format(calc.proteinMultiplier)} g/lb" else null)
                    Stat("Carbs", "${calc.carbs.toInt()} g", "${calc.carbs.toInt() * 4} cals")
                    Stat("Fats", "${calc.fats.toInt()} g", "${calc.fats.toInt() * 9} cals")
                }
            }
        }
        if (state.saving) Text("Saving…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(label: String, value: String, sub: String? = null, sub2: String? = null) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        sub?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        sub2?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
