package fr.alaedine.aesh.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.repository.BackupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

/**
 * Presentation-layer state holder for the settings screen: local JSON
 * backup export/restore on top of [backupRepository].
 *
 * Deliberately works with plain [OutputStream]/[InputStream] rather than
 * `android.net.Uri`: [SettingsRoute] resolves the Storage Access
 * Framework-picked file into a stream via `ContentResolver` and hands it to
 * this class, so it stays fully unit-testable (no Robolectric/Android
 * framework needed) despite depending on Android's `ViewModel`.
 */
class SettingsViewModel(
    private val backupRepository: BackupRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Serializes the full local database as JSON into [destination] (a SAF-picked file). */
    fun onExportRequested(destination: OutputStream) {
        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            runCatching { backupRepository.exportBackup(destination) }
                .onSuccess {
                    _uiState.update {
                        it.copy(isProcessing = false, statusMessage = SettingsStatusMessage.ExportSuccess)
                    }
                }.onFailure { error -> handleFailure(error) { reason -> SettingsStatusMessage.ExportFailed(reason) } }
        }
    }

    /** Reports that the SAF-picked export destination couldn't be opened for writing. */
    fun onExportFailedToOpenFile() {
        _uiState.update { it.copy(statusMessage = SettingsStatusMessage.ExportFileOpenFailed) }
    }

    /** Shows the "this replaces all local data" warning before restoring. */
    fun onRestoreClicked() {
        _uiState.update { it.copy(isRestoreConfirmationVisible = true) }
    }

    fun onRestoreCancelled() {
        _uiState.update { it.copy(isRestoreConfirmationVisible = false) }
    }

    /** Dismisses the warning dialog; [SettingsRoute] follows up by launching the SAF file picker. */
    fun onRestoreConfirmed() {
        _uiState.update { it.copy(isRestoreConfirmationVisible = false) }
    }

    /** Reports that the SAF-picked restore source couldn't be opened for reading. */
    fun onRestoreFailedToOpenFile() {
        _uiState.update { it.copy(statusMessage = SettingsStatusMessage.RestoreFileOpenFailed) }
    }

    /** Replaces the full local database with the JSON content read from [source] (a SAF-picked file). */
    fun onRestoreFileSelected(source: InputStream) {
        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            runCatching { backupRepository.importBackup(source) }
                .onSuccess {
                    _uiState.update {
                        it.copy(isProcessing = false, statusMessage = SettingsStatusMessage.RestoreSuccess)
                    }
                }.onFailure { error -> handleFailure(error) { reason -> SettingsStatusMessage.RestoreFailed(reason) } }
        }
    }

    /** Clears the one-shot [SettingsUiState.statusMessage] once [SettingsScreen] has shown it. */
    fun onStatusMessageShown() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun handleFailure(
        error: Throwable,
        toMessage: (String) -> SettingsStatusMessage,
    ) {
        // Coroutine cancellation must always propagate, never be swallowed as a "failure".
        if (error is CancellationException) throw error
        _uiState.update { it.copy(isProcessing = false, statusMessage = toMessage(error.message.orEmpty())) }
    }
}
