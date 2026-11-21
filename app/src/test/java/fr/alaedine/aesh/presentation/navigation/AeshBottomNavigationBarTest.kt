package fr.alaedine.aesh.presentation.navigation

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards [AeshBottomNavigationBar]'s labels. Material 3 centres each item's
 * icon and label on their own, so a label that wraps pushes its icon above
 * the others': every label must fit on one line on a narrow phone, even one
 * text size above the default. Larger text sizes may wrap a label but must
 * never cut it off, as Material's accessibility guidance asks (the full
 * label stays visible up to 2x).
 *
 * Every case runs on a 360dp-wide phone, the narrowest width common phones
 * ship with. The slightly larger text size of the fit checks leaves margin
 * for wider OEM fonts and for users who bump their text size up a notch.
 *
 * Robolectric's native graphics mode lays text out with the real Android
 * text stack and AOSP's fonts, so the widths are those of a stock Android
 * (Roboto) device. [Config.application] swaps out
 * [fr.alaedine.aesh.AeshApplication] for the reason given in
 * [fr.alaedine.aesh.data.local.dao.ScheduleSlotDaoTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AeshBottomNavigationBarTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = NARROW_ENGLISH_PHONE, fontScale = SLIGHTLY_LARGER_FONT_SCALE)
    fun `should fit every English label on one line when the phone is narrow and text slightly larger`() {
        // Given
        setBottomNavigationBar()

        // When
        val labels = labelLayouts()

        // Then
        assertEveryLabelFitsOnOneLine(labels)
    }

    @Test
    @Config(qualifiers = NARROW_FRENCH_PHONE, fontScale = SLIGHTLY_LARGER_FONT_SCALE)
    fun `should fit every French label on one line when the phone is narrow and text slightly larger`() {
        // Given
        setBottomNavigationBar()

        // When
        val labels = labelLayouts()

        // Then
        assertEveryLabelFitsOnOneLine(labels)
    }

    @Test
    @Config(qualifiers = NARROW_FRENCH_PHONE, fontScale = LARGEST_FONT_SCALE)
    fun `should show every label in full when text is at its largest size`() {
        // Given
        setBottomNavigationBar()

        // When
        val labels = labelLayouts()

        // Then
        labels.forEach { (label, layout) ->
            assertFalse(layout.didOverflowHeight, "\"$label\" is cut off")
            assertTrue(
                (0 until layout.lineCount).none { line -> layout.isLineEllipsized(line) },
                "\"$label\" is cut off with an ellipsis",
            )
        }
    }

    private fun setBottomNavigationBar() {
        composeTestRule.setContent {
            AeshAssistantTheme(dynamicColor = false) {
                AeshBottomNavigationBar(
                    currentTab = AeshBottomNavTab.Dashboard,
                    onTabSelected = {},
                )
            }
        }
    }

    /** Every text node in the bar (the icons have none), with how it was laid out. */
    private fun labelLayouts(): List<Pair<String, TextLayoutResult>> {
        val labels =
            composeTestRule
                .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .map { node ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                    node.config[SemanticsProperties.Text].joinToString() to layouts.single()
                }
        assertEquals(AeshBottomNavTab.entries.size, labels.size, "expected one label per tab")
        return labels
    }

    private fun assertEveryLabelFitsOnOneLine(labels: List<Pair<String, TextLayoutResult>>) {
        labels.forEach { (label, layout) ->
            val neededWidth = layout.multiParagraph.intrinsics.maxIntrinsicWidth
            val availableWidth = layout.layoutInput.constraints.maxWidth
            assertTrue(
                neededWidth <= availableWidth,
                "\"$label\" needs ${neededWidth}px but its tab only has ${availableWidth}px: $SHORTEN_IT",
            )
            assertEquals(1, layout.lineCount, "\"$label\" wraps onto ${layout.lineCount} lines: $SHORTEN_IT")
        }
    }

    private companion object {
        const val NARROW_ENGLISH_PHONE = "en-w360dp-h800dp-xxhdpi"
        const val NARROW_FRENCH_PHONE = "fr-w360dp-h800dp-xxhdpi"

        /** Android 14+'s first step above the default text size. */
        const val SLIGHTLY_LARGER_FONT_SCALE = 1.15f

        /** The largest text size Android's settings offer. */
        const val LARGEST_FONT_SCALE = 2f

        const val SHORTEN_IT = "shorten it in the nav_label_* strings"
    }
}
