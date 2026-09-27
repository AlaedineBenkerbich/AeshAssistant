package fr.alaedine.aesh.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.R
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import org.koin.androidx.compose.koinViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Stateful entry point wired to [HomeViewModel]. Kept separate from the
 * stateless [HomeScreen] so the latter has no Android/ViewModel dependencies
 * and stays trivially previewable and testable.
 */
@Composable
fun HomeRoute(
    onNavigateToStudents: () -> Unit,
    onNavigateToDailyReport: (Long?) -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToEssReport: () -> Unit,
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
        onNavigateToEssReport = onNavigateToEssReport,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier,
    )
}

/**
 * The app's main entry point: a calendar-style week strip highlighting
 * today (see [CalendarWeekHeader]), a per-student breakdown of whether
 * their daily report has been filled out yet (warning icon when missing,
 * see [StudentReportStatusRow]). Tapping a student row jumps straight into
 * the [fr.alaedine.aesh.presentation.report.DailyReportFormScreen] with
 * that student preselected — logging an observation for a specific student
 * is the far more common action — while the FAB opens the same form
 * without preselecting anyone, for the rarer case of picking the student
 * from within the form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onNavigateToStudents: () -> Unit,
    onNavigateToDailyReport: (Long?) -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToEssReport: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onNavigateToStudents) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = stringResource(R.string.cd_nav_students))
                    }
                    IconButton(onClick = onNavigateToSchedule) {
                        Icon(imageVector = Icons.Default.DateRange, contentDescription = stringResource(R.string.cd_nav_schedule))
                    }
                    IconButton(onClick = onNavigateToEssReport) {
                        Icon(imageVector = Icons.Default.Create, contentDescription = stringResource(R.string.cd_nav_ess_report))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(R.string.cd_nav_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToDailyReport(null) }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.cd_new_daily_report))
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CalendarWeekHeader(
                    today = uiState.date,
                    daysWithScheduledClasses = uiState.daysWithScheduledClasses,
                )
            }
            if (uiState.hasMissingReports) {
                item {
                    MissingReportsWarning(missingCount = uiState.missingReportCount)
                }
            }
            if (uiState.studentStatuses.isEmpty() && !uiState.isLoading) {
                item {
                    Text(
                        text = stringResource(R.string.home_empty_students),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(items = uiState.studentStatuses, key = { it.student.id }) { status ->
                    StudentReportStatusRow(
                        status = status,
                        onClick = { onNavigateToDailyReport(status.student.id) },
                    )
                }
            }
        }
    }
}

/**
 * Calendar-style dashboard header: the current month/year followed by a
 * week strip (locale-aware first day of week) with [today] highlighted and
 * a small dot under any day that has at least one entry in
 * [daysWithScheduledClasses], so the date display feels like a real
 * calendar rather than a plain date string.
 */
@Composable
private fun CalendarWeekHeader(
    today: LocalDate,
    daysWithScheduledClasses: Set<DayOfWeek>,
    modifier: Modifier = Modifier,
) {
    // Read through LocalLocale (rather than Locale.getDefault()) so this
    // recomposes if the user changes the system locale while the app is running.
    val locale = LocalLocale.current.platformLocale
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = monthYearLabel(today, locale),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                currentWeekDates(today, locale).forEach { date ->
                    CalendarDayCell(
                        date = date,
                        isToday = date == today,
                        hasScheduledClasses = date.dayOfWeek in daysWithScheduledClasses,
                        locale = locale,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** A single day cell in [CalendarWeekHeader]: weekday label, a day-of-month number circled when [isToday], and an event dot when [hasScheduledClasses]. */
@Composable
private fun CalendarDayCell(
    date: LocalDate,
    isToday: Boolean,
    hasScheduledClasses: Boolean,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val todayLabel = stringResource(R.string.cd_calendar_today)
    val hasScheduledClassesLabel = stringResource(R.string.cd_calendar_has_scheduled_classes)
    val accessibilityLabel =
        buildString {
            append(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)))
            if (isToday) append(", ").append(todayLabel)
            if (hasScheduledClasses) append(", ").append(hasScheduledClassesLabel)
        }
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = accessibilityLabel },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (hasScheduledClasses) MaterialTheme.colorScheme.tertiary else Color.Transparent),
        )
    }
}

/** The 7 [LocalDate]s of the week containing [date], starting from [locale]'s first day of the week. */
private fun currentWeekDates(
    date: LocalDate,
    locale: Locale,
): List<LocalDate> {
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val offsetFromWeekStart = (date.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val startOfWeek = date.minusDays(offsetFromWeekStart.toLong())
    return List(7) { startOfWeek.plusDays(it.toLong()) }
}

/** E.g. "September 2026". */
private fun monthYearLabel(
    date: LocalDate,
    locale: Locale,
): String =
    date
        .format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        .replaceFirstChar { it.titlecase(locale) }

/** Warning banner shown when [missingCount] students still haven't filled out today's report. */
@Composable
private fun MissingReportsWarning(
    missingCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier =
                Modifier
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
            val label = pluralStringResource(R.plurals.missing_reports_warning, missingCount, missingCount)
            Text(text = label, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

/**
 * A single student's name/class alongside a check (report filled) or
 * warning (report missing) icon. Tapping the row invokes [onClick] to jump
 * straight into today's [fr.alaedine.aesh.presentation.report.DailyReportFormScreen]
 * with this student preselected, since logging an observation for them is
 * the far more common action.
 */
@Composable
private fun StudentReportStatusRow(
    status: StudentReportStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openDailyReportLabel =
        stringResource(
            R.string.cd_open_daily_report_for_student,
            stringResource(R.string.student_display_name, status.student.firstName, status.student.className),
        )
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = openDailyReportLabel },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    stringResource(
                        R.string.student_display_name,
                        status.student.firstName,
                        status.student.className,
                    ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (status.hasReportToday) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.cd_report_completed),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = stringResource(R.string.cd_report_missing),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    AeshAssistantTheme {
        HomeScreen(
            uiState =
                HomeUiState(
                    studentStatuses =
                        listOf(
                            StudentReportStatus(
                                student = Student(id = 1L, firstName = "Alice", className = "CE2"),
                                hasReportToday = true,
                            ),
                            StudentReportStatus(
                                student = Student(id = 2L, firstName = "Amir", className = "CM2"),
                                hasReportToday = false,
                            ),
                        ),
                    daysWithScheduledClasses = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                    isLoading = false,
                ),
            onNavigateToStudents = {},
            onNavigateToDailyReport = {},
            onNavigateToSchedule = {},
            onNavigateToEssReport = {},
            onNavigateToSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenAllReportedPreview() {
    AeshAssistantTheme {
        HomeScreen(
            uiState =
                HomeUiState(
                    studentStatuses =
                        listOf(
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
            onNavigateToEssReport = {},
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
            onNavigateToEssReport = {},
            onNavigateToSettings = {},
        )
    }
}
