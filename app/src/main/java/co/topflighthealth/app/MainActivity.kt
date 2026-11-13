package co.topflighthealth.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.topflighthealth.app.ui.TfhApp
import co.topflighthealth.app.ui.theme.TfhTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TfhTheme { TfhApp() }
        }
    }
}
