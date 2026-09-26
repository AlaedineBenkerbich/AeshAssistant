package fr.alaedine.aesh.presentation.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeViewModelTest {

    @Test
    fun `should expose default welcome content when view model is initialized`() {
        // Given
        val viewModel = HomeViewModel()

        // When
        val state = viewModel.uiState.value

        // Then
        assertEquals(HomeUiState(), state)
    }
}
