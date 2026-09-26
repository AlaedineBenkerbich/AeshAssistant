package fr.alaedine.aesh.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import fr.alaedine.aesh.data.local.entity.StudentEntity
import kotlinx.coroutines.flow.Flow

/** Data Access Object for CRUD operations on [StudentEntity] rows. */
@Dao
interface StudentDao {

    @Query("SELECT * FROM students ORDER BY firstName ASC")
    fun observeAll(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getById(id: Long): StudentEntity?

    /** Inserts [student] and returns its generated row id. */
    @Insert
    suspend fun insert(student: StudentEntity): Long

    /** Inserts every one of [students], preserving their ids; used when restoring a backup (see `BackupRepositoryImpl`). */
    @Insert
    suspend fun insertAll(students: List<StudentEntity>)

    @Update
    suspend fun update(student: StudentEntity)

    @Delete
    suspend fun delete(student: StudentEntity)

    /** Removes every row; used when restoring a backup (see `BackupRepositoryImpl`). */
    @Query("DELETE FROM students")
    suspend fun deleteAll()
}
