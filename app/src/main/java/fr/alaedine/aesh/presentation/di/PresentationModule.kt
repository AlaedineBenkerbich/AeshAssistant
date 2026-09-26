package fr.alaedine.aesh.presentation.di

import fr.alaedine.aesh.presentation.home.HomeViewModel
import fr.alaedine.aesh.presentation.report.DailyReportFormViewModel
import fr.alaedine.aesh.presentation.schedule.ScheduleFormViewModel
import fr.alaedine.aesh.presentation.schedule.ScheduleListViewModel
import fr.alaedine.aesh.presentation.settings.SettingsViewModel
import fr.alaedine.aesh.presentation.student.StudentFormViewModel
import fr.alaedine.aesh.presentation.student.StudentListViewModel
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
    viewModel { HomeViewModel(get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { StudentListViewModel(get()) }
    // The nullable student id (null when adding, an existing id when
    // editing) is supplied at call time via `koinViewModel(parameters = ...)`
    // from the navigation argument, see `StudentFormRoute`.
    viewModel { params -> StudentFormViewModel(get(), params.getOrNull()) }
    viewModel { DailyReportFormViewModel(get(), get()) }
    viewModel { ScheduleListViewModel(get()) }
    // Same nullable-id-as-injection-parameter pattern as `StudentFormViewModel`
    // above, sourced from the navigation argument, see `ScheduleFormRoute`.
    viewModel { params -> ScheduleFormViewModel(get(), params.getOrNull()) }
}
