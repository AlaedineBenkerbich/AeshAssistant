package fr.alaedine.aesh.presentation.student

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.R
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful entry point wired to [StudentFormViewModel]. Kept separate from
 * the stateless [StudentFormScreen] so the latter has no Android/ViewModel
 * dependencies and stays trivially previewable and testable.
 *
 * @param studentId `null` to add a new student, or the id of the student to edit.
 */
@Composable
fun StudentFormRoute(
    studentId: Long?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudentFormViewModel = koinViewModel(parameters = { parametersOf(studentId) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    StudentFormScreen(
        uiState = uiState,
        onFirstNameChanged = viewModel::onFirstNameChanged,
        onClassNameChanged = viewModel::onClassNameChanged,
        onPpsGoalsChanged = viewModel::onPpsGoalsChanged,
        onSaveClicked = viewModel::onSaveClicked,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

/**
 * Form to add or edit a student: their first name, class and PPS goals in
 * one rounded section on a tinted background, with Save in the top bar. The
 * keyboard capitalizes the first name and class as they're typed and moves
 * from one field to the next.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentFormScreen(
    uiState: StudentFormUiState,
    onFirstNameChanged: (String) -> Unit,
    onClassNameChanged: (String) -> Unit,
    onPpsGoalsChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =
                            stringResource(
                                if (uiState.isEditing) R.string.student_form_title_edit else R.string.student_form_title_add,
                            ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = onSaveClicked,
                        enabled = uiState.canSave,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(text = stringResource(R.string.action_save))
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
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceBright,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.firstName,
                        onValueChange = onFirstNameChanged,
                        label = { Text(text = stringResource(R.string.student_form_first_name)) },
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.className,
                        onValueChange = onClassNameChanged,
                        label = { Text(text = stringResource(R.string.student_form_class)) },
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.ppsGoals,
                        onValueChange = onPpsGoalsChanged,
                        label = { Text(text = stringResource(R.string.student_form_pps_goals)) },
                        supportingText = { Text(text = stringResource(R.string.student_form_pps_goals_hint)) },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StudentFormScreenAddPreview() {
    AeshAssistantTheme {
        StudentFormScreen(
            uiState = StudentFormUiState(),
            onFirstNameChanged = {},
            onClassNameChanged = {},
            onPpsGoalsChanged = {},
            onSaveClicked = {},
            onNavigateBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StudentFormScreenEditPreview() {
    AeshAssistantTheme {
        StudentFormScreen(
            uiState =
                StudentFormUiState(
                    studentId = 1L,
                    firstName = "Alice",
                    className = "CE2",
                    ppsGoals = "Read aloud daily",
                ),
            onFirstNameChanged = {},
            onClassNameChanged = {},
            onPpsGoalsChanged = {},
            onSaveClicked = {},
            onNavigateBack = {},
        )
    }
}
