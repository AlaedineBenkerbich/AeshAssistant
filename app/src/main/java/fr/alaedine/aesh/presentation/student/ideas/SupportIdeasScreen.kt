package fr.alaedine.aesh.presentation.student.ideas

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.R
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.model.SupportIdea
import fr.alaedine.aesh.domain.model.SupportIdeas
import fr.alaedine.aesh.presentation.components.EmptyState
import fr.alaedine.aesh.presentation.components.KeepScreenOn
import fr.alaedine.aesh.presentation.components.OnDeviceAiIntroCard
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Stateful entry point wired to [SupportIdeasViewModel]. Kept separate from
 * the stateless [SupportIdeasScreen] so the latter has no Android/ViewModel
 * dependencies and stays trivially previewable and testable.
 *
 * @param studentId The student to suggest support ideas for.
 * @param onAddObservation Opens the daily observation form for that student,
 * offered while they have no observation to draw ideas from.
 */
@Composable
fun SupportIdeasRoute(
    studentId: Long,
    onNavigateBack: () -> Unit,
    onAddObservation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SupportIdeasViewModel = koinViewModel(parameters = { parametersOf(studentId) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Nothing can be suggested for a student who no longer exists.
    LaunchedEffect(uiState.isStudentMissing) {
        if (uiState.isStudentMissing) onNavigateBack()
    }

    SupportIdeasScreen(
        uiState = uiState,
        onSuggestClicked = viewModel::onSuggestClicked,
        onCancelClicked = viewModel::onCancelClicked,
        onAddObservation = onAddObservation,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

/**
 * Suggests what could be put in place for a student, from their recent daily
 * observations, entirely on-device: a card introducing the on-device AI, a
 * button asking for ideas, then the ideas themselves, each with what in the
 * observations led to it, framed as leads to talk over with the teaching
 * team and easy to copy elsewhere. With no observation yet, an [EmptyState]
 * explains what the ideas need and offers to fill in the first one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportIdeasScreen(
    uiState: SupportIdeasUiState,
    onSuggestClicked: () -> Unit,
    onCancelClicked: () -> Unit,
    onAddObservation: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val student = uiState.student
    val snackbarHostState = remember { SnackbarHostState() }
    val onCopyClicked = copyIdeasAction(uiState.ideas, student?.firstName.orEmpty(), snackbarHostState)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =
                            if (student == null) {
                                stringResource(R.string.support_ideas_title)
                            } else {
                                stringResource(R.string.support_ideas_title_for_student, student.firstName)
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        when {
            student == null -> Unit
            uiState.hasNoObservations ->
                Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                    EmptyState(
                        icon = painterResource(id = R.drawable.ic_auto_awesome),
                        title = stringResource(R.string.support_ideas_empty_title),
                        message = stringResource(R.string.support_ideas_empty_message, student.firstName),
                        action = {
                            Button(onClick = onAddObservation) {
                                Text(text = stringResource(R.string.support_ideas_add_observation))
                            }
                        },
                        modifier = Modifier.padding(top = 48.dp),
                    )
                }
            else ->
                SupportIdeasContent(
                    uiState = uiState,
                    student = student,
                    onSuggestClicked = onSuggestClicked,
                    onCancelClicked = onCancelClicked,
                    onCopyClicked = onCopyClicked,
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
        }
    }
}

@Composable
private fun SupportIdeasContent(
    uiState: SupportIdeasUiState,
    student: Student,
    onSuggestClicked: () -> Unit,
    onCancelClicked: () -> Unit,
    onCopyClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OnDeviceAiIntroCard(text = stringResource(R.string.support_ideas_description, student.firstName))

        Button(
            onClick = onSuggestClicked,
            enabled = uiState.canSuggest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_auto_awesome),
                contentDescription = null,
                modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
            )
            Text(
                text =
                    stringResource(
                        if (uiState.ideas == null) R.string.support_ideas_suggest else R.string.support_ideas_suggest_again,
                    ),
            )
        }

        if (uiState.isGenerating) {
            GeneratingIndicator(onCancelClicked = onCancelClicked)
            KeepScreenOn()
        }

        uiState.failure?.let { failure -> FailureCard(failure = failure) }

        uiState.ideas?.let { ideas ->
            IdeasSection(ideas = ideas, studentFirstName = student.firstName, onCopyClicked = onCopyClicked)
        }
    }
}

/**
 * Copies [ideas] to the clipboard as plain text, titled after the student, to
 * share them with the teaching team or paste them into an ESS report.
 */
@Composable
private fun copyIdeasAction(
    ideas: SupportIdeas?,
    studentFirstName: String,
    snackbarHostState: SnackbarHostState,
): () -> Unit {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.support_ideas_title)
    val clipText = ideas?.let { ideasAsPlainText(it, studentFirstName) }
    val copiedMessage = stringResource(R.string.support_ideas_copied)
    return {
        if (clipText != null) {
            coroutineScope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, clipText)))
                // From Android 13, the system confirms every copy itself.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbarHostState.showSnackbar(copiedMessage)
            }
        }
    }
}

@Composable
private fun ideasAsPlainText(
    ideas: SupportIdeas,
    studentFirstName: String,
): String {
    val title = stringResource(R.string.support_ideas_title_for_student, studentFirstName)
    val ideaParagraphs =
        ideas.ideas.mapIndexed { index, idea ->
            val reasonLine = if (idea.reason.isEmpty()) "" else "\n" + stringResource(R.string.support_ideas_reason, idea.reason)
            "${index + 1}. ${idea.text}$reasonLine"
        }
    return (listOf("$title\n${basedOnObservationsText(ideas)}") + ideaParagraphs).joinToString(separator = "\n\n")
}

