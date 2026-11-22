package fr.alaedine.aesh.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.alaedine.aesh.R
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme

/**
 * Explains what a screen's on-device AI feature does ([text]), under the
 * sparkles that mark the app's on-device AI features.
 */
@Composable
fun OnDeviceAiIntroCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_auto_awesome),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Keeps the screen on for as long as this is composed. Meant for while the
 * on-device AI model is working: Android only lets the app on screen use it,
 * so a screen timeout would stop it.
 */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnDeviceAiIntroCardPreview() {
    AeshAssistantTheme(dynamicColor = false) {
        OnDeviceAiIntroCard(text = "Summarize a student's daily notes into a professional ESS report, entirely on-device.")
    }
}
