package co.topflighthealth.app.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.topflighthealth.app.data.Profile
import co.topflighthealth.app.ui.screens.ComingSoonScreen

private enum class NutritionSection { Macros, Journal, Pantry }

/**
 * The Nutrition tab (iOS NutritionView): Macros, Journal and Pantry buttons
 * across the top, Journal first. Pantry shows only with macro tracking on.
 */
@Composable
fun NutritionTab(profile: Profile?, modifier: Modifier = Modifier) {
    var section by rememberSaveable { mutableStateOf(NutritionSection.Journal) }
    val showPantry = profile?.macroTracking == true
    if (section == NutritionSection.Pantry && !showPantry) section = NutritionSection.Journal

    var inMealFlow by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        // The add/edit meal pages take the whole tab, like the iOS full-screen cover.
        if (!inMealFlow) Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            NavButton(Icons.Outlined.Eco, "Macros", Color(0xFF34C759), section == NutritionSection.Macros) { section = NutritionSection.Macros }
            NavButton(Icons.AutoMirrored.Outlined.MenuBook, "Journal", Color(0xFF007AFF), section == NutritionSection.Journal) { section = NutritionSection.Journal }
            if (showPantry) NavButton(Icons.Outlined.Kitchen, "Pantry", Color(0xFFFF9500), section == NutritionSection.Pantry) { section = NutritionSection.Pantry }
        }
        when (section) {
            NutritionSection.Macros -> MacrosScreen()
            NutritionSection.Journal -> JournalScreen(showMacros = profile?.macroTracking == true, onFlowActive = { inMealFlow = it })
            NutritionSection.Pantry -> ComingSoonScreen("Pantry", "phase 4")
        }
    }
}

/** Colored square icon button with a label; the selected one gets a ring (iOS ColoredNavButton). */
@Composable
fun NavButton(icon: ImageVector, label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.size(50.dp)
                .border(2.dp, if (selected) color.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
                .padding(3.dp).clip(RoundedCornerShape(10.dp)).background(color),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White.copy(alpha = if (selected) 0.6f else 1f)) }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
    }
}
