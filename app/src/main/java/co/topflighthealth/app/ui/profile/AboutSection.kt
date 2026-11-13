package co.topflighthealth.app.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Source links shown in the iOS AboutView (AppConstants on iOS).
private const val SOURCE_BMR = "https://pubmed.ncbi.nlm.nih.gov/2305711/"
private const val SOURCE_TDEE = "https://reference.medscape.com/calculator/846/mifflin-st-jeor-equation"
private const val SOURCE_FAT = "https://www.ncbi.nlm.nih.gov/books/NBK594740/"
private const val SOURCE_CARBS = "https://cdn.realfood.gov/Scientific%20Report_508.pdf"

/** The About page from iOS (AboutView), without the glucose part, which waits for v2. */
@Composable
fun AboutSection() {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Heading("Health")
        Item("Weight Chart", "The weight chart is simply the dated weight entries that you provided charted next to your entered goal weight.")

        Heading("Nutrition")
        Item("Basal Metabolic Rate (BMR)", "We apply the Mifflin–St Jeor equation to estimate resting energy needs. This is the standard approach used in modern adult nutrition planning.")
        TextButton(onClick = { open(SOURCE_BMR) }) { Text("Source: Mifflin-St Jeor Study (1990)") }
        Item("Total Daily Energy Expenditure (TDEE)", "Your activity selection converts BMR into TDEE using the standard Physical Activity Level multipliers published in the Institute of Medicine's Dietary Reference Intakes.")
        TextButton(onClick = { open(SOURCE_TDEE) }) { Text("Source: IOM Dietary Reference Intakes") }
        Item(
            "Macro Calculation Minimum Healthy",
            "Calories and macro planning never drop below floors that keep hormones and training fuel in a healthy range. Protein starts at 1 g per lb of goal weight, and may auto-reduce toward 0.7 g per lb when that 1 g floor would put Lose Fat at or above TDEE (never more than a 500 calorie deficit). We then allow at least 130 g of carbohydrates (a coach may lower that toward 110 g) and a fat floor of 50 g for men or 40 g for women before distributing remaining calories. If you are paying back a large week surplus, live Journal targets may step slightly below those planning mins (carbs −20 g, fats −9 g) and use the same 0.7 g per lb protein floor so payback never means starving.",
        )
        TextButton(onClick = { open(SOURCE_CARBS) }) { Text("Carbs Source") }
        TextButton(onClick = { open(SOURCE_FAT) }) { Text("Fat Source") }

        Heading("Activity Level")
        Item("Sedentary", "We do not use the term sedentary in a negative manner. In fact probably 98% of people, myself included for the purpose of this calculator are sedentary. These are people that outside of training sessions get say 9,000 steps or less in an average day.\nEx: Office Workers")
        Item("Active", "This is considered anyone who gets roughly 10,000 steps a day outside of their training.\nEx: Nurse, waiter, etc.")
        Item("Very Active", "These crazy folks are exercising one or twice a day plus have a manual labor job. Or maybe are something like an Ironman athlete.\nEx: Construction worker")

        Heading("Balance of Calories")
        Item(null, "After accounting for the calories from protein, these are used to calculate the balance of your calories. Select based on your preference. Some people prefer it to lean towards carbs and some fat. Or an even split.")
        Item("Carbs", "We will lean towards more carbs.")
        Item("Fats", "The remaining calories will be heavier on fats.")
        Item("Both", "This will be an even split of carbs and fats.")

        Heading("Disclaimer")
        Item(null, "Peter Koruga and Jenelle Aspinwall owners of Top Flight Health are health coaches and personal trainers. We are not licensed physicians, dietitians, therapists, or medical professionals.")
        Item(null, "The content, information and guidence provided by Top Flight Health are for educational and informational purposes only. It does not constitute medical advice, diagnosis, treatment, or a substitute for professional healthcare. We do not supervise, interpret, or provide medical oversight.")
        Item(null, "Consult your doctor before starting any exercise, nutrition, wellness program, or health testing, especially if you have health conditions. Participation in any services or use of any products or services including this app are at your own risk.")
    }
}

@Composable
private fun Heading(text: String) =
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Item(title: String?, body: String) {
    Column {
        title?.let { Text(it, fontWeight = FontWeight.SemiBold) }
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
