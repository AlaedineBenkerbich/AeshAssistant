package fr.alaedine.aesh.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme

/** Corner radius of a group's first and last items' outer corners. */
private val GroupOuterCornerRadius = 20.dp

/** Corner radius where two items of the same group meet. */
private val GroupInnerCornerRadius = 4.dp

/** Vertical gap between two items of the same group, through which the screen's background shows. */
val GroupedListItemSpacing = 2.dp

/**
 * The shape of the item at [index] in a group of [count] items: well-rounded
 * outer corners on the group's first and last items, slightly rounded ones
 * where two items meet. Stacked [GroupedListItemSpacing] apart, the items
 * read as one rounded block, the way Android's own settings group them.
 */
fun groupedListItemShape(
    index: Int,
    count: Int,
): Shape {
    val top = if (index == 0) GroupOuterCornerRadius else GroupInnerCornerRadius
    val bottom = if (index == count - 1) GroupOuterCornerRadius else GroupInnerCornerRadius
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * A [ListItem] drawn as the item at [index] of a group of [count] (see
 * [groupedListItemShape]), on the brightest surface color so groups stand
 * out from a `surfaceContainer` screen background in both light and dark
 * themes. Clickable when [onClick] is given, [onClickLabel] then telling
 * screen reader users what a tap does. [overlineContent] goes above the
 * headline, e.g. to label the value the headline shows.
 */
@Composable
fun GroupedListItem(
    index: Int,
    count: Int,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val shape = groupedListItemShape(index, count)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier =
            if (onClick != null) {
                modifier.clip(shape).clickable(onClickLabel = onClickLabel, onClick = onClick)
            } else {
                modifier
            },
    ) {
        ListItem(
            headlineContent = headlineContent,
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3EDF7)
@Composable
private fun GroupedListItemPreview() {
    AeshAssistantTheme(dynamicColor = false) {
        Column(verticalArrangement = Arrangement.spacedBy(GroupedListItemSpacing)) {
            listOf("Alice", "Amir", "Inès").forEachIndexed { index, name ->
                GroupedListItem(
                    index = index,
                    count = 3,
                    onClick = {},
                    headlineContent = { Text(text = name) },
                    supportingContent = { Text(text = "CE2") },
                    leadingContent = { InitialsAvatar(name = name, colorKey = index.toLong()) },
                )
            }
        }
    }
}
