package fr.alaedine.aesh.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fr.alaedine.aesh.domain.model.ParsedScheduleSlot
import fr.alaedine.aesh.presentation.home.HomeRoute
import fr.alaedine.aesh.presentation.report.DailyReportFormRoute
import fr.alaedine.aesh.presentation.report.EssReportRoute
import fr.alaedine.aesh.presentation.report.notes.NotesScannerRoute
import fr.alaedine.aesh.presentation.schedule.ScheduleFormRoute
import fr.alaedine.aesh.presentation.schedule.ScheduleListRoute
import fr.alaedine.aesh.presentation.schedule.scanner.ScheduleScannerRoute
import fr.alaedine.aesh.presentation.settings.SettingsRoute
import fr.alaedine.aesh.presentation.student.StudentFormRoute
import fr.alaedine.aesh.presentation.student.StudentListRoute
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Key under which [AeshDestination.NotesScanner] hands the text it read back
 * to the [AeshDestination.DailyReportForm] entry beneath it.
 */
private const val SCANNED_NOTES_RESULT_KEY = "scanned_notes"

/**
 * Hosts every screen behind a single [androidx.navigation.NavController],
 * wiring the [AeshDestination] graph. This is the sole place that knows
 * about the full set of screens; each route composable only knows how to
 * reach its immediate neighbors via the callbacks passed to it.
 */
@Composable
fun AeshNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    // Shared by every top-level tab screen's bottom navigation bar: tabs are
    // siblings at the root of the back stack rather than screens pushed on
    // top of one another, so switching tabs never shows a back button and
    // popping back to a previously visited tab restores its scroll/state
    // (saveState/restoreState) instead of recreating it from scratch.
    val onTabSelected: (AeshBottomNavTab) -> Unit = { tab ->
        navController.navigate(tab.toDestination()) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = AeshDestination.Dashboard,
        modifier = modifier,
    ) {
        composable<AeshDestination.Dashboard> {
            HomeRoute(
                onTabSelected = onTabSelected,
                onNavigateToDailyReport = { studentId, date ->
                    navController.navigate(AeshDestination.DailyReportForm(studentId = studentId, date = date?.toString()))
                },
            )
        }
        composable<AeshDestination.Settings> {
            SettingsRoute(
                onTabSelected = onTabSelected,
            )
        }
        composable<AeshDestination.StudentList> {
            StudentListRoute(
                onAddStudent = { navController.navigate(AeshDestination.AddStudent) },
                onEditStudent = { studentId -> navController.navigate(AeshDestination.EditStudent(studentId)) },
                onTabSelected = onTabSelected,
            )
        }
        composable<AeshDestination.AddStudent> {
            StudentFormRoute(
                studentId = null,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<AeshDestination.EditStudent> { backStackEntry ->
            val destination = backStackEntry.toRoute<AeshDestination.EditStudent>()
            StudentFormRoute(
                studentId = destination.studentId,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<AeshDestination.DailyReportForm> { backStackEntry ->
            val destination = backStackEntry.toRoute<AeshDestination.DailyReportForm>()
            val scannedNotes by backStackEntry.savedStateHandle
                .getStateFlow<String?>(SCANNED_NOTES_RESULT_KEY, null)
                .collectAsStateWithLifecycle()
            DailyReportFormRoute(
                studentId = destination.studentId,
                date = destination.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                onNavigateBack = { navController.popBackStack() },
                // Single top: a double tap mustn't stack a second scanner, which would hand its result to the first
                // scanner instead of this form, losing the scanned notes.
                onScanNotes = { navController.navigate(AeshDestination.NotesScanner) { launchSingleTop = true } },
                scannedNotes = scannedNotes,
                onScannedNotesConsumed = { backStackEntry.savedStateHandle.remove<String>(SCANNED_NOTES_RESULT_KEY) },
            )
        }
        composable<AeshDestination.EssReportForm> {
            EssReportRoute(
                onTabSelected = onTabSelected,
            )
        }
        composable<AeshDestination.ScheduleList> {
            ScheduleListRoute(
                onAddScheduleSlot = { navController.navigate(AeshDestination.AddScheduleSlot()) },
                onScanScheduleSlot = { navController.navigate(AeshDestination.ScheduleScanner) },
                onEditScheduleSlot = { scheduleSlotId ->
                    navController.navigate(AeshDestination.EditScheduleSlot(scheduleSlotId))
                },
                onTabSelected = onTabSelected,
            )
        }
        composable<AeshDestination.AddScheduleSlot> { backStackEntry ->
            val destination = backStackEntry.toRoute<AeshDestination.AddScheduleSlot>()
            ScheduleFormRoute(
                scheduleSlotId = null,
                prefill = destination.toParsedScheduleSlot(),
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<AeshDestination.EditScheduleSlot> { backStackEntry ->
            val destination = backStackEntry.toRoute<AeshDestination.EditScheduleSlot>()
            ScheduleFormRoute(
                scheduleSlotId = destination.scheduleSlotId,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<AeshDestination.ScheduleScanner> {
            ScheduleScannerRoute(
                onScanned = { parsed ->
                    navController.navigate(parsed.toAddScheduleSlotDestination()) {
                        // Scanning replaces the "add slot" step rather than
                        // stacking on top of it, so back from the pre-filled
                        // form returns straight to the schedule list.
                        popUpTo(AeshDestination.ScheduleScanner) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<AeshDestination.NotesScanner> {
            NotesScannerRoute(
                onNotesScanned = { notes ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(SCANNED_NOTES_RESULT_KEY, notes)
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}

/** Resolves a bottom navigation tab to the root [AeshDestination] it navigates to. */
private fun AeshBottomNavTab.toDestination(): AeshDestination =
    when (this) {
        AeshBottomNavTab.Dashboard -> AeshDestination.Dashboard
        AeshBottomNavTab.Students -> AeshDestination.StudentList
        AeshBottomNavTab.Schedule -> AeshDestination.ScheduleList
        AeshBottomNavTab.EssReport -> AeshDestination.EssReportForm
        AeshBottomNavTab.Settings -> AeshDestination.Settings
    }

/** Converts recognized OCR fields into the primitive-typed nav arguments [AeshDestination.AddScheduleSlot] carries. */
private fun ParsedScheduleSlot.toAddScheduleSlotDestination(): AeshDestination.AddScheduleSlot =
    AeshDestination.AddScheduleSlot(
        dayOfWeek = dayOfWeek?.name,
        startTime = startTime?.toString(),
        endTime = endTime?.toString(),
        subject = subject,
        room = room,
    )

/** The inverse of [toAddScheduleSlotDestination], tolerating malformed/missing fields by leaving them `null`. */
private fun AeshDestination.AddScheduleSlot.toParsedScheduleSlot(): ParsedScheduleSlot =
    ParsedScheduleSlot(
        dayOfWeek = dayOfWeek?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() },
        startTime = startTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        endTime = endTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        subject = subject,
        room = room,
    )
