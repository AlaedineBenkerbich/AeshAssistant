package fr.alaedine.aesh.presentation.settings

/**
 * Immutable UI state rendered by [SettingsScreen].
 *
 * @property isProcessing Whether an export or restore is currently
 * running; disables both actions and shows a progress indicator so the
 * user can't start overlapping operations.
 * @property isRestoreConfirmationVisible Whether the "this replaces all
 * local data" warning dialog is shown, gating the destructive restore flow
 * behind an explicit confirmation before the file picker even opens.
 * @property statusMessage One-shot result of the last export/restore
 * (`null` once shown), surfaced as a Snackbar by [SettingsScreen] and
 * cleared via [SettingsViewModel.onStatusMessageShown].
 */
data class SettingsUiState(
    val title: String = "Settings",
    val message: String = "Export a local backup of your data, or restore it from a previous backup file.",
    val isProcessing: Boolean = false,
    val isRestoreConfirmationVisible: Boolean = false,
    val statusMessage: String? = null,
)
