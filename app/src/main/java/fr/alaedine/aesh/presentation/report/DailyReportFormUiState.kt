package fr.alaedine.aesh.presentation.report

import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.report.notes.DictationError
import java.time.LocalDate

/** Slider position for mood/focus/social interactions before the user picks a value. */
const val NEUTRAL_LEVEL = 3

/**
 * Immutable UI state rendered by [DailyReportFormScreen].
 *
 * @property reportId `null` while no report exists yet for [selectedStudent]
 * on [date], or the id of the existing report being edited. The
 * `(studentId, date)` unique index allows at most one report per student per
 * day (see [fr.alaedine.aesh.domain.repository.DailyReportRepository.getReportByDateAndStudent]),
 * so re-opening the form the same day edits that report instead of
 * attempting a duplicate insert.
 * @property date The day this report covers. Defaults to today but can be
 * changed to any day up to and including today (see
 * [DailyReportFormViewModel.onDateSelected]), so a missed observation can
 * be backfilled for a previous day.
 * @property students Students to pick from, kept in sync with
 * [fr.alaedine.aesh.domain.repository.StudentRepository.observeStudents].
 * @property selectedStudent The student this report is being filled out for,
 * or `null` until the user picks one.
 * @property moodLevel The student's mood, on a 1 (lowest) to 5 (highest) scale.
 * @property focusLevel The student's focus/concentration, on a 1 (lowest) to
 * 5 (highest) scale.
 * @property socialInteractions The quality of the student's social
 * interactions, on a 1 (lowest) to 5 (highest) scale.
 * @property autonomyLevel How independently the student completed today's
 * tasks/activities, on a 1 (needed constant help) to 5 (fully autonomous)
 * scale.
 * @property obstacles Free-text notes on difficulties/obstacles encountered
 * by the student today.
 * @property supportStrategies Free-text notes on support or strategies that
 * helped the student today.
 * @property freeNotes Free-text notes for anything the level scores don't
 * capture.
 * @property isLoading Whether the student list is still being loaded; avoids
 * briefly showing the "no students" message before the first emission
 * arrives.
 * @property isSaved Whether the current fields have just been persisted,
 * signalling [DailyReportFormScreen] to show a success state and
 * [DailyReportFormRoute] to navigate back.
 * @property isSortingNotes Whether dictated or photographed notes are
 * currently being sorted into the free-text fields by the on-device AI
 * model. The form is held back meanwhile (see [canSave]) so the sorted notes
 * can't land in the wrong report or after it was saved.
 * @property statusMessage A one-shot user-facing message — how dictated or
 * photographed notes ended up in the fields, or why dictation failed —
 * `null` once shown.
 */
data class DailyReportFormUiState(
    val reportId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val students: List<Student> = emptyList(),
    val selectedStudent: Student? = null,
    val moodLevel: Int = NEUTRAL_LEVEL,
    val focusLevel: Int = NEUTRAL_LEVEL,
    val socialInteractions: Int = NEUTRAL_LEVEL,
    val autonomyLevel: Int = NEUTRAL_LEVEL,
    val obstacles: String = "",
    val supportStrategies: String = "",
    val freeNotes: String = "",
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    val isSortingNotes: Boolean = false,
    val statusMessage: DailyReportStatusMessage? = null,
) {
    /** Whether this state represents editing an existing report for [date] rather than adding a new one. */
    val isEditing: Boolean get() = reportId != null

    /** Whether a student has been picked and the form can be submitted. */
    val canSave: Boolean get() = selectedStudent != null && !isSortingNotes
}

/**
 * One-shot result of filling the form from dictated or photographed notes.
 *
 * Kept as a semantic type rather than a raw `String` so
 * [DailyReportFormViewModel] stays free of Android resources/`Context`;
 * [DailyReportFormScreen] maps each variant to localized text via
 * `stringResource`.
 */
sealed interface DailyReportStatusMessage {
    /** The notes were sorted into the free-text fields, to be checked by the user before saving. */
    data object NotesFilledIn : DailyReportStatusMessage

    /** The notes were added to the free notes field as they are; [reason] says why they weren't sorted. */
    data class NotesAddedUnsorted(
        val reason: UnsortedNotesReason,
    ) : DailyReportStatusMessage

    /** Dictation couldn't produce any notes, because of [error]. */
    data class DictationFailed(
        val error: DictationError,
    ) : DailyReportStatusMessage
}

/** Why dictated or photographed notes were added to the form without being sorted into its fields. */
enum class UnsortedNotesReason {
    /** The on-device AI model isn't supported on this device. */
    AiUnavailable,

    /** Sorting didn't finish within a reasonable time. */
    Timeout,

    /** Sorting failed for any other reason. */
    Failed,

    /** The user chose to skip sorting. */
    Skipped,
}
