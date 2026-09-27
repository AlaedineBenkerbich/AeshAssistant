package fr.alaedine.aesh.presentation.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.R
import fr.alaedine.aesh.domain.model.ScheduleSlot
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import org.koin.androidx.compose.koinViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

/** Width reserved for the hour-of-day labels down the left edge of [WeeklyScheduleGrid]. */
private val TIME_AXIS_WIDTH = 40.dp

/** Vertical space representing one hour in [WeeklyScheduleGrid]; every slot's block height/offset scales from this. */
private val HOUR_HEIGHT = 64.dp

/**
 * [WeeklyScheduleGrid] always spans at least this hour range so it keeps a
 * consistent, calendar-like shape even on a day with only one short class;
 * it still expands to fit slots scheduled outside these hours.
 */
private const val DEFAULT_GRID_START_HOUR = 8
private const val DEFAULT_GRID_END_HOUR = 17

/** Floor for a rendered block's height so a slot with a degenerate (zero or negative) duration stays visible and tappable. */
private const val MIN_BLOCK_DURATION_MINUTES = 15

/**
 * Stateful entry point wired to [ScheduleListViewModel]. Kept separate from
 * the stateless [ScheduleListScreen] so the latter has no Android/ViewModel
 * dependencies and stays trivially previewable and testable.
 */
@Composable
fun ScheduleListRoute(
    onAddScheduleSlot: () -> Unit,
    onScanScheduleSlot: () -> Unit,
    onEditScheduleSlot: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleListScreen(
        uiState = uiState,
        onAddScheduleSlot = onAddScheduleSlot,
        onScanScheduleSlot = onScanScheduleSlot,
        onEditScheduleSlot = onEditScheduleSlot,
        onNavigateBack = onNavigateBack,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteCancelled = viewModel::onDeleteCancelled,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleListScreen(
    uiState: ScheduleListUiState,
    onAddScheduleSlot: () -> Unit,
    onScanScheduleSlot: () -> Unit,
    onEditScheduleSlot: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onDeleteRequested: (ScheduleSlot) -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.schedule_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onScanScheduleSlot) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_camera),
                            contentDescription = stringResource(R.string.cd_scan_schedule),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddScheduleSlot) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_schedule_slot))
            }
        },
    ) { contentPadding ->
        if (uiState.scheduleSlots.isEmpty() && !uiState.isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.schedule_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            WeeklyScheduleGrid(
                scheduleSlots = uiState.scheduleSlots,
                studentsById = uiState.studentsById,
                today = uiState.today,
                onEditScheduleSlot = onEditScheduleSlot,
                onDeleteRequested = onDeleteRequested,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(16.dp),
            )
        }
    }

    val scheduleSlotPendingDeletion = uiState.pendingDeletion
    if (scheduleSlotPendingDeletion != null) {
        DeleteScheduleSlotConfirmationDialog(
            scheduleSlot = scheduleSlotPendingDeletion,
            onConfirm = onDeleteConfirmed,
            onDismiss = onDeleteCancelled,
        )
    }
}

/**
 * Renders [scheduleSlots] as a time-based weekly grid, evoking a real
 * timetable/calendar app rather than a plain list: one column per day that
 * actually has a class (Monday-first, matching [scheduleSlots]' existing
 * order), a shared hour axis on the left ([TimeAxisColumn]), hourly
 * gridlines, and every slot rendered as a colored block ([ScheduleSlotBlock])
 * positioned and sized from its start time and duration. Colors are assigned
 * per-subject (see [subjectColorPalette]) so recurring subjects are easy to
 * spot at a glance, and [today]'s column is highlighted the same way
 * [fr.alaedine.aesh.presentation.home.HomeScreen]'s week strip highlights
 * the current day.
 */
