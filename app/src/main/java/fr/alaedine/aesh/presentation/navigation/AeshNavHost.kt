package fr.alaedine.aesh.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fr.alaedine.aesh.domain.model.ParsedScheduleSlot
import fr.alaedine.aesh.presentation.home.HomeRoute
import fr.alaedine.aesh.presentation.report.DailyReportFormRoute
import fr.alaedine.aesh.presentation.report.EssReportRoute
import fr.alaedine.aesh.presentation.schedule.ScheduleFormRoute
import fr.alaedine.aesh.presentation.schedule.ScheduleListRoute
import fr.alaedine.aesh.presentation.schedule.scanner.ScheduleScannerRoute
import fr.alaedine.aesh.presentation.settings.SettingsRoute
import fr.alaedine.aesh.presentation.student.StudentFormRoute
import fr.alaedine.aesh.presentation.student.StudentListRoute
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Hosts every screen behind a single [androidx.navigation.NavController],
 * wiring the [AeshDestination] graph. This is the sole place that knows
 * about the full set of screens; each route composable only knows how to
 * reach its immediate neighbors via the callbacks passed to it.
 */
@Composable
fun AeshNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AeshDestination.Dashboard,
        modifier = modifier,
    ) {
        composable<AeshDestination.Dashboard> {
            HomeRoute(
                onNavigateToStudents = { navController.navigate(AeshDestination.StudentList) },
                onNavigateToDailyReport = { navController.navigate(AeshDestination.DailyReportForm) },
                onNavigateToSchedule = { navController.navigate(AeshDestination.ScheduleList) },
                onNavigateToEssReport = { navController.navigate(AeshDestination.EssReportForm) },
                onNavigateToSettings = { navController.navigate(AeshDestination.Settings) },
            )
        }
        composable<AeshDestination.Settings> {
            SettingsRoute(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<AeshDestination.StudentList> {
            StudentListRoute(
                onAddStudent = { navController.navigate(AeshDestination.AddStudent) },
                onEditStudent = { studentId -> navController.navigate(AeshDestination.EditStudent(studentId)) },
                onNavigateBack = { navController.popBackStack() },
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
        composable<AeshDestination.DailyReportForm> {
            DailyReportFormRoute(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<AeshDestination.EssReportForm> {
            EssReportRoute(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<AeshDestination.ScheduleList> {
            ScheduleListRoute(
                onAddScheduleSlot = { navController.navigate(AeshDestination.AddScheduleSlot()) },
                onScanScheduleSlot = { navController.navigate(AeshDestination.ScheduleScanner) },
                onEditScheduleSlot = { scheduleSlotId ->
                    navController.navigate(AeshDestination.EditScheduleSlot(scheduleSlotId))
                },
                onNavigateBack = { navController.popBackStack() },
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
    }
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

