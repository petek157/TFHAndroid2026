package co.topflighthealth.app.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import co.topflighthealth.app.ui.theme.BlueMain
import co.topflighthealth.app.ui.theme.RedSecondary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.topflighthealth.app.BuildConfig
import co.topflighthealth.app.data.Profile
import co.topflighthealth.app.ui.AppState
import co.topflighthealth.app.ui.theme.RedMain
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class ProfileSection(val title: String) { Profile("Profile"), About("About"), Plan("Plan") }

private val ACTIVITY_LEVELS = listOf("Sedentary", "Active", "Very Active")
private val SEXES = listOf("Male", "Female")
private val WRIST_SIZES = generateSequence(4.0) { it + 0.125 }.takeWhile { it <= 12.0 }.toList()

/**
 * The Profile tab, laid out like iOS ProfileView: the title names the section,
 * and the colored quick-action buttons on the right switch between Profile,
 * About and Plan, or sign out. Free users only see this tab.
 */
@Composable
fun ProfileTab(
    app: AppState,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(ProfileSection.Profile) }
    // Plan is hidden for admins, as on iOS.
    val showPlan = !app.isAdmin
    if (section == ProfileSection.Plan && !showPlan) section = ProfileSection.Profile
    // iOS saves when you leave the profile; do the same.
    DisposableEffect(Unit) { onDispose { vm.saveNow() } }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(section.title, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Icons.Filled.Person, "Profile", BlueMain, section == ProfileSection.Profile) { section = ProfileSection.Profile }
                QuickAction(Icons.Outlined.Info, "About", Color(0xFFAF52DE), section == ProfileSection.About) { section = ProfileSection.About }
                if (showPlan) {
                    QuickAction(Icons.Outlined.StarOutline, "Plan", Color(0xFFFF9500), section == ProfileSection.Plan) { section = ProfileSection.Plan }
                }
                QuickAction(Icons.AutoMirrored.Filled.Logout, "Sign out", Color(0xFFFF3B30), selected = false) { vm.saveNow(); onSignOut() }
            }
        }
        when (section) {
            ProfileSection.Profile -> ProfileForm(state, vm, onActivityHelp = { section = ProfileSection.About })
            ProfileSection.About -> AboutSection()
            ProfileSection.Plan -> MembershipSection(app)
        }
    }
}

/** A 32 dp colored square with a white icon; the active one gets a half-opacity ring and a dimmed icon (iOS headerIcon). */
@Composable
private fun QuickAction(icon: ImageVector, label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .border(2.dp, if (selected) color.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(10.dp))
            .padding(3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = if (selected) 0.6f else 1f), modifier = Modifier.size(18.dp))
    }
}

