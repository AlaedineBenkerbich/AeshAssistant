package fr.alaedine.aesh.domain.repository

import java.io.OutputStream

/**
 * Framework-agnostic contract for rendering plain text as a paginated PDF
 * document, implemented by
 * [fr.alaedine.aesh.data.pdf.AndroidPdfExportRepository] on top of
 * Android's built-in `android.graphics.pdf.PdfDocument` — no new dependency
 * needed, and (like every other feature in this app) it never touches the
 * network.
 *
 * Deliberately expressed over a plain [OutputStream] rather than
 * `android.net.Uri`, mirroring [BackupRepository]: the Storage Access
 * Framework picker that lets the user choose *where* to save the file is a
 * presentation-layer concern (see
 * `fr.alaedine.aesh.presentation.report.EssReportRoute`, which resolves the
 * user-picked file into a stream via `ContentResolver` and hands it here).
 */
interface PdfExportRepository {
    /** Renders [title] followed by [body] as a paginated PDF, writing the result into [destination]. */
    suspend fun exportTextAsPdf(
        title: String,
        body: String,
        destination: OutputStream,
    )
}
