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
 * @property autonomyLevel How independently the student completed today's
 * tasks/activities, on a 1 (needed constant help) to 5 (fully autonomous)
 * scale — inspired by the A-D autonomy rating used on the official ESS
 * ("Équipe de Suivi de Scolarisation") preparation form. Defaults to the
 * neutral middle value (3) so reports created before this field existed
 * remain valid.
 * @property obstacles Free-text description of difficulties/obstacles
 * encountered by the student today, mirroring "Cadre 1" of the ESS
 * preparation form; most useful when [autonomyLevel] is low.
 * @property supportStrategies Free-text description of support or
 * strategies that helped the student today, mirroring "Cadre 2" of the ESS
 * preparation form.
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
    val autonomyLevel: Int = 3,
    val obstacles: String = "",
    val supportStrategies: String = "",
    val freeNotes: String = "",
)
