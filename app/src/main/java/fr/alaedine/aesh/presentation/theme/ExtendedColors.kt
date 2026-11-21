package fr.alaedine.aesh.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.MaterialColors

/**
 * A color for content to stand out with, the color of what is drawn on it,
 * and a lower-emphasis container variant with its own content color: the
 * same four roles Material 3 gives its primary, secondary and tertiary
 * colors.
 */
@Immutable
data class AccentColorRoles(
    val color: Color,
    val onColor: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * Colors Material 3's color scheme has no role for. Each one is harmonized
 * with the scheme's primary color, static or wallpaper-based, so it keeps
 * its meaning (green is still green) while fitting whatever palette the app
 * runs with.
 *
 * @property success Something done, e.g. a filled-in observation.
 * @property pending Something still to do. Deliberately calmer than the
 * scheme's error color: a missing observation is a to-do, not a failure.
 * @property avatars Rotating palette for initials avatars, so students are
 * told apart at a glance.
 */
@Immutable
data class ExtendedColors(
    val success: AccentColorRoles,
    val pending: AccentColorRoles,
    val avatars: List<AccentColorRoles>,
)

private val SuccessSeed = Color(0xFF2E7D32)
private val PendingSeed = Color(0xFFF9A825)
private val AvatarSeeds =
    listOf(
        Color(0xFF1E88E5),
        Color(0xFF00897B),
        Color(0xFFF4511E),
        Color(0xFFD81B60),
        Color(0xFF8E24AA),
        Color(0xFF7CB342),
    )

/** Derives the [ExtendedColors] that go with a color scheme whose primary color is [primary]. */
fun extendedColors(
    primary: Color,
    isDark: Boolean,
): ExtendedColors {
    fun harmonizedRoles(seed: Color): AccentColorRoles {
        val harmonized = MaterialColors.harmonize(seed.toArgb(), primary.toArgb())
        val roles = MaterialColors.getColorRoles(harmonized, !isDark)
        return AccentColorRoles(
            color = Color(roles.accent),
            onColor = Color(roles.onAccent),
            container = Color(roles.accentContainer),
            onContainer = Color(roles.onAccentContainer),
        )
    }
    return ExtendedColors(
        success = harmonizedRoles(SuccessSeed),
        pending = harmonizedRoles(PendingSeed),
        avatars = AvatarSeeds.map(::harmonizedRoles),
    )
}

/** Provided by [AeshAssistantTheme]; the fallback only serves composables rendered outside of it. */
val LocalExtendedColors = staticCompositionLocalOf { extendedColors(primary = Purple40, isDark = false) }

/** The app's theme values that [MaterialTheme] doesn't carry, read the same way as `MaterialTheme.colorScheme`. */
object AeshTheme {
    val extendedColors: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}
