package fr.alaedine.aesh.presentation.report

import fr.alaedine.aesh.domain.model.Student
import java.time.LocalDate

/**
 * Immutable UI state rendered by [EssReportScreen].
 *
 * @property students Students to pick from, kept in sync with
 * [fr.alaedine.aesh.domain.repository.StudentRepository.observeStudents].
 * @property selectedStudent The student the report is being generated for,
 * or `null` until the user picks one.
 * @property startDate The (inclusive) start of the date range whose daily
 * reports are fed to the on-device model.
 * @property endDate The (inclusive) end of that date range.
 * @property isGenerating Whether a generation request is currently running
 * (including any first-time on-device model download, which can take a
 * moment).
 * @property generatedText The report text last produced by the on-device
 * model, editable by the user afterwards; `null` before the first
 * successful generation.
 * @property isLoadingStudents Whether the student list is still being
 * loaded; avoids briefly showing the "no students" message before the
 * first emission arrives.
 * @property statusMessage A one-shot user-facing message — a generation
 * failure or a PDF export result — `null` once shown.
 */
data class EssReportUiState(
    val students: List<Student> = emptyList(),
    val selectedStudent: Student? = null,
    val startDate: LocalDate = LocalDate.now().minusMonths(1),
    val endDate: LocalDate = LocalDate.now(),
    val isGenerating: Boolean = false,
    val generatedText: String? = null,
    val isLoadingStudents: Boolean = true,
    val statusMessage: EssReportStatusMessage? = null,
) {
    /** Whether the start/end range is valid and a student is picked, allowing generation to start. */
    val canGenerate: Boolean
        get() = selectedStudent != null && !isDateRangeInvalid && !isGenerating

    /** Whether the range's start is strictly after its end — an invalid selection the user must fix. */
    val isDateRangeInvalid: Boolean get() = startDate.isAfter(endDate)

    /** Suggested Storage Access Framework file name for the PDF export. */
    val suggestedPdfFileName: String
        get() {
            val studentPart = selectedStudent?.firstName?.lowercase() ?: "student"
            return "ess-report-$studentPart-$startDate-to-$endDate.pdf"
        }
}

/**
 * One-shot result of an [EssReportViewModel] generation/export operation.
 *
 * Kept as a semantic type rather than a raw `String` so [EssReportViewModel]
 * stays free of Android resources/`Context`; [EssReportScreen] maps each
 * variant to localized text via `stringResource`.
 */
sealed interface EssReportStatusMessage {
    /** No daily reports exist for [studentFirstName] within the selected date range. */
    data class NoReportsInRange(
        val studentFirstName: String,
    ) : EssReportStatusMessage

    /** The on-device generative model isn't supported on this device. */
    data object AiFeatureUnavailable : EssReportStatusMessage

    /** Generation didn't finish (status check, model download, or inference) within a reasonable time. */
    data object GenerationTimeout : EssReportStatusMessage

    /**
     * Generation failed for an unexpected reason; [reason] is the
     * underlying (untranslated) error message when available, falling back
     * to a generic localized message otherwise.
     */
    data class GenerationFailed(
        val reason: String?,
    ) : EssReportStatusMessage

    data object ExportSuccess : EssReportStatusMessage

    data class ExportFailed(
        val reason: String,
    ) : EssReportStatusMessage

    data object ExportFileOpenFailed : EssReportStatusMessage
}
