package fr.alaedine.aesh.presentation.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [SettingsViewModel] reads/writes [fr.alaedine.aesh.domain.repository.BackupRepository]
 * through `viewModelScope`, which requires the `Main` dispatcher to be
 * available; [UnconfinedTestDispatcher] makes coroutines launched on it run
 * eagerly so state updates are visible immediately, without needing manual
 * virtual-time advancement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should expose default settings content when view model is initialized`() {
        // Given / When
        val viewModel = SettingsViewModel(FakeBackupRepository())

        // Then
        assertEquals(SettingsUiState(), viewModel.uiState.value)
    }

    @Test
    fun `should export the backup and show a success message when export succeeds`() =
        runTest {
            // Given
            val repository = FakeBackupRepository()
            val viewModel = SettingsViewModel(repository)
            val destination = ByteArrayOutputStream()

            // When
            viewModel.onExportRequested(destination)

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isProcessing)
            assertEquals(SettingsStatusMessage.ExportSuccess, state.statusMessage)
            assertSame(destination, repository.exportedTo)
        }

    @Test
    fun `should show a failure message when export fails`() =
        runTest {
            // Given
            val repository = FakeBackupRepository(exportError = IOException("disk full"))
            val viewModel = SettingsViewModel(repository)

            // When
            viewModel.onExportRequested(ByteArrayOutputStream())

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isProcessing)
            assertEquals(SettingsStatusMessage.ExportFailed("disk full"), state.statusMessage)
        }

    @Test
    fun `should show a failure message when the export destination file could not be opened`() {
        // Given
        val viewModel = SettingsViewModel(FakeBackupRepository())

        // When
        viewModel.onExportFailedToOpenFile()

        // Then
        assertEquals(SettingsStatusMessage.ExportFileOpenFailed, viewModel.uiState.value.statusMessage)
    }

    @Test
    fun `should show the restore confirmation when restore is clicked`() {
        // Given
        val viewModel = SettingsViewModel(FakeBackupRepository())

        // When
        viewModel.onRestoreClicked()

        // Then
        assertTrue(viewModel.uiState.value.isRestoreConfirmationVisible)
    }

    @Test
    fun `should hide the restore confirmation when restore is cancelled`() {
        // Given
        val viewModel = SettingsViewModel(FakeBackupRepository())
        viewModel.onRestoreClicked()

        // When
        viewModel.onRestoreCancelled()

        // Then
        assertFalse(viewModel.uiState.value.isRestoreConfirmationVisible)
    }

    @Test
    fun `should hide the restore confirmation when restore is confirmed`() {
        // Given
        val viewModel = SettingsViewModel(FakeBackupRepository())
        viewModel.onRestoreClicked()

        // When
        viewModel.onRestoreConfirmed()

        // Then
        assertFalse(viewModel.uiState.value.isRestoreConfirmationVisible)
    }

    @Test
    fun `should restore the backup and show a success message when the selected file is imported`() =
        runTest {
            // Given
            val repository = FakeBackupRepository()
            val viewModel = SettingsViewModel(repository)
            val source = ByteArrayInputStream(ByteArray(0))

            // When
            viewModel.onRestoreFileSelected(source)

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isProcessing)
            assertEquals(SettingsStatusMessage.RestoreSuccess, state.statusMessage)
            assertSame(source, repository.importedFrom)
        }

    @Test
    fun `should show a failure message when restore fails`() =
        runTest {
            // Given
            val repository = FakeBackupRepository(importError = IOException("malformed JSON"))
            val viewModel = SettingsViewModel(repository)

            // When
            viewModel.onRestoreFileSelected(ByteArrayInputStream(ByteArray(0)))

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isProcessing)
            assertEquals(SettingsStatusMessage.RestoreFailed("malformed JSON"), state.statusMessage)
        }

    @Test
    fun `should show a failure message when the restore source file could not be opened`() {
        // Given
        val viewModel = SettingsViewModel(FakeBackupRepository())

        // When
        viewModel.onRestoreFailedToOpenFile()

        // Then
        assertEquals(SettingsStatusMessage.RestoreFileOpenFailed, viewModel.uiState.value.statusMessage)
    }

    @Test
    fun `should clear the status message once it has been shown`() =
        runTest {
            // Given
            val viewModel = SettingsViewModel(FakeBackupRepository())
            viewModel.onExportRequested(ByteArrayOutputStream())

            // When
            viewModel.onStatusMessageShown()

            // Then
            assertNull(viewModel.uiState.value.statusMessage)
        }
}
