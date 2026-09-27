package fr.alaedine.aesh.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Row of the many-to-many junction table assigning a [StudentEntity] to a
 * [ScheduleSlotEntity]. Deleting either side cascades to remove the
 * corresponding cross-ref rows ([ForeignKey.CASCADE]), so assignments never
 * dangle after a slot or student is deleted.
 *
 * Queried through [fr.alaedine.aesh.data.local.dao.ScheduleSlotDao], joined
 * into [ScheduleSlotWithStudents] for reads.
 */
@Entity(
    tableName = "schedule_slot_student_cross_ref",
    primaryKeys = ["scheduleSlotId", "studentId"],
    foreignKeys = [
        ForeignKey(
            entity = ScheduleSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleSlotId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // Covers the studentId foreign key: the composite primary key above
    // already covers scheduleSlotId as its leading column.
    indices = [Index("studentId")],
)
data class ScheduleSlotStudentCrossRef(
    val scheduleSlotId: Long,
    val studentId: Long,
)
