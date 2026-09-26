package fr.alaedine.aesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room-persisted representation of a student. Kept separate from
 * [fr.alaedine.aesh.domain.model.Student] so persistence annotations never
 * leak into the domain layer (see [toDomain]/[toEntity] for the mapping).
 */
@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val firstName: String,
    val className: String,
    val ppsGoals: String = "",
)
