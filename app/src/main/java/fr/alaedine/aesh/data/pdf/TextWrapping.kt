package fr.alaedine.aesh.data.pdf

private val WHITESPACE_REGEX = Regex("""\s+""")

/**
 * Greedily word-wraps one [paragraph] (no line breaks) so every line is at
 * most [maxWidth] wide, as measured by [measureWidth]. Words are split on
 * any whitespace, and a word wider than a whole line (a link, a long
 * identifier...) is broken across lines instead of running off the page.
 */
internal fun wrapParagraph(
    paragraph: String,
    maxWidth: Float,
    measureWidth: (String) -> Float,
): List<String> {
    val words =
        paragraph
            .split(WHITESPACE_REGEX)
            .filter { it.isNotEmpty() }
            .flatMap { breakWord(it, maxWidth, measureWidth) }
    if (words.isEmpty()) return listOf("")

    val lines = mutableListOf<String>()
    var currentLine = ""
    for (word in words) {
        val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (currentLine.isEmpty() || measureWidth(candidate) <= maxWidth) {
            currentLine = candidate
        } else {
            lines += currentLine
            currentLine = word
        }
    }
    lines += currentLine
    return lines
}

private fun breakWord(
    word: String,
    maxWidth: Float,
    measureWidth: (String) -> Float,
): List<String> {
    if (measureWidth(word) <= maxWidth) return listOf(word)
    val pieces = mutableListOf<String>()
    val piece = StringBuilder()
    for (character in word) {
        if (piece.isNotEmpty() && measureWidth("$piece$character") > maxWidth) {
            pieces += piece.toString()
            piece.clear()
        }
        piece.append(character)
    }
    if (piece.isNotEmpty()) pieces += piece.toString()
    return pieces
}
