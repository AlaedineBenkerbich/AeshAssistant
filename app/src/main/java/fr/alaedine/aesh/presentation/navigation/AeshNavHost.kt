package fr.alaedine.aesh.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fr.alaedine.aesh.presentation.home.HomeRoute
import fr.alaedine.aesh.presentation.report.DailyReportFormRoute
import fr.alaedine.aesh.presentation.schedule.ScheduleFormRoute
import fr.alaedine.aesh.presentation.schedule.ScheduleListRoute
import fr.alaedine.aesh.presentation.settings.SettingsRoute
import fr.alaedine.aesh.presentation.student.StudentFormRoute
import fr.alaedine.aesh.presentation.student.StudentListRoute

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
        composable<AeshDestination.ScheduleList> {
            ScheduleListRoute(
                onAddScheduleSlot = { navController.navigate(AeshDestination.AddScheduleSlot) },
                onEditScheduleSlot = { scheduleSlotId ->
                    navController.navigate(AeshDestination.EditScheduleSlot(scheduleSlotId))
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<AeshDestination.AddScheduleSlot> {
            ScheduleFormRoute(
                scheduleSlotId = null,
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
    }
}
