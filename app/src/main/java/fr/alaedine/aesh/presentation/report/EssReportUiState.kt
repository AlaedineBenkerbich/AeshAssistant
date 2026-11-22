package fr.alaedine.aesh.presentation.report

import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.usecase.EssReportProgress
import java.time.LocalDate

/**
 * Immutable UI state rendered by [EssReportScreen].
 *
 * @property students Students to pick from, kept in sync with
 * [fr.alaedine.aesh.domain.repository.StudentRepository.observeStudents].
 * @property selectedStudentId The id of the student the report is being
 * generated for, or `null` until the user picks one. An id rather than a
 * [Student] so [selectedStudent] always reflects the latest edits.
 * @property startDate The (inclusive) start of the date range whose daily
 * reports are fed to the on-device model.
 * @property endDate The (inclusive) end of that date range.
 * @property isGenerating Whether a generation request is currently running
 * (including any first-time on-device model download, which can take a
 * moment).
 * @property generationProgress Which of its requests to the on-device model
 * a running generation is at, `null` until it sends the first one.
 * @property draft The report last produced by the on-device model, editable
 * by the user afterwards; `null` before the first successful generation.
 * Kept while a new one is generated, so a failed regeneration loses nothing.
 * @property isRegenerateConfirmationVisible Whether the user is being asked
 * to confirm replacing a draft they edited with a newly generated one.
 * @property isLoadingStudents Whether the student list is still being
 * loaded; avoids briefly showing the "no students" message before the
 * first emission arrives.
 * @property statusMessage A one-shot user-facing message — a generation
 * failure or a PDF export result — `null` once shown.
 */
data class EssReportUiState(
    val students: List<Student> = emptyList(),
    val selectedStudentId: Long? = null,
    val startDate: LocalDate = LocalDate.now().minusMonths(1),
    val endDate: LocalDate = LocalDate.now(),
    val isGenerating: Boolean = false,
    val generationProgress: EssReportProgress? = null,
    val draft: EssReportDraft? = null,
    val isRegenerateConfirmationVisible: Boolean = false,
    val isLoadingStudents: Boolean = true,
    val statusMessage: EssReportStatusMessage? = null,
) {
    /** The selected student as currently stored, or `null` when none is selected (or it was deleted since). */
    val selectedStudent: Student? get() = students.firstOrNull { it.id == selectedStudentId }

    /** The text of the [draft], if any. */
    val generatedText: String? get() = draft?.text

    /** Whether the start/end range is valid and a student is picked, allowing generation to start. */
    val canGenerate: Boolean
        get() = selectedStudent != null && !isDateRangeInvalid && !isGenerating

    /** Whether the range's start is strictly after its end — an invalid selection the user must fix. */
    val isDateRangeInvalid: Boolean get() = startDate.isAfter(endDate)

    /** Suggested Storage Access Framework file name for the PDF export, named after what the [draft] is about. */
    val suggestedPdfFileName: String
        get() {
            val studentPart = (draft?.studentFirstName ?: selectedStudent?.firstName)?.lowercase() ?: "student"
            return "ess-report-$studentPart-${draft?.startDate ?: startDate}-to-${draft?.endDate ?: endDate}.pdf"
        }
}

/**
 * A generated ESS report, with what it is about: the PDF export is titled
 * after the student and period the text was actually generated for.
 *
 * @property isEdited Whether the user changed the text since it was
 * generated, in which case regenerating it asks for confirmation first.
 * @property isCutOff Whether the on-device model reached the longest answer
 * it may write before finishing the report, so its end may be missing: the
 * user is told to check it.
 */
data class EssReportDraft(
    val text: String,
    val studentFirstName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val isEdited: Boolean = false,
    val isCutOff: Boolean = false,
)

/**
 * One-shot result of an [EssReportViewModel] generation/export operation.
 *
 * Kept as a semantic type rather than a raw `String` so [EssReportViewModel]
 * stays free of Android resources/`Context`; [EssReportScreen] maps each
 * variant to localized text via `stringResource`. Underlying exception
 * messages are never shown: they are in English and meant for developers.
 */
sealed interface EssReportStatusMessage {
    /** No daily reports exist for [studentFirstName] within the selected date range. */
    data class NoReportsInRange(
        val studentFirstName: String,
    ) : EssReportStatusMessage

    /** The on-device generative model can't run on this device, at least for now. */
    data object AiFeatureUnavailable : EssReportStatusMessage

    /** Generation didn't finish (status check, model download, or inference) within a reasonable time. */
    data object GenerationTimeout : EssReportStatusMessage

    /** The notes were too long for the on-device model, even summarized period by period. */
    data object PromptTooLong : EssReportStatusMessage

    /** The on-device model is busy, or this app used up its share of it for now. */
    data object AiBusy : EssReportStatusMessage

    /** Generation stopped because the app left the screen, where Android doesn't let it use on-device AI. */
    data object BackgroundUseBlocked : EssReportStatusMessage

    /** There isn't enough free storage to download the on-device model. */
    data object NotEnoughStorage : EssReportStatusMessage

    /** Generation failed for another, unexpected reason. */
    data object GenerationFailed : EssReportStatusMessage

    data object ExportSuccess : EssReportStatusMessage

    data object ExportFailed : EssReportStatusMessage

    data object ExportFileOpenFailed : EssReportStatusMessage
}
