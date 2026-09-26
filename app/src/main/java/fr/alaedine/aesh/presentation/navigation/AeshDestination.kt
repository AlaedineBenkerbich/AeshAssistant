package fr.alaedine.aesh.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for [AeshNavHost].
 *
 * Each destination is a serializable route consumed by Jetpack Navigation
 * Compose's typed `composable<T>` / `navigate<T>` APIs, so arguments (once
 * screens need them, e.g. a student id) are checked at compile time instead
 * of being encoded as raw strings.
 */
sealed interface AeshDestination {

    /** App landing screen (see `HomeRoute`). */
    @Serializable
    data object Dashboard : AeshDestination

    /** Placeholder preferences screen; will grow with real settings later. */
    @Serializable
    data object Settings : AeshDestination
}
