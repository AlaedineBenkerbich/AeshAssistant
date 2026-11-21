package fr.alaedine.aesh.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import fr.alaedine.aesh.presentation.theme.AeshTheme
import java.util.Locale

/**
 * A round avatar showing the first letter of [name], tinted with one of
 * [fr.alaedine.aesh.presentation.theme.ExtendedColors.avatars] picked from
 * [colorKey] (e.g. a student's id), so the same person keeps the same color
 * on every screen. Purely decorative: the name it stands for is always
 * written next to it, so it carries no semantics of its own.
 */
@Composable
fun InitialsAvatar(
    name: String,
    colorKey: Long,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val palette = AeshTheme.extendedColors.avatars
    val colors = palette[avatarPaletteIndex(colorKey, palette.size)]
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialOf(name, LocalLocale.current.platformLocale),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onContainer,
        )
    }
}

/** The first character of [name], upper-cased (a whole code point, so an accented or emoji initial isn't cut in half); empty when [name] is blank. */
internal fun initialOf(
    name: String,
    locale: Locale,
): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase(locale)
}

/** Which of a palette of [paletteSize] colors [colorKey] gets: consecutive keys get consecutive colors, so a handful of students all look different. */
internal fun avatarPaletteIndex(
    colorKey: Long,
    paletteSize: Int,
): Int = Math.floorMod(colorKey, paletteSize.toLong()).toInt()

@Preview(showBackground = true)
@Composable
private fun InitialsAvatarPreview() {
    AeshAssistantTheme {
        InitialsAvatar(name = "Inès", colorKey = 3L)
    }
}
