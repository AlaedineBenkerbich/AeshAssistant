package fr.alaedine.aesh.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.alaedine.aesh.presentation.home.HomeRoute
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme

/**
 * Single-activity host for the Compose UI. All navigation and screen
 * composition happens inside [setContent]; this class intentionally
 * contains no business logic (see [HomeViewModel][fr.alaedine.aesh.presentation.home.HomeViewModel]
 * and the future domain layer for that).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AeshAssistantTheme {
                HomeRoute()
            }
        }
    }
}
