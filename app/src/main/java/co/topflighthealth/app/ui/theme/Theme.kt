package co.topflighthealth.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand colors from the iOS asset catalog (Assets.xcassets/Theme).
val BlueMain = Color(0xFF1B1464)       // blueMain
val BlueSecondary = Color(0xFF5851A3)  // blueSecondary
val RedMain = Color(0xFFC1272D)        // redMain, also LaunchColor
val RedSecondary = Color(0xFFFFCCBC)   // redSecondary

private val LightColors = lightColorScheme(
    primary = BlueMain,
    onPrimary = Color.White,
    secondary = RedMain,
    onSecondary = Color.White,
    tertiary = BlueSecondary,
    secondaryContainer = RedSecondary,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9B4FF),
    onPrimary = BlueMain,
    secondary = Color(0xFFFF8A80),
    onSecondary = Color(0xFF5C0A0D),
    tertiary = BlueSecondary,
)

@Composable
fun TfhTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
