package fr.alaedine.aesh.presentation.student

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import org.koin.androidx.compose.koinViewModel

/**
 * Bottom padding that keeps the last student clear of the floating action
 * button once scrolled to the end: the FAB's height plus its margin from the
 * bottom bar, with some breathing room.
 */
private val FAB_CLEARANCE = 96.dp

/**
 * Stateful entry point wired to [StudentListViewModel]. Kept separate from
 * the stateless [StudentListScreen] so the latter has no Android/ViewModel
 * dependencies and stays trivially previewable and testable.
 */
@Composable
fun StudentListRoute(
    onAddStudent: () -> Unit,
    onEditStudent: (Long) -> Unit,
    onTabSelected: (AeshBottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudentListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StudentListScreen(
        uiState = uiState,
        onAddStudent = onAddStudent,
        onEditStudent = onEditStudent,
        onTabSelected = onTabSelected,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteCancelled = viewModel::onDeleteCancelled,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        modifier = modifier,
    )
}

/**
 * Every student as one rounded group of rows (see [StudentRow]) under a
 * count of how many there are, with an "Add student" button that shrinks to
 * its icon once the list is scrolled. Deleting a student, rare and
 * destructive, sits behind each row's options menu rather than one tap away,
 * and still asks for confirmation (see [DeleteStudentConfirmationDialog]).
 * With no student yet, an [EmptyState] explains what the list is for and
 * offers to add the first one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentListScreen(
    uiState: StudentListUiState,
    onAddStudent: () -> Unit,
    onEditStudent: (Long) -> Unit,
    onTabSelected: (AeshBottomNavTab) -> Unit,
    onDeleteRequested: (Student) -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val isFabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val hasStudents = uiState.students.isNotEmpty()
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.student_list_title)) },
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
                currentTab = AeshBottomNavTab.Students,
                onTabSelected = onTabSelected,
            )
        },
        floatingActionButton = {
            // The empty state already offers the same action, front and center.
            if (hasStudents) {
                val addStudentLabel = stringResource(R.string.action_add_student)
                ExtendedFloatingActionButton(
                    text = { Text(text = addStudentLabel) },
                    icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                    expanded = isFabExpanded,
                    onClick = onAddStudent,
                    // Keeps the button labelled for screen readers once it has shrunk to its icon.
                    modifier = Modifier.semantics { contentDescription = addStudentLabel },
                )
            }
        },
    ) { contentPadding ->
        if (!hasStudents && !uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                EmptyState(
                    icon = rememberVectorPainter(Icons.Default.Person),
                    title = stringResource(R.string.student_list_empty_title),
                    message = stringResource(R.string.student_list_empty_message),
                    action = {
                        Button(onClick = onAddStudent) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.padding(end = ButtonDefaults.IconSpacing),
                            )
                            Text(text = stringResource(R.string.action_add_student))
                        }
                    },
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
        } else {
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
                if (hasStudents) {
                    item(key = "count") {
                        Text(
                            text =
                                pluralStringResource(
                                    R.plurals.student_list_count,
                                    uiState.students.size,
                                    uiState.students.size,
                                ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                        )
                    }
                }
                itemsIndexed(items = uiState.students, key = { _, student -> student.id }) { index, student ->
                    StudentRow(
                        student = student,
                        index = index,
                        count = uiState.students.size,
                        onClick = { onEditStudent(student.id) },
                        onDeleteClick = { onDeleteRequested(student) },
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else GroupedListItemSpacing),
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

/**
 * A student, drawn as the item at [index] of the list's group of [count]:
 * initials avatar, first name, class, and an options menu offering to edit
 * or delete them. Tapping the row itself edits the student.
 */
@Composable
private fun StudentRow(
    student: Student,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    GroupedListItem(
        index = index,
        count = count,
        onClick = onClick,
        onClickLabel = stringResource(R.string.action_edit),
        modifier = modifier,
        headlineContent = { Text(text = student.firstName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(text = student.className, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { InitialsAvatar(name = student.firstName, colorKey = student.id) },
        trailingContent = {
            Box {
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_student_options, student.firstName),
                    )
                }
                DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_edit)) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            onClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null) },
                        colors =
                            MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error,
                                leadingIconColor = MaterialTheme.colorScheme.error,
                            ),
                        onClick = {
                            isMenuExpanded = false
                            onDeleteClick()
                        },
                    )
                }
            }
        },
    )
}

/** Asks before deleting [student], spelling out that their daily observations go with them; the confirm button is styled as destructive. */
@Composable
private fun DeleteStudentConfirmationDialog(
    student: Student,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null) },
        title = { Text(text = stringResource(R.string.confirm_delete_title, student.firstName)) },
        text = { Text(text = stringResource(R.string.delete_student_confirm_text)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
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

@Preview(showBackground = true)
@Composable
private fun StudentListScreenPreview() {
    AeshAssistantTheme {
        StudentListScreen(
            uiState =
                StudentListUiState(
                    students =
                        listOf(
                            Student(id = 1L, firstName = "Alice", className = "CE2"),
                            Student(id = 2L, firstName = "Amir", className = "CM2"),
                        ),
                    isLoading = false,
                ),
            onAddStudent = {},
            onEditStudent = {},
            onTabSelected = {},
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
            onTabSelected = {},
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
            uiState =
                StudentListUiState(
                    students = listOf(Student(id = 1L, firstName = "Alice", className = "CE2")),
                    isLoading = false,
                    pendingDeletion = Student(id = 1L, firstName = "Alice", className = "CE2"),
                ),
            onAddStudent = {},
            onEditStudent = {},
            onTabSelected = {},
            onDeleteRequested = {},
            onDeleteCancelled = {},
            onDeleteConfirmed = {},
        )
    }
}
