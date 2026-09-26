package fr.alaedine.aesh.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import fr.alaedine.aesh.presentation.navigation.AeshNavHost
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme

/**
 * Single-activity host for the Compose UI. All navigation and screen
 * composition happens inside [setContent] via [AeshNavHost]; this class
 * intentionally contains no business logic (see each screen's `ViewModel`
 * and the future domain layer for that), aside from the runtime
 * notification permission request the daily report reminder (see
 * `fr.alaedine.aesh.data.reminder`) needs on API 33+.
 */
class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* No-op either way: the reminder worker re-checks the permission before posting. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            AeshAssistantTheme {
                AeshNavHost()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

