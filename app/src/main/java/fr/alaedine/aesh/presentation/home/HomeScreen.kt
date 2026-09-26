package fr.alaedine.aesh.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.koin.androidx.compose.koinViewModel

/**
 * Stateful entry point wired to [HomeViewModel]. Kept separate from the
 * stateless [HomeScreen] so the latter has no Android/ViewModel dependencies
 * and stays trivially previewable and testable.
 */
@Composable
fun HomeRoute(
    onNavigateToStudents: () -> Unit,
    onNavigateToDailyReport: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onNavigateToStudents = onNavigateToStudents,
        onNavigateToDailyReport = onNavigateToDailyReport,
        onNavigateToSchedule = onNavigateToSchedule,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier,
    )
}

/**
 * The app's main entry point: today's date, a per-student breakdown of
 * whether their daily report has been filled out yet (warning icon when
 * missing, see [StudentReportStatusRow]), and a FAB to jump straight into
 * the [fr.alaedine.aesh.presentation.report.DailyReportFormScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onNavigateToStudents: () -> Unit,
    onNavigateToDailyReport: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Dashboard") },
                actions = {
                    IconButton(onClick = onNavigateToStudents) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = "Students")
                    }
                    IconButton(onClick = onNavigateToSchedule) {
                        Icon(imageVector = Icons.Default.DateRange, contentDescription = "Schedule")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToDailyReport) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New daily report")
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(text = formattedDate(uiState.date), style = MaterialTheme.typography.headlineMedium)
            }
            if (uiState.hasMissingReports) {
                item {
                    MissingReportsWarning(missingCount = uiState.missingReportCount)
                }
            }
            if (uiState.studentStatuses.isEmpty() && !uiState.isLoading) {
                item {
                    Text(
                        text = "No students yet. Add one to start tracking daily reports.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(items = uiState.studentStatuses, key = { it.student.id }) { status ->
                    StudentReportStatusRow(status = status)
                }
            }
        }
    }
}

/** Warning banner shown when [missingCount] students still haven't filled out today's report. */
@Composable
private fun MissingReportsWarning(missingCount: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            val label = if (missingCount == 1) {
                "1 student is still missing today's report."
            } else {
                "$missingCount students are still missing today's report."
            }
            Text(text = label, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

/** A single student's name/class alongside a check (report filled) or warning (report missing) icon. */
@Composable
private fun StudentReportStatusRow(status: StudentReportStatus, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${status.student.firstName} — ${status.student.className}",
                style = MaterialTheme.typography.titleMedium,
            )
            if (status.hasReportToday) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Report completed",
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Report missing",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun formattedDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    AeshAssistantTheme {
        HomeScreen(
            uiState = HomeUiState(
                studentStatuses = listOf(
                    StudentReportStatus(
                        student = Student(id = 1L, firstName = "Alice", className = "CE2"),
                        hasReportToday = true,
                    ),
                    StudentReportStatus(
                        student = Student(id = 2L, firstName = "Amir", className = "CM2"),
                        hasReportToday = false,
                    ),
                ),
                isLoading = false,
            ),
            onNavigateToStudents = {},
            onNavigateToDailyReport = {},
            onNavigateToSchedule = {},
            onNavigateToSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenAllReportedPreview() {
    AeshAssistantTheme {
        HomeScreen(
            uiState = HomeUiState(
                studentStatuses = listOf(
                    StudentReportStatus(
                        student = Student(id = 1L, firstName = "Alice", className = "CE2"),
                        hasReportToday = true,
                    ),
                ),
                isLoading = false,
            ),
            onNavigateToStudents = {},
            onNavigateToDailyReport = {},
            onNavigateToSchedule = {},
            onNavigateToSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenEmptyPreview() {
    AeshAssistantTheme {
        HomeScreen(
            uiState = HomeUiState(studentStatuses = emptyList(), isLoading = false),
            onNavigateToStudents = {},
            onNavigateToDailyReport = {},
            onNavigateToSchedule = {},
            onNavigateToSettings = {},
        )
    }
}
