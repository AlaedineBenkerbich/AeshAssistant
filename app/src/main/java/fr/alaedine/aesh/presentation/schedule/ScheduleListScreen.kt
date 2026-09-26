package fr.alaedine.aesh.presentation.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.domain.model.ScheduleSlot
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import org.koin.androidx.compose.koinViewModel

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Stateful entry point wired to [ScheduleListViewModel]. Kept separate from
 * the stateless [ScheduleListScreen] so the latter has no Android/ViewModel
 * dependencies and stays trivially previewable and testable.
 */
@Composable
fun ScheduleListRoute(
    onAddScheduleSlot: () -> Unit,
    onEditScheduleSlot: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleListScreen(
        uiState = uiState,
        onAddScheduleSlot = onAddScheduleSlot,
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
                title = { Text(text = "Weekly schedule") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddScheduleSlot) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add class slot")
            }
        },
    ) { contentPadding ->
        if (uiState.scheduleSlots.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No classes yet. Tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            // Slots already arrive ordered Monday-first by day then start time (see
            // `ScheduleSlotDao.observeAll`), so grouping preserves that order without re-sorting.
            val scheduleSlotsByDay = uiState.scheduleSlots.groupBy { it.dayOfWeek }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                scheduleSlotsByDay.forEach { (dayOfWeek, scheduleSlots) ->
                    item(key = "header-${dayOfWeek.name}") {
                        Text(
                            text = dayOfWeek.displayName(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(items = scheduleSlots, key = { it.id }) { scheduleSlot ->
                        ScheduleSlotRow(
                            scheduleSlot = scheduleSlot,
                            onClick = { onEditScheduleSlot(scheduleSlot.id) },
                            onDeleteClick = { onDeleteRequested(scheduleSlot) },
                        )
                    }
                }
            }
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

@Composable
private fun ScheduleSlotRow(scheduleSlot: ScheduleSlot, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = scheduleSlot.subject, style = MaterialTheme.typography.titleMedium)
                Text(text = scheduleSlot.formattedSubtitle(), style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete ${scheduleSlot.subject}",
                )
            }
        }
    }
}

@Composable
private fun DeleteScheduleSlotConfirmationDialog(scheduleSlot: ScheduleSlot, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete ${scheduleSlot.subject}?") },
        text = { Text(text = "This will permanently remove this class slot and cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

private fun ScheduleSlot.formattedSubtitle(): String {
    val timeRange = "${startTime.format(TIME_FORMATTER)} – ${endTime.format(TIME_FORMATTER)}"
    return if (room.isNotBlank()) "$timeRange • $room" else timeRange
}

private fun DayOfWeek.displayName(): String = getDisplayName(TextStyle.FULL, Locale.getDefault())

@Preview(showBackground = true)
@Composable
private fun ScheduleListScreenPreview() {
    AeshAssistantTheme {
        ScheduleListScreen(
            uiState = ScheduleListUiState(
                scheduleSlots = listOf(
                    ScheduleSlot(
                        id = 1L,
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(8, 30),
                        endTime = LocalTime.of(9, 30),
                        subject = "Mathématiques",
                        room = "B12",
                    ),
                    ScheduleSlot(
                        id = 2L,
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(9, 30),
                        endTime = LocalTime.of(10, 30),
                        subject = "Français",
                        room = "B12",
                    ),
                    ScheduleSlot(
                        id = 3L,
                        dayOfWeek = DayOfWeek.TUESDAY,
                        startTime = LocalTime.of(8, 30),
                        endTime = LocalTime.of(9, 30),
                        subject = "Sport",
                        room = "Gymnase",
                    ),
                ),
                isLoading = false,
            ),
            onAddScheduleSlot = {},
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
        val mathSlot = ScheduleSlot(
            id = 1L,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(8, 30),
            endTime = LocalTime.of(9, 30),
            subject = "Mathématiques",
            room = "B12",
        )
        ScheduleListScreen(
            uiState = ScheduleListUiState(
                scheduleSlots = listOf(mathSlot),
                isLoading = false,
                pendingDeletion = mathSlot,
            ),
            onAddScheduleSlot = {},
            onEditScheduleSlot = {},
            onNavigateBack = {},
            onDeleteRequested = {},
            onDeleteCancelled = {},
            onDeleteConfirmed = {},
        )
    }
}
