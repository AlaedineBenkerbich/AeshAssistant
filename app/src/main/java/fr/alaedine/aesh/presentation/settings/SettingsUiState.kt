package fr.alaedine.aesh.presentation.settings

/**
 * Immutable UI state rendered by [SettingsScreen].
 *
 * Kept intentionally minimal for the project bootstrap: it only exposes
 * static placeholder content. Real preferences (reminder time, backup and
 * restore — see the project README's later milestones) will enrich this
 * state once the corresponding domain/data layers exist.
 */
data class SettingsUiState(
    val title: String = "Settings",
    val message: String = "Preferences will live here in a later milestone.",
)
