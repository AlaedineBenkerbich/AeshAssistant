package fr.alaedine.aesh.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.R
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.components.EmptyState
import fr.alaedine.aesh.presentation.components.GroupedListItem
import fr.alaedine.aesh.presentation.components.GroupedListItemSpacing
import fr.alaedine.aesh.presentation.components.InitialsAvatar
import fr.alaedine.aesh.presentation.navigation.AeshBottomNavTab
import fr.alaedine.aesh.presentation.navigation.AeshBottomNavigationBar
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import fr.alaedine.aesh.presentation.theme.AeshTheme
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
 * Bottom padding that keeps the last student row clear of the floating
 * action button once scrolled to the end: the FAB's height plus its margin
 * from the bottom bar, with some breathing room.
 */
private val FAB_CLEARANCE = 96.dp

/**
 * Stateful entry point wired to [HomeViewModel]. Kept separate from the
 * stateless [HomeScreen] so the latter has no Android/ViewModel dependencies
 * and stays trivially previewable and testable.
 */
@Composable
fun HomeRoute(
    onNavigateToDailyReport: (Long?, LocalDate?) -> Unit,
    onTabSelected: (AeshBottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onNavigateToDailyReport = onNavigateToDailyReport,
        onTabSelected = onTabSelected,
        onDateSelected = viewModel::onDateSelected,
        onPreviousWeekClicked = viewModel::onPreviousWeekClicked,
        onNextWeekClicked = viewModel::onNextWeekClicked,
        modifier = modifier,
    )
}

