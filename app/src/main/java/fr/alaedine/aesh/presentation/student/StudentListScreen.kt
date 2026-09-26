package fr.alaedine.aesh.presentation.student

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
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import org.koin.androidx.compose.koinViewModel

/**
 * Stateful entry point wired to [StudentListViewModel]. Kept separate from
 * the stateless [StudentListScreen] so the latter has no Android/ViewModel
 * dependencies and stays trivially previewable and testable.
 */
@Composable
fun StudentListRoute(
    onAddStudent: () -> Unit,
    onEditStudent: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudentListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StudentListScreen(
        uiState = uiState,
        onAddStudent = onAddStudent,
        onEditStudent = onEditStudent,
        onNavigateBack = onNavigateBack,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteCancelled = viewModel::onDeleteCancelled,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentListScreen(
    uiState: StudentListUiState,
    onAddStudent: () -> Unit,
    onEditStudent: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onDeleteRequested: (Student) -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Students") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddStudent) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add student")
            }
        },
    ) { contentPadding ->
        if (uiState.students.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No students yet. Tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = uiState.students, key = { it.id }) { student ->
                    StudentRow(
                        student = student,
                        onClick = { onEditStudent(student.id) },
                        onDeleteClick = { onDeleteRequested(student) },
                    )
                }
            }
        }
    }

    val studentPendingDeletion = uiState.pendingDeletion
    if (studentPendingDeletion != null) {
        DeleteStudentConfirmationDialog(
            student = studentPendingDeletion,
            onConfirm = onDeleteConfirmed,
            onDismiss = onDeleteCancelled,
        )
    }
}

@Composable
private fun StudentRow(student: Student, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = student.firstName, style = MaterialTheme.typography.titleMedium)
                Text(text = student.className, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete ${student.firstName}",
                )
            }
        }
    }
}

@Composable
private fun DeleteStudentConfirmationDialog(student: Student, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete ${student.firstName}?") },
        text = { Text(text = "This will permanently remove this student and cannot be undone.") },
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

@Preview(showBackground = true)
@Composable
private fun StudentListScreenPreview() {
    AeshAssistantTheme {
        StudentListScreen(
            uiState = StudentListUiState(
                students = listOf(
                    Student(id = 1L, firstName = "Alice", className = "CE2"),
                    Student(id = 2L, firstName = "Amir", className = "CM2"),
                ),
                isLoading = false,
            ),
            onAddStudent = {},
            onEditStudent = {},
            onNavigateBack = {},
            onDeleteRequested = {},
            onDeleteCancelled = {},
            onDeleteConfirmed = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StudentListScreenEmptyPreview() {
    AeshAssistantTheme {
        StudentListScreen(
            uiState = StudentListUiState(students = emptyList(), isLoading = false),
            onAddStudent = {},
            onEditStudent = {},
            onNavigateBack = {},
            onDeleteRequested = {},
            onDeleteCancelled = {},
            onDeleteConfirmed = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StudentListScreenDeleteConfirmationPreview() {
    AeshAssistantTheme {
        StudentListScreen(
            uiState = StudentListUiState(
                students = listOf(Student(id = 1L, firstName = "Alice", className = "CE2")),
                isLoading = false,
                pendingDeletion = Student(id = 1L, firstName = "Alice", className = "CE2"),
            ),
            onAddStudent = {},
            onEditStudent = {},
            onNavigateBack = {},
            onDeleteRequested = {},
            onDeleteCancelled = {},
            onDeleteConfirmed = {},
        )
    }
}
