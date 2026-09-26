package fr.alaedine.aesh.di

import fr.alaedine.aesh.data.di.dataModule
import fr.alaedine.aesh.data.di.reminderModule
import fr.alaedine.aesh.presentation.di.presentationModule

/**
 * Aggregates every Koin module in the app.
 *
 * As more of the domain layer lands (see the project README's milestones),
 * its modules will be added here too, keeping dependencies flowing inward:
 * ViewModels receive domain use cases/repositories through constructor
 * injection rather than reaching into data sources directly.
 */
val appModules = listOf(presentationModule, dataModule, reminderModule)
