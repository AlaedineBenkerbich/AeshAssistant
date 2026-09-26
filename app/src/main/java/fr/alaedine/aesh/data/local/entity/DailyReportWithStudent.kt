package fr.alaedine.aesh.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A [DailyReportEntity] together with the [StudentEntity] it was filled out
 * for, joined by Room via [studentId][DailyReportEntity.studentId] ->
 * [StudentEntity.id]. Queried through
 * [fr.alaedine.aesh.data.local.dao.DailyReportDao.observeAllWithStudent].
 */
data class DailyReportWithStudent(
    @Embedded
    val report: DailyReportEntity,
    @Relation(
        parentColumn = "studentId",
        entityColumn = "id",
    )
    val student: StudentEntity,
)
