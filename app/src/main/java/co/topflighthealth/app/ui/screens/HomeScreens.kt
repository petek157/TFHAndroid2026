package co.topflighthealth.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.topflighthealth.app.BuildConfig
import co.topflighthealth.app.ui.AppState

/** Stand-in for a tab whose real screen comes in a later phase. */
@Composable
fun ComingSoonScreen(title: String, phase: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Coming in $phase", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Phase 1 profile: who is signed in, membership state and sign out. The full
 * profile editor arrives in phase 2. Free users only get this tab, as on iOS.
 */
@Composable
fun ProfileScreen(state: AppState, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(state.user?.displayName ?: "Loading…", style = MaterialTheme.typography.titleLarge)
                state.user?.email?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        MembershipCard(state)
        if (state.error != null) {
            Text(state.error, color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign Out") }
        if (BuildConfig.ENV != "prod") {
            Text(
                "Dev build · ${BuildConfig.API_BASE_URL}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Membership state. Android sells nothing in the app (no Google Play billing),
 * so non-members are told to join on the website. Check Google Play's current
 * rules before adding a link or button here.
 */
@Composable
private fun MembershipCard(state: AppState) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Membership", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when {
                state.membership == null -> Text("Checking…")
                state.isMember -> {
                    Text("Active · ${state.membership.subscription.replaceFirstChar { it.uppercase() }}")
                }
                else -> Text(
                    "You don't have an active membership. Visit topflighthealth.co to become a member, then come back to the app.",
                    textAlign = TextAlign.Start,
                )
            }
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://topflighthealth.co/privacy")))
            }) { Text("Privacy Policy") }
        }
    }
}
