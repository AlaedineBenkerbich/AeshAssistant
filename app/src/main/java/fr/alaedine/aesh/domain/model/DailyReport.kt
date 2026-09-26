package fr.alaedine.aesh.domain.model

import java.time.LocalDate

/**
 * A single day's observation log for a student, framework-agnostic so it
 * can be used throughout `domain` and `presentation` without leaking
 * persistence details (see
 * [fr.alaedine.aesh.data.local.entity.DailyReportEntity] for the Room-mapped
 * counterpart).
 *
 * @property id Unique identifier; `0` for a report not yet persisted.
 * @property date The day this report covers.
 * @property studentId The [Student.id] this report was filled out for.
 * @property moodLevel The student's mood, on a 1 (lowest) to 5 (highest) scale.
 * @property focusLevel The student's focus/concentration, on a 1 (lowest) to
 * 5 (highest) scale.
 * @property socialInteractions The quality of the student's social
 * interactions, on a 1 (lowest) to 5 (highest) scale.
 * @property freeNotes Free-text notes for anything the level scores don't
 * capture.
 */
data class DailyReport(
    val id: Long = 0L,
    val date: LocalDate,
    val studentId: Long,
    val moodLevel: Int,
    val focusLevel: Int,
    val socialInteractions: Int,
    val freeNotes: String = "",
)
