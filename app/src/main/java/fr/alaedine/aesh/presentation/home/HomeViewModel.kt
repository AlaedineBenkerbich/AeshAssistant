package fr.alaedine.aesh.presentation.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Presentation-layer state holder for the welcome/home screen.
 *
 * It depends on nothing outside the presentation layer today. Once the
 * domain layer exists (student/schedule use cases, tracked in later
 * milestones), this class will consume it through constructor-injected use
 * case interfaces rather than reaching into data sources directly, keeping
 * the Clean Architecture dependency rule (dependencies always point inward)
 * intact.
 */
class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}
