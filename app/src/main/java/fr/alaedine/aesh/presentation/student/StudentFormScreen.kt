package fr.alaedine.aesh.presentation.student

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    Scaffold(
        modifier = modifier.fillMaxSize(),
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
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.firstName,
                onValueChange = onFirstNameChanged,
                label = { Text(text = stringResource(R.string.student_form_first_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.className,
                onValueChange = onClassNameChanged,
                label = { Text(text = stringResource(R.string.student_form_class)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.ppsGoals,
                onValueChange = onPpsGoalsChanged,
                label = { Text(text = stringResource(R.string.student_form_pps_goals)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSaveClicked,
                enabled = uiState.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.action_save))
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
