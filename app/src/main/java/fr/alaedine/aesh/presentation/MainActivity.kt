package fr.alaedine.aesh.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.alaedine.aesh.presentation.navigation.AeshNavHost
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme

/**
 * Single-activity host for the Compose UI. All navigation and screen
 * composition happens inside [setContent] via [AeshNavHost]; this class
 * intentionally contains no business logic (see each screen's `ViewModel`
 * and the future domain layer for that).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AeshAssistantTheme {
                AeshNavHost()
            }
        }
    }
}
