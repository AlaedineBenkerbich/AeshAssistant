package fr.alaedine.aesh.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for [AeshNavHost].
 *
 * Each destination is a serializable route consumed by Jetpack Navigation
 * Compose's typed `composable<T>` / `navigate<T>` APIs, so arguments (once
 * screens need them, e.g. a student id) are checked at compile time instead
 * of being encoded as raw strings.
 */
sealed interface AeshDestination {
    /** App landing screen (see `HomeRoute`). */
    @Serializable
    data object Dashboard : AeshDestination

    /** Preferences screen: local JSON backup export/restore (see `SettingsRoute`). */
    @Serializable
    data object Settings : AeshDestination

    /** Lists every student; entry point into student management (see `StudentListRoute`). */
    @Serializable
    data object StudentList : AeshDestination

    /** Form to create a new student (see `StudentFormRoute`). */
    @Serializable
    data object AddStudent : AeshDestination

    /** Form to edit the existing student identified by [studentId] (see `StudentFormRoute`). */
    @Serializable
    data class EditStudent(
        val studentId: Long,
    ) : AeshDestination

    /**
     * Daily observation form (see `DailyReportFormRoute`).
     *
     * [studentId] preselects that student on load — used when this
     * destination is reached by tapping a student row on the dashboard (see
     * `HomeScreen`'s `StudentReportStatusRow`). `null` when reached from the
     * dashboard's "new report" FAB instead, in which case the student is
     * picked from within the form.
     *
     * [date] is an ISO-8601 string (e.g. `"2026-09-27"`), keeping this
     * destination serializable with primitive types only (same pattern as
     * [AddScheduleSlot]'s recognized fields). It carries over the
     * dashboard's currently selected date so editing/completing an
     * observation opens the form already on the right day instead of
     * always defaulting to today. `null` when reached from the "new
     * report" FAB, in which case the form defaults to today.
     */
    @Serializable
    data class DailyReportForm(
        val studentId: Long? = null,
        val date: String? = null,
    ) : AeshDestination

    /**
     * ESS (*Équipe de Suivi de Scolarisation*) report generation screen; the
     * student and date range are picked from within the form (see
     * `EssReportRoute`).
     */
    @Serializable
    data object EssReportForm : AeshDestination

    /** Weekly schedule list; entry point into schedule management (see `ScheduleListRoute`). */
    @Serializable
    data object ScheduleList : AeshDestination

    /**
     * Form to create a new schedule slot (see `ScheduleFormRoute`).
     *
     * The optional fields pre-fill the form when reached from
     * [ScheduleScanner]'s photo-recognition flow: [dayOfWeek] is a
     * [java.time.DayOfWeek] enum name (e.g. `"MONDAY"`) and [startTime]/
     * [endTime] are `HH:mm` strings, so this destination stays serializable
     * with primitive types only. Every field is `null` when adding a slot
     * normally, or when that particular field wasn't recognized.
     */
    @Serializable
    data class AddScheduleSlot(
        val dayOfWeek: String? = null,
        val startTime: String? = null,
        val endTime: String? = null,
        val subject: String? = null,
        val room: String? = null,
    ) : AeshDestination

    /** Form to edit the existing schedule slot identified by [scheduleSlotId] (see `ScheduleFormRoute`). */
    @Serializable
    data class EditScheduleSlot(
        val scheduleSlotId: Long,
    ) : AeshDestination

    /**
     * Camera + on-device OCR scanner that pre-fills a new schedule slot
     * from a photo of a physical schedule (see `ScheduleScannerRoute`).
     * On success, navigates to [AddScheduleSlot] with the recognized
     * fields.
     */
    @Serializable
    data object ScheduleScanner : AeshDestination

    /**
     * Camera + on-device OCR scanner that reads photos of handwritten
     * observation notes, to fill in the daily observation form's free-text
     * fields (see `NotesScannerRoute`). Unlike [ScheduleScanner] it doesn't
     * navigate forward on success: it goes back to the [DailyReportForm] it
     * was opened from and hands the recognized text over through that
     * entry's saved state, since the form must keep everything the user
     * already entered.
     */
    @Serializable
    data object NotesScanner : AeshDestination
}
