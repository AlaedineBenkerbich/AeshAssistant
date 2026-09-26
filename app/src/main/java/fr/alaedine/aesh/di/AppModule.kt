package fr.alaedine.aesh.di

import fr.alaedine.aesh.presentation.di.presentationModule

/**
 * Aggregates every Koin module in the app.
 *
 * As the domain and data layers land (see the project README's milestones),
 * their own modules will be added here and combined with [presentationModule],
 * keeping dependencies flowing inward: ViewModels receive domain use cases
 * through constructor injection rather than reaching into data sources
 * directly.
 */
val appModules = listOf(presentationModule)
