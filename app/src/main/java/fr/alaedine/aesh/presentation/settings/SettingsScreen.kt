package fr.alaedine.aesh.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.R
import fr.alaedine.aesh.presentation.navigation.AeshBottomNavTab
import fr.alaedine.aesh.presentation.navigation.AeshBottomNavigationBar
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate

private const val BACKUP_MIME_TYPE = "application/json"

/**
 * Stateful entry point wired to [SettingsViewModel]. Kept separate from the
 * stateless [SettingsScreen] so the latter has no Android/ViewModel
 * dependencies and stays trivially previewable and testable.
 *
 * Owns the two Storage Access Framework launchers (export/restore file
 * pickers) and resolves the user-picked `Uri` into an
 * `OutputStream`/`InputStream` via `ContentResolver`, so [SettingsViewModel]
 * itself never needs to know `android.net.Uri`/`ContentResolver` exist.
 */
@Composable
fun SettingsRoute(
    onTabSelected: (AeshBottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val destination = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
            if (destination == null) {
                viewModel.onExportFailedToOpenFile()
            } else {
                viewModel.onExportRequested(destination)
            }
        }

    val restoreLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val source = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            if (source == null) {
                viewModel.onRestoreFailedToOpenFile()
            } else {
                viewModel.onRestoreFileSelected(source)
            }
        }

    SettingsScreen(
        uiState = uiState,
        onTabSelected = onTabSelected,
        onExportClicked = { exportLauncher.launch(defaultBackupFileName()) },
        onRestoreClicked = viewModel::onRestoreClicked,
        onRestoreCancelled = viewModel::onRestoreCancelled,
        onRestoreConfirmed = {
            viewModel.onRestoreConfirmed()
            restoreLauncher.launch(arrayOf(BACKUP_MIME_TYPE))
        },
        onStatusMessageShown = viewModel::onStatusMessageShown,
        modifier = modifier,
    )
}

/** Suggested SAF file name for a new export, e.g. `aesh-backup-2026-09-26.json`. */
private fun defaultBackupFileName(): String = "aesh-backup-${LocalDate.now()}.json"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onTabSelected: (AeshBottomNavTab) -> Unit,
    onExportClicked: () -> Unit,
    onRestoreClicked: () -> Unit,
    onRestoreCancelled: () -> Unit,
    onRestoreConfirmed: () -> Unit,
    onStatusMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val statusMessageText = uiState.statusMessage?.let { resolvedStatusMessage(it) }

    LaunchedEffect(uiState.statusMessage) {
        val message = statusMessageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onStatusMessageShown()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
            )
        },
        bottomBar = {
            AeshBottomNavigationBar(
                currentTab = AeshBottomNavTab.Settings,
                onTabSelected = onTabSelected,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(R.string.settings_section_data_backup), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.settings_message), style = MaterialTheme.typography.bodyMedium)

            Button(
                onClick = onExportClicked,
                enabled = !uiState.isProcessing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.settings_export_backup))
            }

            OutlinedButton(
                onClick = onRestoreClicked,
                enabled = !uiState.isProcessing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.settings_restore_backup))
            }

            if (uiState.isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (uiState.isRestoreConfirmationVisible) {
        RestoreConfirmationDialog(onConfirm = onRestoreConfirmed, onDismiss = onRestoreCancelled)
    }
}

/** Resolves a one-shot [SettingsStatusMessage] to its localized Snackbar text. */
@Composable
private fun resolvedStatusMessage(message: SettingsStatusMessage): String =
    when (message) {
        is SettingsStatusMessage.ExportSuccess -> stringResource(R.string.settings_export_success)
        is SettingsStatusMessage.ExportFailed -> stringResource(R.string.settings_export_failed, message.reason)
        is SettingsStatusMessage.ExportFileOpenFailed -> stringResource(R.string.error_export_file_open_failed)
        is SettingsStatusMessage.RestoreSuccess -> stringResource(R.string.settings_restore_success)
        is SettingsStatusMessage.RestoreFailed -> stringResource(R.string.settings_restore_failed, message.reason)
        is SettingsStatusMessage.RestoreFileOpenFailed -> stringResource(R.string.settings_restore_file_open_failed)
    }

@Composable
private fun RestoreConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_restore_confirm_title)) },
        text = {
            Text(text = stringResource(R.string.settings_restore_confirm_text))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.settings_choose_file))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    AeshAssistantTheme {
        SettingsScreen(
            uiState = SettingsUiState(),
            onTabSelected = {},
            onExportClicked = {},
            onRestoreClicked = {},
            onRestoreCancelled = {},
            onRestoreConfirmed = {},
            onStatusMessageShown = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenProcessingPreview() {
    AeshAssistantTheme {
        SettingsScreen(
            uiState = SettingsUiState(isProcessing = true),
            onTabSelected = {},
            onExportClicked = {},
            onRestoreClicked = {},
            onRestoreCancelled = {},
            onRestoreConfirmed = {},
            onStatusMessageShown = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenRestoreConfirmationPreview() {
    AeshAssistantTheme {
        SettingsScreen(
            uiState = SettingsUiState(isRestoreConfirmationVisible = true),
            onTabSelected = {},
            onExportClicked = {},
            onRestoreClicked = {},
            onRestoreCancelled = {},
            onRestoreConfirmed = {},
            onStatusMessageShown = {},
        )
    }
}
