package fr.alaedine.aesh.presentation.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Presentation-layer state holder for the settings screen.
 *
 * It depends on nothing outside the presentation layer today. Once the data
 * layer exists (backup/restore, reminder preferences — see later
 * milestones), this class will consume it through constructor-injected use
 * case interfaces rather than reaching into data sources directly, keeping
 * the Clean Architecture dependency rule (dependencies always point inward)
 * intact.
 */
class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
}