/**
 * The app's main entry point: a freely browsable, calendar-style week strip
 * (see [CalendarWeekHeader]) that can jump to any day — via the previous/
 * next week arrows, tapping a day cell, picking a date outright from the
 * month/year label's date picker, or the top bar's "Today" action — rather
 * than being stuck on the present. Below it, a summary of how many of the
 * selected day's observations are filled in (see [DayProgressCard]), then a
 * per-student breakdown of whether each daily report has been filled out
 * yet, segmented lesson by lesson: one [LessonBlockHeader] per
 * [fr.alaedine.aesh.domain.model.ScheduleSlot] on that day, in schedule
 * order, each followed by its students grouped into one rounded block (see
 * [StudentReportStatusRow]), so only students who actually have a class then
 * show up — solely the schedule determines who needs an observation.
 * Tapping a student row jumps straight into the
 * [fr.alaedine.aesh.presentation.report.DailyReportFormScreen] with that
 * student and [uiState]'s currently selected date preselected — editing or
 * completing an observation for a specific student on a specific day is
 * the far more common action — while the "New observation" button opens the
 * same form without preselecting anyone, letting the AESH manually log an
 * observation for any known student even if they weren't on the selected
 * day's schedule. That button shrinks to its icon once the list is
 * scrolled, and the list keeps room for it at its end, so it never hides a
 * student row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onNavigateToDailyReport: (Long?, LocalDate?) -> Unit,
    onTabSelected: (AeshBottomNavTab) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousWeekClicked: () -> Unit,
    onNextWeekClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val isFabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val isToday = uiState.date == uiState.today
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.home_title)) },
                actions = {
                    // Only shown once the user has actually navigated away from today,
                    // since there'd be nothing to "return" to otherwise.
                    if (!isToday) {
                        TextButton(onClick = { onDateSelected(uiState.today) }) {
                            Text(text = stringResource(R.string.action_today))
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            AeshBottomNavigationBar(
                currentTab = AeshBottomNavTab.Dashboard,
                onTabSelected = onTabSelected,
            )
        },
        floatingActionButton = {
            val newObservationLabel = stringResource(R.string.home_new_observation)
            ExtendedFloatingActionButton(
                text = { Text(text = newObservationLabel) },
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                expanded = isFabExpanded,
                onClick = { onNavigateToDailyReport(null, null) },
                // Keeps the button labelled for screen readers once it has shrunk to its icon.
                modifier = Modifier.semantics { contentDescription = newObservationLabel },
            )
        },
    ) { contentPadding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + FAB_CLEARANCE,
                ),
        ) {
            item(key = "calendar") {
                CalendarWeekHeader(
                    selectedDate = uiState.date,
                    today = uiState.today,
                    weekDates = uiState.weekDates,
                    daysWithScheduledClasses = uiState.daysWithScheduledClasses,
                    onDateSelected = onDateSelected,
                    onPreviousWeekClicked = onPreviousWeekClicked,
                    onNextWeekClicked = onNextWeekClicked,
                )
            }
            if (!uiState.isLoading && uiState.scheduledStudentCount > 0) {
                item(key = "progress") {
                    DayProgressCard(
                        completedCount = uiState.completedReportCount,
                        totalCount = uiState.scheduledStudentCount,
                        isToday = isToday,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            if (uiState.lessonBlocks.isEmpty() && !uiState.isLoading) {
                item(key = "empty") {
                    EmptyState(
                        icon = painterResource(id = R.drawable.ic_schedule),
                        title =
                            stringResource(
                                if (isToday) R.string.home_empty_title_today else R.string.home_empty_title_other_day,
                            ),
                        message = stringResource(R.string.home_empty_message),
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            } else {
                uiState.lessonBlocks.forEach { lessonBlock ->
                    item(key = "lesson-header-${lessonBlock.scheduleSlotId}") {
                        LessonBlockHeader(
                            lessonBlock = lessonBlock,
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                        )
                    }
                    itemsIndexed(
                        items = lessonBlock.studentStatuses,
                        key = { _, status -> "${lessonBlock.scheduleSlotId}-${status.student.id}" },
                    ) { index, status ->
                        StudentReportStatusRow(
                            status = status,
                            index = index,
                            count = lessonBlock.studentStatuses.size,
                            onClick = { onNavigateToDailyReport(status.student.id, uiState.date) },
                            modifier = Modifier.padding(top = if (index == 0) 0.dp else GroupedListItemSpacing),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Calendar-style dashboard header enabling free navigation instead of being
 * stuck on the present: tapping the month/year label opens a full
 * [DatePickerDialog] to jump straight to any day, the previous/next-week
 * arrows next to it shift [selectedDate] by exactly one week (see
 * [onPreviousWeekClicked]/[onNextWeekClicked]), and the strip below shows
 * [weekDates] (Monday to Friday of [selectedDate]'s week, plus the weekend
 * days that matter, see [HomeUiState.weekDates]) — tapping any of those day
 * cells selects it (see [onDateSelected]). [selectedDate] is filled in and
 * [today] outlined when they differ, so the user can always tell which day
 * they're viewing versus which day it actually is; a small dot marks any day
 * with at least one entry in [daysWithScheduledClasses]. The arrows sit
 * together at the end, away from the label, so the label never moves when
 * the top bar's "Today" action appears.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarWeekHeader(
    selectedDate: LocalDate,
    today: LocalDate,
    weekDates: List<LocalDate>,
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The arrows are measured first (the label is weighted), so even a long month name at a
            // large font size only gets ellipsized rather than pushing them off the card.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(
                                onClickLabel = stringResource(R.string.cd_pick_date),
                                onClick = { isDatePickerVisible = true },
                            ).padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Text(
                        text = monthYearLabel(selectedDate, locale),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                }
                Row {
                    IconButton(onClick = onPreviousWeekClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.cd_previous_week),
                        )
                    }
                    IconButton(onClick = onNextWeekClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.cd_next_week),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                weekDates.forEach { date ->
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
 * when the selected day is today), and an event dot when
 * [hasScheduledClasses]. Tapping the cell invokes [onClick] to select that
 * day.
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
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected || isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .then(
                        if (isToday && !isSelected) {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
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
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (hasScheduledClasses) MaterialTheme.colorScheme.tertiary else Color.Transparent),
        )
    }
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

/**
 * Summary of the selected day: a ring filling up as observations get filled
 * in ([completedCount] of [totalCount], the number of students scheduled
 * that day), and how many are left. A gentle nudge rather than an alarm —
 * a missing observation is a to-do, not an error — turning to the success
 * color, with a word of congratulation, once every observation is in.
 */
