package fr.alaedine.aesh.data.local.dao

import androidx.room.Room
import fr.alaedine.aesh.data.local.AeshDatabase
import fr.alaedine.aesh.data.local.entity.DailyReportEntity
import fr.alaedine.aesh.data.local.entity.StudentEntity
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
 * Exercises [DailyReportDao] against a real (in-memory) Room database. Run
 * under Robolectric so the Android SQLite framework Room delegates to is
 * available on the local JVM, without needing an emulator/device.
 *
 * [Config.application] swaps out the manifest-declared
 * [fr.alaedine.aesh.AeshApplication] (which starts Koin) for a plain
 * [android.app.Application]: these tests build Room directly and don't need
 * the DI graph, and starting Koin repeatedly across test methods would
 * throw [org.koin.core.error.KoinApplicationAlreadyStartedException].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class DailyReportDaoTest {

    private lateinit var database: AeshDatabase
    private lateinit var dailyReportDao: DailyReportDao
    private lateinit var studentDao: StudentDao
    private var studentId: Long = 0L

    @BeforeTest
    fun createDatabase() = runTest {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AeshDatabase::class.java)
            .build()
        dailyReportDao = database.dailyReportDao()
        studentDao = database.studentDao()
        studentId = studentDao.insert(StudentEntity(firstName = "Alice", className = "CE2"))
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `should return the generated id when a report is inserted`() = runTest {
        // Given
        val report = DailyReportEntity(
            studentId = studentId,
            date = LocalDate.of(2026, 9, 26),
            moodLevel = 4,
            focusLevel = 3,
            socialInteractions = 5,
            freeNotes = "Great day",
        )

        // When
        val id = dailyReportDao.insert(report)

        // Then
        assertEquals(report.copy(id = id), dailyReportDao.getById(id))
    }

    @Test
    fun `should return null when no report exists for the given id`() = runTest {
        // Given / When
        val result = dailyReportDao.getById(id = 42L)

        // Then
        assertNull(result)
    }

    @Test
    fun `should return the report when queried by date and studentId`() = runTest {
        // Given
        val date = LocalDate.of(2026, 9, 26)
        val id = dailyReportDao.insert(
            DailyReportEntity(
                studentId = studentId,
                date = date,
                moodLevel = 2,
                focusLevel = 3,
                socialInteractions = 1,
            ),
        )

        // When
        val result = dailyReportDao.getByDateAndStudent(date = date, studentId = studentId)

        // Then
        assertEquals(id, result?.id)
    }

    @Test
    fun `should return null when no report exists for the given date and studentId`() = runTest {
        // Given / When
        val result = dailyReportDao.getByDateAndStudent(date = LocalDate.of(2026, 9, 26), studentId = studentId)

        // Then
        assertNull(result)
    }

    @Test
    fun `should emit reports ordered by date descending when observing all`() = runTest {
        // Given
        dailyReportDao.insert(
            DailyReportEntity(
                studentId = studentId,
                date = LocalDate.of(2026, 9, 20),
                moodLevel = 3,
                focusLevel = 3,
                socialInteractions = 3,
            ),
        )
        dailyReportDao.insert(
            DailyReportEntity(
                studentId = studentId,
                date = LocalDate.of(2026, 9, 25),
                moodLevel = 4,
                focusLevel = 4,
                socialInteractions = 4,
            ),
        )

        // When
        val reports = dailyReportDao.observeAll().first()

        // Then
        assertEquals(listOf(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 20)), reports.map { it.date })
    }

    @Test
    fun `should persist changes when an existing report is updated`() = runTest {
        // Given
        val id = dailyReportDao.insert(
            DailyReportEntity(
                studentId = studentId,
                date = LocalDate.of(2026, 9, 26),
                moodLevel = 2,
                focusLevel = 2,
                socialInteractions = 2,
                freeNotes = "Initial notes",
            ),
        )
        val updated = DailyReportEntity(
            id = id,
            studentId = studentId,
            date = LocalDate.of(2026, 9, 26),
            moodLevel = 5,
            focusLevel = 5,
            socialInteractions = 5,
            freeNotes = "Updated notes",
        )

        // When
        dailyReportDao.update(updated)

        // Then
        assertEquals(updated, dailyReportDao.getById(id))
    }

    @Test
    fun `should remove the report when deleted`() = runTest {
        // Given
        val id = dailyReportDao.insert(
            DailyReportEntity(
                studentId = studentId,
                date = LocalDate.of(2026, 9, 26),
                moodLevel = 3,
                focusLevel = 3,
                socialInteractions = 3,
            ),
        )
        val inserted = dailyReportDao.getById(id)!!

        // When
        dailyReportDao.delete(inserted)

        // Then
        assertNull(dailyReportDao.getById(id))
    }

    @Test
    fun `should emit the associated student when observing all with student`() = runTest {
        // Given
        dailyReportDao.insert(
            DailyReportEntity(
                studentId = studentId,
                date = LocalDate.of(2026, 9, 26),
                moodLevel = 3,
                focusLevel = 3,
                socialInteractions = 3,
            ),
        )

        // When
        val reportsWithStudent = dailyReportDao.observeAllWithStudent().first()

        // Then
        assertEquals("Alice", reportsWithStudent.single().student.firstName)
    }

    @Test
    fun `should remove the report when its student is deleted`() = runTest {
        // Given
        val id = dailyReportDao.insert(
            DailyReportEntity(
                studentId = studentId,
                date = LocalDate.of(2026, 9, 26),
                moodLevel = 3,
                focusLevel = 3,
                socialInteractions = 3,
            ),
        )
        val student = studentDao.getById(studentId)!!

        // When
        studentDao.delete(student)

        // Then
        assertNull(dailyReportDao.getById(id))
    }
}