@Composable
private fun WeeklyScheduleGrid(
    scheduleSlots: List<ScheduleSlot>,
    studentsById: Map<Long, Student>,
    today: DayOfWeek,
    onEditScheduleSlot: (Long) -> Unit,
    onDeleteRequested: (ScheduleSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read through LocalLocale (rather than Locale.getDefault()) so this
    // recomposes if the user changes the system locale while the app is running.
    val locale = LocalLocale.current.platformLocale
    // Slots already arrive ordered Monday-first by day then start time (see
    // `ScheduleSlotDao.observeAll`), so grouping preserves that order without
    // re-sorting, and only days with at least one class become columns.
    val scheduleSlotsByDay = scheduleSlots.groupBy { it.dayOfWeek }
    val days = scheduleSlotsByDay.keys.toList()
    val gridStartHour =
        minOf(scheduleSlots.minOfOrNull { it.startTime.hour } ?: DEFAULT_GRID_START_HOUR, DEFAULT_GRID_START_HOUR)
    val gridEndHour =
        maxOf(scheduleSlots.maxOfOrNull { it.endTime.hourCeil() } ?: DEFAULT_GRID_END_HOUR, DEFAULT_GRID_END_HOUR)
    val hourCount = gridEndHour - gridStartHour
    val subjectColors = subjectColorPalette(scheduleSlots.map { it.subject })

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(TIME_AXIS_WIDTH))
            days.forEach { dayOfWeek ->
                DayHeaderCell(
                    dayOfWeek = dayOfWeek,
                    isToday = dayOfWeek == today,
                    locale = locale,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 24.dp),
        ) {
            TimeAxisColumn(
                gridStartHour = gridStartHour,
                hourCount = hourCount,
                modifier = Modifier.width(TIME_AXIS_WIDTH),
            )
            days.forEach { dayOfWeek ->
                DayColumn(
                    scheduleSlots = scheduleSlotsByDay[dayOfWeek].orEmpty(),
                    studentsById = studentsById,
                    gridStartHour = gridStartHour,
                    hourCount = hourCount,
                    isToday = dayOfWeek == today,
                    subjectColors = subjectColors,
                    onEditScheduleSlot = onEditScheduleSlot,
                    onDeleteRequested = onDeleteRequested,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Assigns each distinct subject a stable, theme-adapting color pair
 * (container color to content color) from a small rotating palette, so e.g.
 * every "Mathématiques" block looks the same across the week.
 */
@Composable
private fun subjectColorPalette(subjects: List<String>): Map<String, Pair<Color, Color>> {
    val palette =
        listOf(
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        )
    return subjects.distinct().mapIndexed { index, subject -> subject to palette[index % palette.size] }.toMap()
}

/**
 * A single day-column header: short weekday label, bolded and tinted
 * primary with a small dot underneath when [isToday] — mirrors
 * [fr.alaedine.aesh.presentation.home.HomeScreen]'s calendar week strip.
 */
@Composable
private fun DayHeaderCell(
    dayOfWeek: DayOfWeek,
    isToday: Boolean,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = dayOfWeek.shortDisplayName(locale),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Box(
            modifier =
                Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

/**
 * The shared hour-of-day labels ("08:00", "09:00", …) down the left edge of
 * [WeeklyScheduleGrid], one per gridline so they line up with [DayColumn]'s
 * hour rows.
 */
@Composable
private fun TimeAxisColumn(
    gridStartHour: Int,
    hourCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.height(HOUR_HEIGHT * hourCount)) {
        (0..hourCount).forEach { index ->
            Text(
                text = "${(gridStartHour + index).toString().padStart(2, '0')}:00",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .offset(y = HOUR_HEIGHT * index)
                        .padding(end = 4.dp),
            )
        }
    }
}

/**
 * One day's column of [ScheduleSlotBlock]s in [WeeklyScheduleGrid]: hourly
 * gridlines spanning [hourCount] equal-height rows from [gridStartHour], a
 * faint tint when [isToday], and every one of [scheduleSlots] absolutely
 * positioned/sized from its start time and duration so classes read at a
 * glance the way they would on a paper timetable.
 */
@Composable
private fun DayColumn(
    scheduleSlots: List<ScheduleSlot>,
    studentsById: Map<Long, Student>,
    gridStartHour: Int,
    hourCount: Int,
    isToday: Boolean,
    subjectColors: Map<String, Pair<Color, Color>>,
    onEditScheduleSlot: (Long) -> Unit,
    onDeleteRequested: (ScheduleSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(HOUR_HEIGHT * hourCount)
                .background(if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent),
    ) {
        // Hour gridlines: `hourCount` equal-height rows, each topped by a
        // divider, plus one trailing divider for the grid's bottom edge.
        Column(modifier = Modifier.matchParentSize()) {
            repeat(hourCount) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        scheduleSlots.forEach { scheduleSlot ->
            val startMinutes = scheduleSlot.startTime.hour * 60 + scheduleSlot.startTime.minute
            val endMinutes = scheduleSlot.endTime.hour * 60 + scheduleSlot.endTime.minute
            val offsetMinutes = startMinutes - gridStartHour * 60
            val durationMinutes = (endMinutes - startMinutes).coerceAtLeast(MIN_BLOCK_DURATION_MINUTES)
            val (containerColor, contentColor) =
                subjectColors[scheduleSlot.subject]
                    ?: (MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer)
            ScheduleSlotBlock(
                scheduleSlot = scheduleSlot,
                studentNames = scheduleSlot.studentIds.mapNotNull { studentsById[it]?.firstName },
                containerColor = containerColor,
                contentColor = contentColor,
                onEditClick = { onEditScheduleSlot(scheduleSlot.id) },
                onDeleteClick = { onDeleteRequested(scheduleSlot) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .offset(y = HOUR_HEIGHT * (offsetMinutes / 60f))
                        .height(HOUR_HEIGHT * (durationMinutes / 60f))
                        .padding(horizontal = 2.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * A single class block within [DayColumn]. Tapping it opens a dropdown menu
 * with the slot's full details plus edit/delete actions, since the block
 * itself is usually too small to host separate icon buttons; the same
 * options are also exposed directly as TalkBack custom accessibility
 * actions, letting screen reader users edit/delete without opening the menu
 * first.
 */
@Composable
private fun ScheduleSlotBlock(
    scheduleSlot: ScheduleSlot,
    studentNames: List<String>,
    containerColor: Color,
    contentColor: Color,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showActionsMenu by remember { mutableStateOf(false) }
    val actionsLabel = stringResource(R.string.cd_schedule_slot_options, scheduleSlot.subject)
    val editLabel = stringResource(R.string.cd_edit_schedule_slot, scheduleSlot.subject)
    val deleteLabel = stringResource(R.string.cd_delete_schedule_slot, scheduleSlot.subject)
    Box(modifier = modifier) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.extraSmall,
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(onClick = { showActionsMenu = true })
                    .semantics {
                        contentDescription = actionsLabel
                        customActions =
                            listOf(
                                CustomAccessibilityAction(editLabel) {
                                    onEditClick()
                                    true
                                },
                                CustomAccessibilityAction(deleteLabel) {
                                    onDeleteClick()
                                    true
                                },
                            )
                    },
        ) {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text(
                    text = scheduleSlot.subject,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = scheduleSlot.timeRangeLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = showActionsMenu, onDismissRequest = { showActionsMenu = false }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = scheduleSlot.subject,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = scheduleSlot.detailLabel(studentNames),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.action_edit)) },
                leadingIcon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    showActionsMenu = false
                    onEditClick()
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    showActionsMenu = false
                    onDeleteClick()
                },
            )
        }
    }
}

@Composable
private fun DeleteScheduleSlotConfirmationDialog(
    scheduleSlot: ScheduleSlot,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.confirm_delete_title, scheduleSlot.subject)) },
        text = { Text(text = stringResource(R.string.delete_schedule_slot_confirm_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Rounds up to the next whole hour, e.g. 9:00 -> 9, 9:15 -> 10; used to size [WeeklyScheduleGrid] tall enough to fit every slot. */
private fun LocalTime.hourCeil(): Int = if (minute == 0) hour else hour + 1

/** E.g. "08:30 – 09:30". */
private fun ScheduleSlot.timeRangeLabel(): String = "${startTime.format(TIME_FORMATTER)} – ${endTime.format(TIME_FORMATTER)}"

/** Builds the slot's time/room/assigned-students detail line shown in its long-press dropdown, skipping room and/or students when there's nothing to show. */
private fun ScheduleSlot.detailLabel(studentNames: List<String>): String {
    val parts =
        listOfNotNull(
            timeRangeLabel(),
            room.takeIf { it.isNotBlank() },
            studentNames.takeIf { it.isNotEmpty() }?.joinToString(", "),
        )
    return parts.joinToString(" • ")
}

/** E.g. "Mon" (or "lun." in French), with the first letter capitalized regardless of the locale's own casing convention. */
private fun DayOfWeek.shortDisplayName(locale: Locale): String =
    getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) }

@Preview(showBackground = true)
@Composable
private fun ScheduleListScreenPreview() {
    AeshAssistantTheme {
        val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
        val amir = Student(id = 2L, firstName = "Amir", className = "CM2")
        ScheduleListScreen(
            uiState =
                ScheduleListUiState(
                    scheduleSlots =
                        listOf(
                            ScheduleSlot(
                                id = 1L,
                                dayOfWeek = DayOfWeek.MONDAY,
                                startTime = LocalTime.of(8, 30),
                                endTime = LocalTime.of(9, 30),
                                subject = "Mathématiques",
                                room = "B12",
                                studentIds = listOf(alice.id, amir.id),
                            ),
                            ScheduleSlot(
                                id = 2L,
                                dayOfWeek = DayOfWeek.MONDAY,
                                startTime = LocalTime.of(9, 30),
                                endTime = LocalTime.of(10, 30),
                                subject = "Français",
                                room = "B12",
                                studentIds = listOf(alice.id),
                            ),
                            ScheduleSlot(
                                id = 3L,
                                dayOfWeek = DayOfWeek.TUESDAY,
                                startTime = LocalTime.of(8, 30),
                                endTime = LocalTime.of(9, 30),
                                subject = "Sport",
                                room = "Gymnase",
                                studentIds = listOf(amir.id),
                            ),
                        ),
                    studentsById = listOf(alice, amir).associateBy { it.id },
                    today = DayOfWeek.MONDAY,
                    isLoading = false,
                ),
            onAddScheduleSlot = {},
            onScanScheduleSlot = {},
            onEditScheduleSlot = {},
            onNavigateBack = {},
            onDeleteRequested = {},
            onDeleteCancelled = {},
            onDeleteConfirmed = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ScheduleListScreenFullWeekPreview() {
    AeshAssistantTheme {
        val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
        val amir = Student(id = 2L, firstName = "Amir", className = "CM2")

        fun slot(
            id: Long,
            dayOfWeek: DayOfWeek,
            startHour: Int,
            startMinute: Int,
            endHour: Int,
            endMinute: Int,
            subject: String,
            room: String,
        ) = ScheduleSlot(
            id = id,
            dayOfWeek = dayOfWeek,
            startTime = LocalTime.of(startHour, startMinute),
            endTime = LocalTime.of(endHour, endMinute),
            subject = subject,
            room = room,
            studentIds = listOf(alice.id, amir.id),
        )
        ScheduleListScreen(
            uiState =
                ScheduleListUiState(
                    scheduleSlots =
                        listOf(
                            slot(1L, DayOfWeek.MONDAY, 8, 30, 9, 30, "Mathématiques", "B12"),
                            slot(2L, DayOfWeek.MONDAY, 9, 30, 10, 0, "Français", "B12"),
                            slot(3L, DayOfWeek.MONDAY, 10, 30, 12, 0, "Sciences", "B14"),
                            slot(4L, DayOfWeek.TUESDAY, 8, 30, 9, 30, "Mathématiques", "B12"),
                            slot(5L, DayOfWeek.TUESDAY, 10, 0, 11, 0, "Sport", "Gymnase"),
                            slot(6L, DayOfWeek.WEDNESDAY, 8, 30, 10, 30, "Français", "B12"),
                            slot(7L, DayOfWeek.THURSDAY, 8, 30, 9, 0, "Musique", "B03"),
                            slot(8L, DayOfWeek.FRIDAY, 8, 30, 9, 30, "Mathématiques", "B12"),
                            slot(9L, DayOfWeek.FRIDAY, 13, 0, 14, 30, "Sport", "Gymnase"),
                        ),
                    studentsById = listOf(alice, amir).associateBy { it.id },
                    today = DayOfWeek.WEDNESDAY,
                    isLoading = false,
                ),
            onAddScheduleSlot = {},
            onScanScheduleSlot = {},
            onEditScheduleSlot = {},
            onNavigateBack = {},
            onDeleteRequested = {},
            onDeleteCancelled = {},
            onDeleteConfirmed = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleListScreenEmptyPreview() {
    AeshAssistantTheme {
        ScheduleListScreen(
            uiState = ScheduleListUiState(scheduleSlots = emptyList(), isLoading = false),
            onAddScheduleSlot = {},
            onScanScheduleSlot = {},
            onEditScheduleSlot = {},
            onNavigateBack = {},
            onDeleteRequested = {},
            onDeleteCancelled = {},
            onDeleteConfirmed = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleListScreenDeleteConfirmationPreview() {
    AeshAssistantTheme {
        val mathSlot =
            ScheduleSlot(
                id = 1L,
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(8, 30),
                endTime = LocalTime.of(9, 30),
                subject = "Mathématiques",
                room = "B12",
            )
        ScheduleListScreen(
            uiState =
                ScheduleListUiState(
                    scheduleSlots = listOf(mathSlot),
                    isLoading = false,
                    pendingDeletion = mathSlot,
                ),
            onAddScheduleSlot = {},
            onScanScheduleSlot = {},
            onEditScheduleSlot = {},
            onNavigateBack = {},
            onDeleteRequested = {},
            onDeleteCancelled = {},
            onDeleteConfirmed = {},
        )
    }
}
