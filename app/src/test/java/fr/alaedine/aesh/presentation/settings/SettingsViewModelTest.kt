package fr.alaedine.aesh.presentation.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsViewModelTest {

    @Test
    fun `should expose default settings content when view model is initialized`() {
        // Given
        val viewModel = SettingsViewModel()

        // When
        val state = viewModel.uiState.value

        // Then
        assertEquals(SettingsUiState(), state)
    }
}