@Composable
private fun DayProgressCard(
    completedCount: Int,
    totalCount: Int,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val isComplete = completedCount >= totalCount
    val success = AeshTheme.extendedColors.success
    val containerColor = if (isComplete) success.container else MaterialTheme.colorScheme.surfaceBright
    val contentColor = if (isComplete) success.onContainer else MaterialTheme.colorScheme.onSurface
    val progressColor = if (isComplete) success.color else MaterialTheme.colorScheme.primary
    val title = stringResource(if (isToday) R.string.home_progress_title_today else R.string.home_progress_title_other_day)
    val remainingCount = totalCount - completedCount
    val subtitle =
        if (isComplete) {
            stringResource(R.string.home_progress_all_done)
        } else {
            pluralStringResource(R.plurals.home_progress_remaining, remainingCount, remainingCount)
        }
    val progressDescription = stringResource(R.string.cd_home_progress, completedCount, totalCount)
    Surface(
        // Read out as one sentence: the "3/5" inside the ring means little on its own.
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$title. $progressDescription. $subtitle" },
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (totalCount == 0) 0f else completedCount.toFloat() / totalCount },
                    modifier = Modifier.size(56.dp),
                    color = progressColor,
                    trackColor = progressColor.copy(alpha = 0.2f),
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    text = stringResource(R.string.home_progress_count, completedCount, totalCount),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isComplete) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Section header introducing one lesson of the dashboard's student list:
 * [lessonBlock]'s subject alongside its time range and room. Every
 * [StudentReportStatusRow] that follows, until the next header, belongs to
 * this class.
 */
@Composable
private fun LessonBlockHeader(
    lessonBlock: LessonBlock,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = lessonBlock.subject,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = lessonBlock.timeAndRoomLabel(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** E.g. "09:00 – 10:00 • Room 12", omitting the room when blank. */
private fun LessonBlock.timeAndRoomLabel(): String {
    val timeRange = "${startTime.format(TIME_FORMATTER)} – ${endTime.format(TIME_FORMATTER)}"
    return listOfNotNull(timeRange, room.takeIf { it.isNotBlank() }).joinToString(" • ")
}

/**
 * A single student of a lesson, drawn as the item at [index] of the lesson's
 * group of [count] students: their initials avatar, first name and class,
 * and a chip saying whether their report for the selected day is done or
 * still to fill in (see [ReportStatusChip]). Tapping the row invokes
 * [onClick] to jump straight into the
 * [fr.alaedine.aesh.presentation.report.DailyReportFormScreen] with this
 * student and the dashboard's currently selected date preselected, since
 * editing or completing their observation for that day is the far more
 * common action. Screen readers announce it as one element: what a tap does,
 * then the report's status.
 */
@Composable
private fun StudentReportStatusRow(
    status: StudentReportStatus,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openDailyReportLabel =
        stringResource(
            R.string.cd_open_daily_report_for_student,
            stringResource(R.string.student_display_name, status.student.firstName, status.student.className),
        )
    val reportStatusLabel = stringResource(if (status.hasReport) R.string.cd_report_completed else R.string.cd_report_missing)
    GroupedListItem(
        index = index,
        count = count,
        onClick = onClick,
        modifier =
            modifier.semantics(mergeDescendants = true) {
                contentDescription = openDailyReportLabel
                stateDescription = reportStatusLabel
            },
        headlineContent = { Text(text = status.student.firstName) },
        supportingContent = { Text(text = status.student.className) },
        leadingContent = { InitialsAvatar(name = status.student.firstName, colorKey = status.student.id) },
        trailingContent = { ReportStatusChip(hasReport = status.hasReport) },
    )
}

/**
 * Compact status pill: a check and "Done" in the success color when
 * [hasReport], a pencil and "To fill in" in the calmer pending color
 * otherwise.
 */
@Composable
private fun ReportStatusChip(
    hasReport: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = if (hasReport) AeshTheme.extendedColors.success else AeshTheme.extendedColors.pending
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = colors.container,
        contentColor = colors.onContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (hasReport) Icons.Default.Check else Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(if (hasReport) R.string.home_status_done else R.string.home_status_to_fill_in),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private val previewLessonBlocks =
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
    )

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    AeshAssistantTheme {
        HomeScreen(
            uiState =
                HomeUiState(
                    lessonBlocks = previewLessonBlocks,
                    daysWithScheduledClasses = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                    isLoading = false,
                ),
            onNavigateToDailyReport = { _, _ -> },
            onTabSelected = {},
            onDateSelected = {},
            onPreviousWeekClicked = {},
            onNextWeekClicked = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenDarkPreview() {
    AeshAssistantTheme(darkTheme = true) {
        HomeScreen(
            uiState =
                HomeUiState(
                    lessonBlocks = previewLessonBlocks,
                    daysWithScheduledClasses = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                    isLoading = false,
                ),
            onNavigateToDailyReport = { _, _ -> },
            onTabSelected = {},
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
            onNavigateToDailyReport = { _, _ -> },
            onTabSelected = {},
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
            onNavigateToDailyReport = { _, _ -> },
            onTabSelected = {},
            onDateSelected = {},
            onPreviousWeekClicked = {},
            onNextWeekClicked = {},
        )
    }
}
