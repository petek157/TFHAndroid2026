package co.topflighthealth.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.topflighthealth.app.R
import co.topflighthealth.app.ui.theme.RedMain

/** Mirrors AuthenticationView on iOS: red gradient, logo, one sign-in button. */
@Composable
fun SignInScreen(busy: Boolean, error: String?, onSignIn: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RedMain, RedMain.copy(alpha = 0.8f))))
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.launch_image),
                contentDescription = null,
                modifier = Modifier.size(140.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(24.dp))
            Text("Top Flight Health", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("Your Health Journey Starts Here", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.height(48.dp))

            Button(
                onClick = onSignIn,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = RedMain),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = RedMain, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign In / Register", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Secure authentication", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            }

            if (error != null) {
                Spacer(Modifier.height(24.dp))
                Surface(color = Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.Yellow)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Authentication Error", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(error, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
