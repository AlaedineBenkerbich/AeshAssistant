package fr.alaedine.aesh.presentation.di

import fr.alaedine.aesh.presentation.home.HomeViewModel
import fr.alaedine.aesh.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Provides the presentation layer's [androidx.lifecycle.ViewModel]s.
 *
 * Screens resolve their view model with `koinViewModel()` instead of the
 * default factory, so this module is the single place that wires each
 * screen to its dependencies as the app grows.
 */
val presentationModule = module {
    viewModel { HomeViewModel() }
    viewModel { SettingsViewModel() }
}
