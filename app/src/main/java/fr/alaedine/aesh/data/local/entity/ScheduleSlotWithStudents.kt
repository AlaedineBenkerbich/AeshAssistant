package fr.alaedine.aesh.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * A [ScheduleSlotEntity] together with every [StudentEntity] assigned to
 * it, joined via [ScheduleSlotStudentCrossRef]. Queried through
 * [fr.alaedine.aesh.data.local.dao.ScheduleSlotDao.observeAllWithStudents]/
 * [fr.alaedine.aesh.data.local.dao.ScheduleSlotDao.getByIdWithStudents].
 */
data class ScheduleSlotWithStudents(
    @Embedded
    val scheduleSlot: ScheduleSlotEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy =
            Junction(
                value = ScheduleSlotStudentCrossRef::class,
                parentColumn = "scheduleSlotId",
                entityColumn = "studentId",
            ),
    )
    val students: List<StudentEntity>,
)
