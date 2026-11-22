package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.SupportIdea

/**
 * Reads the ideas out of the on-device model's answer to a
 * [SupportIdeasPrompts] prompt. The model is asked for one
 * "- idea Why: reason" line per idea, but a small model takes liberties, so
 * this also copes with numbered lists, Markdown, a sentence introducing or
 * concluding the list, a reason on a line of its own, and no list at all.
 */
internal object SupportIdeasResponseParser {
    /** More than the 3 to 5 ideas asked for, but a model that keeps going shouldn't fill the screen. */
    private const val MAX_IDEAS = 6

    // "- ", "• ", "1. ", "2) "…; a space after the marker keeps "-5 minutes" or "1.5 h" from passing for an item.
    private val LIST_ITEM_REGEX = Regex("""^(?:[-–—•*+]|\d{1,2}[.)])\s+(.+)$""")

    // "Pourquoi :", "Why:", also as "(Pourquoi : …)" or after a dash. The colon tells it apart from the word in a sentence.
    private val REASON_MARKER_REGEX = Regex("""[(\[]?\s*\b(?:pourquoi|why)\s*[:：]\s*""", RegexOption.IGNORE_CASE)
    private val REASON_LINE_REGEX = Regex("""^[(\[]?\s*(?:pourquoi|why)\s*[:：]""", RegexOption.IGNORE_CASE)

    // A label the model may put before the idea itself: "Piste 2 :", "Idée :", "Idea:".
    private val IDEA_LABEL_REGEX = Regex("""^(?:piste|idée|idee|idea|action)\s*\d*\s*[:：]\s*""", RegexOption.IGNORE_CASE)

    private const val IDEA_TRAILING_CHARACTERS = " -–—(,;:"

    /** The ideas in [response], in order and without duplicates; empty only when it holds no text at all. */
    fun parse(response: String): List<SupportIdea> {
        val lines = response.withoutMarkdown().lines().map { it.trim() }
        val items = if (lines.any { LIST_ITEM_REGEX.matches(it) }) listItems(lines) else plainLines(lines)
        return items
            .mapNotNull { it.toSupportIdea() }
            .distinctBy { it.text.lowercase() }
            .take(MAX_IDEAS)
    }

    /**
     * Each item of the list, with the lines that carry on from it up to the next item or a blank line: an
     * introduction or a conclusion around the list is left out. A reason on its own line, even as an item of its
     * own, goes with the item before it.
     */
    private fun listItems(lines: List<String>): List<String> {
        val items = mutableListOf<StringBuilder>()
        var isInItem = false
        for (line in lines) {
            val itemText = LIST_ITEM_REGEX.matchEntire(line)?.groupValues?.get(1)
            when {
                line.isEmpty() -> isInItem = false
                REASON_LINE_REGEX.containsMatchIn(itemText ?: line) && items.isNotEmpty() ->
                    items.last().append(' ').append(itemText ?: line)
                itemText != null -> {
                    items += StringBuilder(itemText)
                    isInItem = true
                }
                isInItem -> items.last().append(' ').append(line)
            }
        }
        return items.map { it.toString() }
    }

    /** Without a list, each line is an idea, except an introduction ending with a colon ("Voici quelques pistes :"). */
    private fun plainLines(lines: List<String>): List<String> {
        val items = mutableListOf<StringBuilder>()
        for (line in lines.filter { it.isNotEmpty() && !it.endsWith(':') }) {
            if (REASON_LINE_REGEX.containsMatchIn(line) && items.isNotEmpty()) {
                items.last().append(' ').append(line)
            } else {
                items += StringBuilder(line)
            }
        }
        return items.map { it.toString() }
    }

    private fun String.toSupportIdea(): SupportIdea? {
        val content = replaceFirst(IDEA_LABEL_REGEX, "")
        val reasonMarker = REASON_MARKER_REGEX.find(content)
        val text = content.substring(0, reasonMarker?.range?.first ?: content.length).trim().trimEnd { it in IDEA_TRAILING_CHARACTERS }
        if (text.isEmpty()) return null
        val reason =
            reasonMarker
                ?.let { marker -> content.substring(marker.range.last + 1).withoutClosingBracketOf(marker.value) }
                .orEmpty()
        return SupportIdea(text = text, reason = reason)
    }

    /** "(Pourquoi : elle s'est vite découragée)." leaves the reason with the bracket closing the [marker]'s one. */
    private fun String.withoutClosingBracketOf(marker: String): String {
        val openingBracket = marker.trimStart().firstOrNull()
        val closingBracket =
            when (openingBracket) {
                '(' -> ')'
                '[' -> ']'
                else -> return trim()
            }
        val hasUnmatchedClosingBracket = count { it == closingBracket } > count { it == openingBracket }
        if (!hasUnmatchedClosingBracket) return trim()
        val closingIndex = lastIndexOf(closingBracket)
        return removeRange(closingIndex, closingIndex + 1).trim()
    }
}
