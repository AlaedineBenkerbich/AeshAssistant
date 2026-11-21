package fr.alaedine.aesh.presentation.permission

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** The runtime state of a single permission, see [rememberPermissionState]. */
@Stable
class PermissionState internal constructor(
    val isGranted: Boolean,
    private val launchRequest: () -> Unit,
) {
    /**
     * Asks the user for the permission; [isGranted] (and the callback of [rememberPermissionState]) reflect their answer.
     * Once Android no longer shows the permission dialog (the user refused it twice, or for good), this opens the
     * app's system settings instead, the only place left where it can be granted.
     */
    fun request() = launchRequest()
}

/**
 * Tracks whether [permission] is granted and lets the caller ask for it, so
 * screens that need a runtime permission (camera, microphone...) don't each
 * re-implement the launcher plumbing. Re-checked whenever the screen comes
 * back to the foreground, e.g. after the permission was granted in settings.
 *
 * @param onResult Invoked with the user's answer after a [PermissionState.request].
 */
@Composable
fun rememberPermissionState(
    permission: String,
    onResult: (isGranted: Boolean) -> Unit = {},
): PermissionState {
    val context = LocalContext.current
    var isGranted by remember { mutableStateOf(context.isPermissionGranted(permission)) }
    // Android answers a request it no longer shows a dialog for with an immediate refusal, and says so through
    // shouldShowRequestPermissionRationale turning false right after a refusal.
    var isDialogBlocked by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { granted ->
            isGranted = granted
            val activity = context.findActivity()
            isDialogBlocked = !granted && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            onResult(granted)
        }
    LifecycleResumeEffect(permission) {
        isGranted = context.isPermissionGranted(permission)
        onPauseOrDispose {}
    }
    return remember(isGranted, isDialogBlocked, launcher) {
        PermissionState(
            isGranted = isGranted,
            launchRequest = { if (isDialogBlocked) context.openAppSettings() else launcher.launch(permission) },
        )
    }
}

private fun Context.isPermissionGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
