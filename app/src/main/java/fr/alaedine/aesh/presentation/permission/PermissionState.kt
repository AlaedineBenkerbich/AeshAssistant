package fr.alaedine.aesh.presentation.permission

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** The runtime state of a single permission, see [rememberPermissionState]. */
@Stable
class PermissionState internal constructor(
    val isGranted: Boolean,
    private val launchRequest: () -> Unit,
) {
    /** Asks the user for the permission; [isGranted] (and the callback of [rememberPermissionState]) reflect their answer. */
    fun request() = launchRequest()
}

/**
 * Tracks whether [permission] is granted and lets the caller ask for it, so
 * screens that need a runtime permission (camera, microphone...) don't each
 * re-implement the launcher plumbing.
 *
 * @param onResult Invoked with the user's answer after a [PermissionState.request].
 */
@Composable
fun rememberPermissionState(
    permission: String,
    onResult: (isGranted: Boolean) -> Unit = {},
): PermissionState {
    val context = LocalContext.current
    var isGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { granted ->
            isGranted = granted
            onResult(granted)
        }
    return remember(isGranted, launcher) { PermissionState(isGranted = isGranted, launchRequest = { launcher.launch(permission) }) }
}
