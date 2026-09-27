package fr.alaedine.aesh.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

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
        onDateSelected = viewModel::onDateSelected,
        onPreviousWeekClicked = viewModel::onPreviousWeekClicked,
        onNextWeekClicked = viewModel::onNextWeekClicked,
        modifier = modifier,
    )
}

/**
 * The app's main entry point: a freely scrollable, calendar-style week strip
 * (see [CalendarWeekHeader]) that can jump to any day — via the previous/
 * next week arrows, tapping a day cell, or picking a date outright from the
 * month/year label's date picker — rather than being stuck on the present.
 * Below it, a per-student breakdown of whether their daily report has been
 * filled out yet for the selected day (warning icon when missing, see
 * [StudentReportStatusRow]), segmented lesson by lesson: one
 * [LessonBlockHeader] per [fr.alaedine.aesh.domain.model.ScheduleSlot] on
 * that day, in schedule order, so only students who actually have a class
 * then show up — solely the schedule determines who needs an observation.
 * Tapping a student row jumps straight into the
 * [fr.alaedine.aesh.presentation.report.DailyReportFormScreen] with that
 * student preselected — logging an observation for a specific student is
 * the far more common action — while the FAB opens the same form without
 * preselecting anyone, letting the AESH manually log an observation for any
 * known student even if they weren't on the selected day's schedule.
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
    onDateSelected: (LocalDate) -> Unit,
    onPreviousWeekClicked: () -> Unit,
    onNextWeekClicked: () -> Unit,
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
                    selectedDate = uiState.date,
                    today = uiState.today,
                    daysWithScheduledClasses = uiState.daysWithScheduledClasses,
                    onDateSelected = onDateSelected,
                    onPreviousWeekClicked = onPreviousWeekClicked,
                    onNextWeekClicked = onNextWeekClicked,
                )
            }
            if (uiState.hasMissingReports) {
                item {
                    MissingReportsWarning(missingCount = uiState.missingReportCount, isToday = uiState.date == uiState.today)
                }
            }
            if (uiState.lessonBlocks.isEmpty() && !uiState.isLoading) {
                item {
                    Text(
                        text =
                            stringResource(
                                if (uiState.date == uiState.today) {
                                    R.string.home_empty_schedule_today
                                } else {
                                    R.string.home_empty_schedule_other_day
                                },
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                uiState.lessonBlocks.forEach { lessonBlock ->
                    item(key = "lesson-header-${lessonBlock.scheduleSlotId}") {
                        LessonBlockHeader(lessonBlock = lessonBlock)
                    }
                    items(
                        items = lessonBlock.studentStatuses,
                        key = { "${lessonBlock.scheduleSlotId}-${it.student.id}" },
                    ) { status ->
                        StudentReportStatusRow(
                            status = status,
                            onClick = { onNavigateToDailyReport(status.student.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Calendar-style dashboard header enabling free navigation instead of being
 * stuck on the present: previous/next-week arrows shift [selectedDate] by
 * exactly one week (see [onPreviousWeekClicked]/[onNextWeekClicked]),
 * tapping the month/year label opens a full [DatePickerDialog] to jump
 * straight to any day, and the week strip below shows Monday through Friday
 * of [selectedDate]'s week — tapping any of those day cells selects it (see
 * [onDateSelected]). [selectedDate] is filled in and [today] outlined when
 * they differ, so the user can always tell which day they're viewing versus
 * which day it actually is; a small dot marks any day with at least one
 * entry in [daysWithScheduledClasses].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarWeekHeader(
    selectedDate: LocalDate,
    today: LocalDate,
    daysWithScheduledClasses: Set<DayOfWeek>,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousWeekClicked: () -> Unit,
    onNextWeekClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read through LocalLocale (rather than Locale.getDefault()) so this
    // recomposes if the user changes the system locale while the app is running.
    val locale = LocalLocale.current.platformLocale
    var isDatePickerVisible by remember { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousWeekClicked) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.cd_previous_week),
                    )
                }
                Text(
                    text = monthYearLabel(selectedDate, locale),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(
                                onClickLabel = stringResource(R.string.cd_pick_date),
                                onClick = { isDatePickerVisible = true },
                            ).padding(horizontal = 8.dp, vertical = 4.dp),
                )
                IconButton(onClick = onNextWeekClicked) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cd_next_week),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                currentWeekDates(selectedDate).forEach { date ->
                    CalendarDayCell(
                        date = date,
                        isSelected = date == selectedDate,
                        isToday = date == today,
                        hasScheduledClasses = date.dayOfWeek in daysWithScheduledClasses,
                        locale = locale,
                        onClick = { onDateSelected(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    if (isDatePickerVisible) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.toUtcEpochMillis())
        DatePickerDialog(
            onDismissRequest = { isDatePickerVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { onDateSelected(it.toUtcLocalDate()) }
                        isDatePickerVisible = false
                    },
                ) {
                    Text(text = stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { isDatePickerVisible = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * A single day cell in [CalendarWeekHeader]: weekday label, a day-of-month
 * number filled when [isSelected] and outlined when [isToday] (both at once
 * when the selected day is today, matching the dashboard's previous,
 * today-only look), and an event dot when [hasScheduledClasses]. Tapping the
 * cell invokes [onClick] to select that day.
 */
