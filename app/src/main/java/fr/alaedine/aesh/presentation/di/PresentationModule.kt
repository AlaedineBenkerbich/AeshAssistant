package fr.alaedine.aesh.presentation.di

import fr.alaedine.aesh.domain.model.ParsedScheduleSlot
import fr.alaedine.aesh.presentation.home.HomeViewModel
import fr.alaedine.aesh.presentation.report.DailyReportFormViewModel
import fr.alaedine.aesh.presentation.report.EssReportViewModel
import fr.alaedine.aesh.presentation.schedule.ScheduleFormViewModel
import fr.alaedine.aesh.presentation.schedule.ScheduleListViewModel
import fr.alaedine.aesh.presentation.schedule.scanner.ScheduleScannerViewModel
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
val presentationModule =
    module {
        viewModel { HomeViewModel(get(), get(), get()) }
        viewModel { SettingsViewModel(get()) }
        viewModel { StudentListViewModel(get()) }
        // The nullable student id (null when adding, an existing id when
        // editing) is supplied at call time via `koinViewModel(parameters = ...)`
        // from the navigation argument, see `StudentFormRoute`.
        viewModel { params -> StudentFormViewModel(get(), params.getOrNull()) }
        // Same nullable-id-as-injection-parameter pattern as
        // `StudentFormViewModel` above: `null` when reached from the "new
        // report" FAB, or a student id to preselect when reached by tapping
        // a student row on the dashboard, see `DailyReportFormRoute`.
        viewModel { params -> DailyReportFormViewModel(get(), get(), params.getOrNull()) }
        viewModel { EssReportViewModel(get(), get(), get()) }
        viewModel { ScheduleListViewModel(get(), get()) }
        // Same nullable-id-as-injection-parameter pattern as `StudentFormViewModel`
        // above, plus an optional scanner pre-fill (both sourced from the
        // navigation argument, see `ScheduleFormRoute`). Koin's `ParametersHolder`
        // resolves each `getOrNull<T>()` call by matching type, so the two
        // parameters can be passed/retrieved in any order.
        viewModel { params ->
            ScheduleFormViewModel(get(), get(), params.getOrNull<Long>(), params.getOrNull<ParsedScheduleSlot>())
        }
        viewModel { ScheduleScannerViewModel(get()) }
    }
