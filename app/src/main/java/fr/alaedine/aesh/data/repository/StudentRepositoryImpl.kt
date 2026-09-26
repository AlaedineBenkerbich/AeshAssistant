package fr.alaedine.aesh.data.repository

import fr.alaedine.aesh.data.local.dao.StudentDao
import fr.alaedine.aesh.data.local.entity.toDomain
import fr.alaedine.aesh.data.local.entity.toEntity
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.repository.StudentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [StudentRepository] backed by Room via [studentDao]. */
class StudentRepositoryImpl(
    private val studentDao: StudentDao,
) : StudentRepository {

    override fun observeStudents(): Flow<List<Student>> =
        studentDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getStudentById(id: Long): Student? =
        studentDao.getById(id)?.toDomain()

    override suspend fun addStudent(student: Student): Long =
        studentDao.insert(student.toEntity())

    override suspend fun updateStudent(student: Student) {
        studentDao.update(student.toEntity())
    }

    override suspend fun deleteStudent(student: Student) {
        studentDao.delete(student.toEntity())
    }
}
