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
 * (`null` once shown), resolved to localized text and surfaced as a
 * Snackbar by [SettingsScreen], and cleared via
 * [SettingsViewModel.onStatusMessageShown].
 */
data class SettingsUiState(
    val isProcessing: Boolean = false,
    val isRestoreConfirmationVisible: Boolean = false,
    val statusMessage: SettingsStatusMessage? = null,
)

/**
 * One-shot result of a [SettingsViewModel] export/restore operation.
 *
 * Kept as a semantic type rather than a raw `String` so [SettingsViewModel]
 * stays free of Android resources/`Context` (see [SettingsUiState]);
 * [SettingsScreen] maps each variant to localized text via `stringResource`.
 */
sealed interface SettingsStatusMessage {
    data object ExportSuccess : SettingsStatusMessage
    data class ExportFailed(val reason: String) : SettingsStatusMessage
    data object ExportFileOpenFailed : SettingsStatusMessage
    data object RestoreSuccess : SettingsStatusMessage
    data class RestoreFailed(val reason: String) : SettingsStatusMessage
    data object RestoreFileOpenFailed : SettingsStatusMessage
}
