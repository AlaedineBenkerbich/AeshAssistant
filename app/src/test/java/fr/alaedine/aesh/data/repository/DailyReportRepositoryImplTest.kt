package fr.alaedine.aesh.data.repository

import androidx.room.Room
import fr.alaedine.aesh.data.local.AeshDatabase
import fr.alaedine.aesh.data.local.dao.DailyReportDao
import fr.alaedine.aesh.data.local.dao.StudentDao
import fr.alaedine.aesh.data.local.entity.StudentEntity
import fr.alaedine.aesh.domain.model.DailyReport
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises [DailyReportRepositoryImpl] end-to-end against a real
 * (in-memory) Room database, verifying the `domain`-facing CRUD contract —
 * including the [DailyReport] <->
 * [fr.alaedine.aesh.data.local.entity.DailyReportEntity] mapping — that the
 * rest of the app depends on.
 *
 * [Config.application] swaps out the manifest-declared
 * [fr.alaedine.aesh.AeshApplication] (which starts Koin) for a plain
 * [android.app.Application]: these tests build Room directly and don't need
 * the DI graph, and starting Koin repeatedly across test methods would
 * throw [org.koin.core.error.KoinApplicationAlreadyStartedException].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class DailyReportRepositoryImplTest {

    private lateinit var database: AeshDatabase
    private lateinit var dailyReportDao: DailyReportDao
    private lateinit var studentDao: StudentDao
    private lateinit var repository: DailyReportRepositoryImpl
    private var studentId: Long = 0L

    @BeforeTest
    fun createRepository() = runTest {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AeshDatabase::class.java)
            .build()
        dailyReportDao = database.dailyReportDao()
        studentDao = database.studentDao()
        repository = DailyReportRepositoryImpl(dailyReportDao)
        studentId = studentDao.insert(StudentEntity(firstName = "Alice", className = "CE2"))
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `should return the generated id when a report is added`() = runTest {
        // Given
        val report = DailyReport(
            date = LocalDate.of(2026, 9, 26),
            studentId = studentId,
            moodLevel = 4,
            focusLevel = 3,
            socialInteractions = 5,
            freeNotes = "Great day",
        )

        // When
        val id = repository.addReport(report)

        // Then
        assertEquals(report.copy(id = id), repository.getReportById(id))
    }

    @Test
    fun `should return null when no report exists for the given id`() = runTest {
        // Given / When
        val result = repository.getReportById(id = 42L)

        // Then
        assertNull(result)
    }

    @Test
    fun `should return the report when queried by date and studentId`() = runTest {
        // Given
        val date = LocalDate.of(2026, 9, 26)
        val report = DailyReport(
            date = date,
            studentId = studentId,
            moodLevel = 2,
            focusLevel = 3,
            socialInteractions = 1,
        )
        val id = repository.addReport(report)

        // When
        val result = repository.getReportByDateAndStudent(date = date, studentId = studentId)

        // Then
        assertEquals(report.copy(id = id), result)
    }

    @Test
    fun `should emit reports ordered by date descending when observing reports`() = runTest {
        // Given
        repository.addReport(
            DailyReport(date = LocalDate.of(2026, 9, 20), studentId = studentId, moodLevel = 3, focusLevel = 3, socialInteractions = 3),
        )
        repository.addReport(
            DailyReport(date = LocalDate.of(2026, 9, 25), studentId = studentId, moodLevel = 4, focusLevel = 4, socialInteractions = 4),
        )

        // When
        val reports = repository.observeReports().first()

        // Then
        assertEquals(listOf(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 20)), reports.map { it.date })
    }

    @Test
    fun `should emit only that student's reports when observing reports for a student`() = runTest {
        // Given
        val otherStudentId = studentDao.insert(StudentEntity(firstName = "Bo", className = "CM1"))
        repository.addReport(
            DailyReport(date = LocalDate.of(2026, 9, 26), studentId = studentId, moodLevel = 3, focusLevel = 3, socialInteractions = 3),
        )
        repository.addReport(
            DailyReport(date = LocalDate.of(2026, 9, 26), studentId = otherStudentId, moodLevel = 2, focusLevel = 2, socialInteractions = 2),
        )

        // When
        val reports = repository.observeReportsForStudent(studentId).first()

        // Then
        assertEquals(listOf(studentId), reports.map { it.studentId })
    }

    @Test
    fun `should persist changes when an existing report is updated`() = runTest {
        // Given
        val id = repository.addReport(
            DailyReport(
                date = LocalDate.of(2026, 9, 26),
                studentId = studentId,
                moodLevel = 2,
                focusLevel = 2,
                socialInteractions = 2,
                freeNotes = "Initial notes",
            ),
        )
        val updated = DailyReport(
            id = id,
            date = LocalDate.of(2026, 9, 26),
            studentId = studentId,
            moodLevel = 5,
            focusLevel = 5,
            socialInteractions = 5,
            freeNotes = "Updated notes",
        )

        // When
        repository.updateReport(updated)

        // Then
        assertEquals(updated, repository.getReportById(id))
    }

    @Test
    fun `should remove the report when deleted`() = runTest {
        // Given
        val id = repository.addReport(
            DailyReport(date = LocalDate.of(2026, 9, 26), studentId = studentId, moodLevel = 3, focusLevel = 3, socialInteractions = 3),
        )
        val report = repository.getReportById(id)!!

        // When
        repository.deleteReport(report)

        // Then
        assertNull(repository.getReportById(id))
    }
}
