package fr.alaedine.aesh.data.pdf

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import fr.alaedine.aesh.domain.repository.PdfExportRepository
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PdfExportRepository] backed by Android's built-in
 * `android.graphics.pdf.PdfDocument`: renders [title] then [body] as
 * left-aligned, word-wrapped text on A4-sized pages, automatically starting
 * a new page once the current one fills up.
 */
class AndroidPdfExportRepository : PdfExportRepository {

    override suspend fun exportTextAsPdf(title: String, body: String, destination: OutputStream) {
        withContext(Dispatchers.IO) {
            val document = PdfDocument()
            try {
                renderPages(document, title, body)
                destination.use { document.writeTo(it) }
            } finally {
                document.close()
            }
        }
    }

    private fun renderPages(document: PdfDocument, title: String, body: String) {
        val lines = layOutLines(title, body)

        var lineIndex = 0
        var pageNumber = 1
        do {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_POINTS, PAGE_HEIGHT_POINTS, pageNumber).create()
            val page = document.startPage(pageInfo)
            var y = MARGIN_POINTS

            while (lineIndex < lines.size) {
                val line = lines[lineIndex]
                y += line.lineHeight
                if (y > PAGE_HEIGHT_POINTS - MARGIN_POINTS) break
                if (line.text.isNotEmpty()) page.canvas.drawText(line.text, MARGIN_POINTS, y, line.paint)
                lineIndex++
            }

            document.finishPage(page)
            pageNumber++
        } while (lineIndex < lines.size)
    }

    /**
     * Word-wraps [title] and [body] (each under its own [Paint] style) into
     * a single flat list of lines to draw one after another, with a blank
     * spacer line in between. Laying both out into one list up front —
     * rather than tracking title/body progress separately — lets the
     * pagination loop above stay a single, uniform pass: it never needs to
     * special-case a title so long it doesn't fit on page 1 either.
     */
    private fun layOutLines(title: String, body: String): List<RenderLine> {
        val titlePaint = Paint().apply {
            textSize = TITLE_TEXT_SIZE
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply { textSize = BODY_TEXT_SIZE }
        val maxLineWidth = PAGE_WIDTH_POINTS - 2 * MARGIN_POINTS

        val titleLines = wrapToLines(title, titlePaint, maxLineWidth)
            .map { RenderLine(it, titlePaint, TITLE_LINE_HEIGHT) }
        val bodyLines = wrapToLines(body, bodyPaint, maxLineWidth)
            .map { RenderLine(it, bodyPaint, BODY_LINE_HEIGHT) }
        return titleLines + RenderLine("", bodyPaint, SPACER_HEIGHT) + bodyLines
    }

    /** Greedily word-wraps [text] (existing line breaks become paragraph boundaries) so every line fits [maxWidth] under [paint]'s font metrics. */
    private fun wrapToLines(text: String, paint: Paint, maxWidth: Float): List<String> =
        text.split("\n").flatMap { paragraph -> wrapParagraph(paragraph, paint, maxWidth) }

    private fun wrapParagraph(paragraph: String, paint: Paint, maxWidth: Float): List<String> {
        if (paragraph.isBlank()) return listOf("")

        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        for (word in paragraph.split(" ")) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (currentLine.isEmpty() || paint.measureText(candidate) <= maxWidth) {
                currentLine = StringBuilder(candidate)
            } else {
                lines += currentLine.toString()
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) lines += currentLine.toString()
        return lines
    }

    /** One line of text to draw, in the [paint] style it belongs to, advancing the cursor by [lineHeight] first. */
    private data class RenderLine(val text: String, val paint: Paint, val lineHeight: Float)

    private companion object {
        // A4 in points (72 points/inch), the coordinate system `PdfDocument` draws in.
        const val PAGE_WIDTH_POINTS = 595
        const val PAGE_HEIGHT_POINTS = 842
        const val MARGIN_POINTS = 40f
        const val TITLE_TEXT_SIZE = 18f
        const val TITLE_LINE_HEIGHT = 24f
        const val BODY_TEXT_SIZE = 12f
        const val BODY_LINE_HEIGHT = 16f
        const val SPACER_HEIGHT = 16f
    }
}