/** The red pill segmented control used across the iOS app (CustomSegmentedControl). */
@Composable
fun PillSegments(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(RedSecondary.copy(alpha = 0.35f))) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) RedMain else Color.Transparent)
                    .clickable { onSelect(o) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, color = if (on) Color.White else RedMain, fontSize = 17.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun ProfileForm(state: ProfileUiState, vm: ProfileViewModel, onActivityHelp: () -> Unit) {
    val p = state.profile
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (p == null) {
            Text("Loading…")
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(p.firstname.orEmpty(), vm::setFirstName, label = { Text("First Name") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(p.lastname.orEmpty(), vm::setLastName, label = { Text("Last Name") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(
            p.phone.orEmpty(), vm::setPhone, label = { Text("Phone") }, placeholder = { Text("Phone number") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Birthday", fontWeight = FontWeight.Bold)
                BirthdayButton(p.birthday, vm::setBirthday)
            }
            Column(Modifier.weight(1f)) {
                Text("Height", fontWeight = FontWeight.Bold)
                HeightButton(p.feet, p.inches, vm::setHeight)
            }
        }
        PillSegments(SEXES, p.sex ?: "Male", vm::setSex)
        GoalWeightRow(p, vm::setGoalWeight)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activity Level", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onActivityHelp) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, "What do these mean?") }
            }
            PillSegments(ACTIVITY_LEVELS, p.activityLevel ?: "Sedentary", vm::setActivityLevel)
        }
        WaterGoalRow(p.waterGoal, vm::setWaterGoal)
        WristRow(p.wristCircumference, vm::setWrist)

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.saving) Text("Saving…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (BuildConfig.ENV != "prod") {
            Text("Dev build · ${BuildConfig.API_BASE_URL}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayButton(birthday: LocalDate?, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FilledTonalButton(onClick = { open = true }, modifier = Modifier.padding(top = 6.dp)) {
        Text(birthday?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: "Set birthday")
    }
    if (open) {
        // The Material date picker works in UTC days, so convert both ways in UTC.
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = (birthday ?: LocalDate.now().minusYears(30)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(picker) }
    }
}

/** Shows 5' 7" and opens a feet/inches picker, like the iOS height sheet. */
@Composable
private fun HeightButton(feet: Int?, inches: Int?, onChange: (Int, Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(if (feet != null) "$feet' ${inches ?: 0}\"" else "Set height", modifier = Modifier.fillMaxWidth())
    }
    if (open) {
        var f by remember { mutableStateOf(feet ?: 5) }
        var i by remember { mutableStateOf(inches ?: 0) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Select Height") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberDropdown("ft", f, (3..7).toList()) { f = it }
                    NumberDropdown("in", i, (0..11).toList()) { i = it }
                }
            },
            confirmButton = { TextButton(onClick = { onChange(f, i); open = false }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Dropdown(label: String, value: T, options: List<T>, format: (T) -> String, width: Int, onPick: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(
            value = format(value), onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.width(width.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(format(o)) }, onClick = { onPick(o); expanded = false }) }
        }
    }
}

@Composable
private fun NumberDropdown(label: String, value: Int, options: List<Int>, onPick: (Int) -> Unit) =
    Dropdown(label, value, options, { it.toString() }, 96, onPick)

@Composable
private fun GoalWeightRow(p: Profile, onChange: (Double) -> Unit) {
    val goal = p.goalWeight ?: 150.0
    val locked = p.useTargetWeightForGoal
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Goal Weight", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${goal.toInt()}", fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = goal.toFloat().coerceIn(80f, 500f),
                onValueChange = { onChange(Math.rint(it.toDouble())) },
                valueRange = 80f..500f,
                enabled = !locked,
                colors = SliderDefaults.colors(thumbColor = RedMain, activeTrackColor = RedMain),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onChange(goal - 1) }, enabled = !locked) { Icon(Icons.Filled.Remove, "Lower goal") }
            IconButton(onClick = { onChange(goal + 1) }, enabled = !locked) { Icon(Icons.Filled.Add, "Raise goal") }
        }
        if (locked) Text("Your coach sets this from your target weight.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WaterGoalRow(goal: Double?, onChange: (Double?) -> Unit) {
    var text by remember(goal) { mutableStateOf(goal?.toInt()?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            val digits = v.filter(Char::isDigit).take(4)
            text = digits
            onChange(digits.toDoubleOrNull())
        },
        label = { Text("Water Goal (oz / day)") },
        placeholder = { Text("e.g. 80") },
        suffix = { Text("oz") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WristRow(value: Double?, onPick: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Wrist Circumference (in)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Dropdown("in", value ?: 6.5, WRIST_SIZES, ::fraction, 120, onPick)
    }
}

/** 6.75 -> "6-3/4", like the iOS doubleToFraction. */
fun fraction(v: Double): String {
    val whole = v.toInt()
    val eighths = Math.round((v - whole) * 8).toInt()
    if (eighths == 0) return "$whole"
    var n = eighths; var d = 8
    while (n % 2 == 0) { n /= 2; d /= 2 }
    return "$whole-$n/$d"
}

@Composable
private fun MembershipSection(app: AppState) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Top Flight Health Membership", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val m = app.membership
                when {
                    m == null -> Text("Checking…")
                    app.isMember -> {
                        Text("Active · ${m.subscription.replaceFirstChar { it.uppercase() }}")
                        m.scheduledMembershipEnd?.let { Text("Access ends ${formatDate(it)}") }
                            ?: m.expirationDate?.let { Text("Renews ${formatDate(it)}") }
                        Text(
                            "To change or cancel your membership, visit topflighthealth.co.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // No in-app purchases on Android. Check Google Play's current
                    // rules before adding a link or button to an outside checkout.
                    else -> Text("You don't have an active membership. Visit topflighthealth.co to become a member, then come back to the app.")
                }
            }
        }
        Row {
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://topflighthealth.co/privacy"))) }) { Text("Privacy Policy") }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://topflighthealth.co/terms"))) }) { Text("Terms of Use") }
        }
    }
}

fun formatWeight(w: Double): String {
    val r = Math.floor(w * 10 + 0.5) / 10
    return if (r % 1.0 == 0.0) r.toInt().toString() else String.format("%.1f", r)
}

fun formatDate(iso: String): String = runCatching {
    Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}.getOrDefault(iso.take(10))

fun formatDateTime(iso: String): String = runCatching {
    Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
}.getOrDefault(iso)
