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
    data class EditStudent(val studentId: Long) : AeshDestination

    /** Daily observation form; the student is picked from within the form (see `DailyReportFormRoute`). */
    @Serializable
    data object DailyReportForm : AeshDestination

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
    data class EditScheduleSlot(val scheduleSlotId: Long) : AeshDestination

    /**
     * Camera + on-device OCR scanner that pre-fills a new schedule slot
     * from a photo of a physical schedule (see `ScheduleScannerRoute`).
     * On success, navigates to [AddScheduleSlot] with the recognized
     * fields.
     */
    @Serializable
    data object ScheduleScanner : AeshDestination
}
