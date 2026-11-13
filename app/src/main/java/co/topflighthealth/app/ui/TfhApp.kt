package co.topflighthealth.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.topflighthealth.app.ui.screens.ComingSoonScreen
import co.topflighthealth.app.ui.screens.ProfileScreen
import co.topflighthealth.app.ui.screens.SignInScreen

/** Tabs in the same order as the iOS tab bar (Dashboard sits in the middle there too). */
private enum class Tab(val label: String, val icon: ImageVector) {
    Profile("Profile", Icons.AutoMirrored.Outlined.List),
    Messages("Messages", Icons.Outlined.ChatBubbleOutline),
    Dashboard("Dashboard", Icons.Outlined.Home),
    Nutrition("Nutrition", Icons.Outlined.EditNote),
}

@Composable
fun TfhApp(vm: AppViewModel = viewModel(factory = AppViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onSignInResult(it.data)
    }

    // Re-check membership each time the app comes back to the front, like iOS does.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    when {
        !state.signedIn -> SignInScreen(
            busy = state.signingIn,
            error = state.error,
            onSignIn = { vm.clearError(); signInLauncher.launch(vm.signInIntent()) },
        )
        state.user == null && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> MainTabs(state, onSignOut = vm::signOut)
    }
}

@Composable
private fun MainTabs(state: AppState, onSignOut: () -> Unit) {
    // Free users get the Profile tab only, as on iOS.
    val tabs = when {
        !state.isMember -> listOf(Tab.Profile)
        state.hasMessaging -> Tab.entries
        else -> Tab.entries - Tab.Messages
    }
    var selected by rememberSaveable { mutableStateOf(if (state.isMember) Tab.Dashboard else Tab.Profile) }
    if (selected !in tabs) selected = tabs.first()

    Scaffold(
        bottomBar = {
            if (tabs.size > 1) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selected,
                            onClick = { selected = tab },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (selected) {
            Tab.Profile -> ProfileScreen(state, onSignOut, modifier)
            Tab.Messages -> ComingSoonScreen("Messages", "phase 6", modifier)
            Tab.Dashboard -> ComingSoonScreen("Dashboard", "phase 2", modifier)
            Tab.Nutrition -> ComingSoonScreen("Nutrition", "phase 3", modifier)
        }
    }
}
