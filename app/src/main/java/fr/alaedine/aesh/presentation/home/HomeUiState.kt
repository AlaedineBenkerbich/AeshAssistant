package fr.alaedine.aesh.presentation.home

/**
 * Immutable UI state rendered by [HomeScreen].
 *
 * Kept intentionally minimal for the project bootstrap: it only exposes the
 * static welcome content. Future milestones (see the project README) will
 * enrich this state with real domain data produced by use cases from the
 * domain layer, consumed by [HomeViewModel].
 */
data class HomeUiState(
    val appName: String = "AESH Assistant",
    val tagline: String = "Offline-first companion for AESH daily follow-up.",
)
