package fr.alaedine.aesh.presentation.report.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import fr.alaedine.aesh.R
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme

/*
 * Stateless building blocks for filling the daily observation form's
 * free-text fields from dictated or photographed notes. The state behind
 * them lives in DictationViewModel, NotesScannerViewModel and
 * DailyReportFormViewModel; DailyReportFormRoute wires them together.
 */

/** The form's two ways to fill its free-text fields without typing: dictating, or photographing handwritten notes. */
@Composable
fun NotesAssistantCard(
    onDictateClicked: () -> Unit,
    onScanNotesClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = stringResource(R.string.notes_assistant_title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.notes_assistant_description), style = MaterialTheme.typography.bodyMedium)
            // Min intrinsic height so both buttons stay the same height when one label wraps (long
            // translations, large font sizes).
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                AssistantActionButton(
                    iconRes = R.drawable.ic_mic,
                    label = stringResource(R.string.notes_assistant_dictate),
                    onClick = onDictateClicked,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                AssistantActionButton(
                    iconRes = R.drawable.ic_camera,
                    label = stringResource(R.string.notes_assistant_scan),
                    onClick = onScanNotesClicked,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun AssistantActionButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        Text(text = label)
    }
}

/**
 * Shown while the microphone is being listened to, with what has been
 * recognized so far. Dismissing it abandons the dictation; [onStopClicked]
 * ends it and keeps what was said.
 */
@Composable
fun DictationDialog(
    partialTranscript: String,
    onStopClicked: () -> Unit,
    onCancelClicked: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelClicked,
        icon = { Icon(painter = painterResource(id = R.drawable.ic_mic), contentDescription = null) },
        title = { Text(text = stringResource(R.string.notes_dictation_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(text = stringResource(R.string.notes_dictation_hint), style = MaterialTheme.typography.bodyMedium)
                if (partialTranscript.isNotBlank()) {
                    Text(text = partialTranscript, style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onStopClicked) {
                Text(text = stringResource(R.string.notes_dictation_stop))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelClicked) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Shown while the on-device AI model sorts the notes into the form's fields.
 * It blocks the form and can't be dismissed, so the notes can't land in
 * another report or after a save; [onSkipClicked] is the way out if the wait
 * is too long.
 */
@Composable
fun NotesSortingDialog(onSkipClicked: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(text = stringResource(R.string.notes_sorting_title)) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(text = stringResource(R.string.notes_sorting_description), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onSkipClicked) {
                Text(text = stringResource(R.string.notes_sorting_skip))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun NotesAssistantCardPreview() {
    AeshAssistantTheme {
        NotesAssistantCard(onDictateClicked = {}, onScanNotesClicked = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun DictationDialogPreview() {
    AeshAssistantTheme {
        DictationDialog(
            partialTranscript = "Il a eu du mal à rester assis pendant la lecture",
            onStopClicked = {},
            onCancelClicked = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NotesSortingDialogPreview() {
    AeshAssistantTheme {
        NotesSortingDialog(onSkipClicked = {})
    }
}