/** Which observations [ideas] were drawn from: how many, and from when to when. */
@Composable
private fun basedOnObservationsText(ideas: SupportIdeas): String {
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalLocale.current.platformLocale)
    val lastDate = ideas.lastObservationDate.format(dateFormatter)
    return if (ideas.observationCount == 1) {
        stringResource(R.string.support_ideas_based_on_one, lastDate)
    } else {
        pluralStringResource(
            R.plurals.support_ideas_based_on,
            ideas.observationCount,
            ideas.observationCount,
            ideas.firstObservationDate.format(dateFormatter),
            lastDate,
        )
    }
}

@Composable
private fun GeneratingIndicator(
    onCancelClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.support_ideas_generating),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancelClicked) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    }
}

/** Says why the last attempt failed; announced by screen readers as soon as it appears. */
@Composable
private fun FailureCard(
    failure: SupportIdeasFailure,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = failureMessage(failure),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun failureMessage(failure: SupportIdeasFailure): String =
    stringResource(
        when (failure) {
            SupportIdeasFailure.AiFeatureUnavailable -> R.string.support_ideas_ai_unavailable
            SupportIdeasFailure.GenerationTimeout -> R.string.support_ideas_generation_timeout
            SupportIdeasFailure.AiBusy -> R.string.support_ideas_ai_busy
            SupportIdeasFailure.BackgroundUseBlocked -> R.string.support_ideas_background_use_blocked
            SupportIdeasFailure.NotEnoughStorage -> R.string.support_ideas_not_enough_storage
            SupportIdeasFailure.GenerationFailed -> R.string.support_ideas_generation_failed
        },
    )

/**
 * The ideas, numbered so they're easy to refer to, under which observations
 * they come from, with a reminder that they're leads to check rather than
 * instructions, and a button copying them.
 */
@Composable
private fun IdeasSection(
    ideas: SupportIdeas,
    studentFirstName: String,
    onCopyClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.support_ideas_section_ideas),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = basedOnObservationsText(ideas),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ideas.ideas.forEachIndexed { index, idea ->
                IdeaRow(number = index + 1, idea = idea)
            }
            Text(
                text = stringResource(R.string.support_ideas_disclaimer, studentFirstName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onCopyClicked, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_content_copy),
                    contentDescription = null,
                    modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
                )
                Text(text = stringResource(R.string.support_ideas_copy))
            }
        }
    }
}

/** One idea after its number, with what led to it underneath; read out by screen readers as one item. */
@Composable
private fun IdeaRow(
    number: Int,
    idea: SupportIdea,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = idea.text, style = MaterialTheme.typography.bodyLarge)
            if (idea.reason.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.support_ideas_reason, idea.reason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val previewStudent = Student(id = 1L, firstName = "Alice", className = "CE2", ppsGoals = "Gain autonomy in written work")

private val previewIdeas =
    SupportIdeas(
        ideas =
            listOf(
                SupportIdea(
                    text = "Keep the visual timer for long exercises, and offer it in maths too.",
                    reason = "It helped Alice refocus after breaks several times.",
                ),
                SupportIdea(
                    text = "Split written instructions into short steps on a checklist she ticks off.",
                    reason = "Starting an exercise alone keeps coming up as a difficulty.",
                ),
                SupportIdea(
                    text = "Plan a short movement break after recess.",
                ),
            ),
        observationCount = 12,
        firstObservationDate = LocalDate.of(2026, 11, 3),
        lastObservationDate = LocalDate.of(2026, 11, 20),
    )

@Preview(showBackground = true)
@Composable
private fun SupportIdeasScreenPreview() {
    AeshAssistantTheme {
        SupportIdeasScreen(
            uiState = SupportIdeasUiState(student = previewStudent, observationCount = 12, isLoading = false),
            onSuggestClicked = {},
            onCancelClicked = {},
            onAddObservation = {},
            onNavigateBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SupportIdeasScreenGeneratingPreview() {
    AeshAssistantTheme {
        SupportIdeasScreen(
            uiState =
                SupportIdeasUiState(
                    student = previewStudent,
                    observationCount = 12,
                    isLoading = false,
                    isGenerating = true,
                ),
            onSuggestClicked = {},
            onCancelClicked = {},
            onAddObservation = {},
            onNavigateBack = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun SupportIdeasScreenWithIdeasPreview() {
    AeshAssistantTheme {
        SupportIdeasScreen(
            uiState =
                SupportIdeasUiState(
                    student = previewStudent,
                    observationCount = 12,
                    isLoading = false,
                    ideas = previewIdeas,
                ),
            onSuggestClicked = {},
            onCancelClicked = {},
            onAddObservation = {},
            onNavigateBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SupportIdeasScreenFailurePreview() {
    AeshAssistantTheme {
        SupportIdeasScreen(
            uiState =
                SupportIdeasUiState(
                    student = previewStudent,
                    observationCount = 12,
                    isLoading = false,
                    failure = SupportIdeasFailure.AiFeatureUnavailable,
                ),
            onSuggestClicked = {},
            onCancelClicked = {},
            onAddObservation = {},
            onNavigateBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SupportIdeasScreenNoObservationsPreview() {
    AeshAssistantTheme {
        SupportIdeasScreen(
            uiState = SupportIdeasUiState(student = previewStudent, observationCount = 0, isLoading = false),
            onSuggestClicked = {},
            onCancelClicked = {},
            onAddObservation = {},
            onNavigateBack = {},
        )
    }
}
