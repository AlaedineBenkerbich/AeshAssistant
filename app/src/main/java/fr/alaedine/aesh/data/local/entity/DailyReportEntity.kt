package fr.alaedine.aesh.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Room-persisted representation of a daily observation report. Kept
 * separate from [fr.alaedine.aesh.domain.model.DailyReport] so persistence
 * annotations never leak into the domain layer (see [toDomain]/[toEntity]
 * for the mapping).
 *
 * [studentId] references [StudentEntity.id]; reports are deleted along with
 * their student ([ForeignKey.CASCADE]). The `(studentId, date)` unique index
 * enforces at most one report per student per day, matching how
 * [fr.alaedine.aesh.data.local.dao.DailyReportDao.getByDateAndStudent] is used.
 */
@Entity(
    tableName = "daily_reports",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["studentId", "date"], unique = true),
    ],
)
data class DailyReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val studentId: Long,
    val date: LocalDate,
    val moodLevel: Int,
    val focusLevel: Int,
    val socialInteractions: Int,
    val autonomyLevel: Int = 3,
    val obstacles: String = "",
    val supportStrategies: String = "",
    val freeNotes: String = "",
)
