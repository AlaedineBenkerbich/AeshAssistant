package fr.alaedine.aesh.presentation.report

import fr.alaedine.aesh.domain.repository.PdfExportRepository
import java.io.OutputStream

/**
 * In-memory [PdfExportRepository] test double, avoiding the need for a
 * mocking library or Android's `PdfDocument` to unit test [EssReportViewModel].
 *
 * @property exportError When non-null, thrown by [exportTextAsPdf] instead
 * of writing anything, to simulate a rendering/I/O failure.
 */
class FakePdfExportRepository(
    private val exportError: Throwable? = null,
) : PdfExportRepository {
    var exportedTitle: String? = null
        private set
    var exportedBody: String? = null
        private set
    var exportedTo: OutputStream? = null
        private set

    override suspend fun exportTextAsPdf(
        title: String,
        body: String,
        destination: OutputStream,
    ) {
        exportError?.let { throw it }
        exportedTitle = title
        exportedBody = body
        exportedTo = destination
    }
}