@Composable
private fun CalendarDayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasScheduledClasses: Boolean,
    locale: Locale,
    onClick: () -> Unit,
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
        modifier =
            modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibilityLabel
                    selected = isSelected
                }.padding(vertical = 4.dp),
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
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .then(
                        if (isToday && !isSelected) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color =
                    when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
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

/** The 5 weekday [LocalDate]s (Monday through Friday) of the week containing [date], matching the schedule screen's default Monday-first week. */
private fun currentWeekDates(date: LocalDate): List<LocalDate> {
    val monday = date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    return List(5) { monday.plusDays(it.toLong()) }
}

/** E.g. "September 2026". */
private fun monthYearLabel(
    date: LocalDate,
    locale: Locale,
): String =
    date
        .format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        .replaceFirstChar { it.titlecase(locale) }

/** Material3's [rememberDatePickerState] operates in UTC epoch millis regardless of the device's time zone. */
private fun LocalDate.toUtcEpochMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Warning banner shown when [missingCount] students still haven't filled out their report for the selected day; wording mentions "today" only when [isToday]. */
@Composable
private fun MissingReportsWarning(
    missingCount: Int,
    isToday: Boolean,
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
            val label =
                pluralStringResource(
                    if (isToday) R.plurals.missing_reports_warning else R.plurals.missing_reports_warning_other_day,
                    missingCount,
                    missingCount,
                )
            Text(text = label, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

/**
 * Section header segmenting the dashboard's student list lesson by lesson:
 * [lessonBlock]'s subject alongside its time range and room, in a tinted
 * [Surface] so it reads as a distinct heading rather than another list row.
 * Every [StudentReportStatusRow] that follows, until the next header,
 * belongs to this class.
 */
@Composable
private fun LessonBlockHeader(
    lessonBlock: LessonBlock,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = lessonBlock.subject,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = lessonBlock.timeAndRoomLabel(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** E.g. "09:00 – 10:00 • Room 12", omitting the room when blank. */
private fun LessonBlock.timeAndRoomLabel(): String {
    val timeRange = "${startTime.format(TIME_FORMATTER)} – ${endTime.format(TIME_FORMATTER)}"
    return listOfNotNull(timeRange, room.takeIf { it.isNotBlank() }).joinToString(" • ")
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
            if (status.hasReport) {
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
                    lessonBlocks =
                        listOf(
                            LessonBlock(
                                scheduleSlotId = 1L,
                                startTime = LocalTime.of(9, 0),
                                endTime = LocalTime.of(10, 0),
                                subject = "Mathématiques",
                                room = "12",
                                studentStatuses =
                                    listOf(
                                        StudentReportStatus(
                                            student = Student(id = 1L, firstName = "Alice", className = "CE2"),
                                            hasReport = true,
                                        ),
                                        StudentReportStatus(
                                            student = Student(id = 2L, firstName = "Amir", className = "CM2"),
                                            hasReport = false,
                                        ),
                                    ),
                            ),
                            LessonBlock(
                                scheduleSlotId = 2L,
                                startTime = LocalTime.of(14, 0),
                                endTime = LocalTime.of(15, 0),
                                subject = "EPS",
                                room = "",
                                studentStatuses =
                                    listOf(
                                        StudentReportStatus(
                                            student = Student(id = 2L, firstName = "Amir", className = "CM2"),
                                            hasReport = false,
                                        ),
                                    ),
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
            onDateSelected = {},
            onPreviousWeekClicked = {},
            onNextWeekClicked = {},
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
                    lessonBlocks =
                        listOf(
                            LessonBlock(
                                scheduleSlotId = 1L,
                                startTime = LocalTime.of(9, 0),
                                endTime = LocalTime.of(10, 0),
                                subject = "Mathématiques",
                                room = "12",
                                studentStatuses =
                                    listOf(
                                        StudentReportStatus(
                                            student = Student(id = 1L, firstName = "Alice", className = "CE2"),
                                            hasReport = true,
                                        ),
                                    ),
                            ),
                        ),
                    isLoading = false,
                ),
            onNavigateToStudents = {},
            onNavigateToDailyReport = {},
            onNavigateToSchedule = {},
            onNavigateToEssReport = {},
            onNavigateToSettings = {},
            onDateSelected = {},
            onPreviousWeekClicked = {},
            onNextWeekClicked = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenEmptyPreview() {
    AeshAssistantTheme {
        HomeScreen(
            uiState = HomeUiState(lessonBlocks = emptyList(), isLoading = false),
            onNavigateToStudents = {},
            onNavigateToDailyReport = {},
            onNavigateToSchedule = {},
            onNavigateToEssReport = {},
            onNavigateToSettings = {},
            onDateSelected = {},
            onPreviousWeekClicked = {},
            onNextWeekClicked = {},
        )
    }
}
