package fr.alaedine.aesh.presentation.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {

    @Test
    fun `initial state exposes the default welcome content`() {
        val viewModel = HomeViewModel()

        val state = viewModel.uiState.value

        assertEquals(HomeUiState(), state)
    }
}
