package fr.alaedine.aesh.data.repository

import androidx.room.Room
import fr.alaedine.aesh.data.backup.BackupPayload
import fr.alaedine.aesh.data.local.AeshDatabase
import fr.alaedine.aesh.data.local.dao.DailyReportDao
import fr.alaedine.aesh.data.local.dao.ScheduleSlotDao
import fr.alaedine.aesh.data.local.dao.StudentDao
import fr.alaedine.aesh.data.local.entity.DailyReportEntity
import fr.alaedine.aesh.data.local.entity.ScheduleSlotEntity
import fr.alaedine.aesh.data.local.entity.ScheduleSlotStudentCrossRef
import fr.alaedine.aesh.data.local.entity.StudentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises [BackupRepositoryImpl] against a real (in-memory) Room
 * database, verifying the JSON backup format (see [BackupPayload]) and that
 * restoring never leaves the database partially updated.
 *
 * [Config.application] swaps out the manifest-declared
 * [fr.alaedine.aesh.AeshApplication] (which starts Koin) for a plain
 * [android.app.Application]: these tests build Room directly and don't need
 * the DI graph, and starting Koin repeatedly across test methods would
 * throw [org.koin.core.error.KoinApplicationAlreadyStartedException].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BackupRepositoryImplTest {
    private lateinit var database: AeshDatabase
    private lateinit var studentDao: StudentDao
    private lateinit var dailyReportDao: DailyReportDao
    private lateinit var scheduleSlotDao: ScheduleSlotDao
    private lateinit var repository: BackupRepositoryImpl

    @BeforeTest
    fun createRepository() {
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AeshDatabase::class.java)
                .build()
        studentDao = database.studentDao()
        dailyReportDao = database.dailyReportDao()
        scheduleSlotDao = database.scheduleSlotDao()
        repository = BackupRepositoryImpl(database, studentDao, dailyReportDao, scheduleSlotDao)
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `should export every student, daily report and schedule slot as JSON`() =
        runTest {
            // Given
            val studentId = studentDao.insert(StudentEntity(firstName = "Alice", className = "CE2", ppsGoals = "Read aloud"))
            dailyReportDao.insert(
                DailyReportEntity(
                    studentId = studentId,
                    date = LocalDate.of(2026, 9, 26),
                    moodLevel = 4,
                    focusLevel = 3,
                    socialInteractions = 5,
                    freeNotes = "Great day",
                ),
            )
            val scheduleSlotId =
                scheduleSlotDao.insert(
                    ScheduleSlotEntity(
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(10, 0),
                        subject = "Mathématiques",
                        room = "B12",
                    ),
                )
            scheduleSlotDao.insertStudentCrossRefs(
                listOf(ScheduleSlotStudentCrossRef(scheduleSlotId = scheduleSlotId, studentId = studentId)),
            )

            // When
            val destination = ByteArrayOutputStream()
            repository.exportBackup(destination)

            // Then
            val payload = Json.decodeFromString(BackupPayload.serializer(), destination.toString(Charsets.UTF_8.name()))
            assertEquals(BackupPayload.SCHEMA_VERSION, payload.schemaVersion)
            assertEquals(listOf("Alice"), payload.students.map { it.firstName })
            assertEquals(listOf("Great day"), payload.dailyReports.map { it.freeNotes })
            assertEquals(listOf("Mathématiques"), payload.scheduleSlots.map { it.subject })
            assertEquals(
                listOf(scheduleSlotId to studentId),
                payload.scheduleSlotStudentCrossRefs.map { it.scheduleSlotId to it.studentId },
            )
        }

    @Test
    fun `should restore every student, daily report and schedule slot when importing a previously exported backup`() =
        runTest {
            // Given
            val studentId = studentDao.insert(StudentEntity(firstName = "Alice", className = "CE2", ppsGoals = "Read aloud"))
            dailyReportDao.insert(
                DailyReportEntity(
                    studentId = studentId,
                    date = LocalDate.of(2026, 9, 26),
                    moodLevel = 4,
                    focusLevel = 3,
                    socialInteractions = 5,
                    freeNotes = "Great day",
                ),
            )
            val scheduleSlotId =
                scheduleSlotDao.insert(
                    ScheduleSlotEntity(
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(10, 0),
                        subject = "Mathématiques",
                        room = "B12",
                    ),
                )
            scheduleSlotDao.insertStudentCrossRefs(
                listOf(ScheduleSlotStudentCrossRef(scheduleSlotId = scheduleSlotId, studentId = studentId)),
            )
            val backup = ByteArrayOutputStream()
            repository.exportBackup(backup)

            // When
            repository.importBackup(ByteArrayInputStream(backup.toByteArray()))

            // Then
            val restoredStudent = studentDao.observeAll().first().single()
            assertEquals("Alice", restoredStudent.firstName)
            assertEquals(studentId, restoredStudent.id)
            val restoredReport = dailyReportDao.observeAll().first().single()
            assertEquals("Great day", restoredReport.freeNotes)
            assertEquals(studentId, restoredReport.studentId)
            val restoredSlot = scheduleSlotDao.observeAll().first().single()
            assertEquals("Mathématiques", restoredSlot.subject)
            val restoredCrossRef = scheduleSlotDao.getAllStudentCrossRefs().single()
            assertEquals(scheduleSlotId, restoredCrossRef.scheduleSlotId)
            assertEquals(studentId, restoredCrossRef.studentId)
        }

    @Test
    fun `should replace existing student assignments instead of merging when importing a backup`() =
        runTest {
            // Given: a backup where Bob is assigned to Math...
            val bobId = studentDao.insert(StudentEntity(firstName = "Bob", className = "CM2"))
            val mathSlotId =
                scheduleSlotDao.insert(
                    ScheduleSlotEntity(
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(10, 0),
                        subject = "Mathématiques",
                    ),
                )
            scheduleSlotDao.insertStudentCrossRefs(listOf(ScheduleSlotStudentCrossRef(scheduleSlotId = mathSlotId, studentId = bobId)))
            val backup = ByteArrayOutputStream()
            repository.exportBackup(backup)

            // ...and Alice was assigned to that same slot afterwards
            val aliceId = studentDao.insert(StudentEntity(firstName = "Alice", className = "CE2"))
            scheduleSlotDao.insertStudentCrossRefs(listOf(ScheduleSlotStudentCrossRef(scheduleSlotId = mathSlotId, studentId = aliceId)))

            // When
            repository.importBackup(ByteArrayInputStream(backup.toByteArray()))

            // Then
            assertEquals(listOf(bobId), scheduleSlotDao.getAllStudentCrossRefs().map { it.studentId })
        }

    @Test
    fun `should replace existing data instead of merging when importing a backup`() =
        runTest {
            // Given: a backup containing only Bob...
            studentDao.insert(StudentEntity(firstName = "Bob", className = "CM2"))
            val backup = ByteArrayOutputStream()
            repository.exportBackup(backup)

            // ...and Alice was added to the database afterwards
            studentDao.insert(StudentEntity(firstName = "Alice", className = "CE2"))

            // When
            repository.importBackup(ByteArrayInputStream(backup.toByteArray()))

            // Then
            assertEquals(listOf("Bob"), studentDao.observeAll().first().map { it.firstName })
        }

    @Test
    fun `should throw and leave the database untouched when the backup schema version is unsupported`() =
        runTest {
            // Given
            val studentId = studentDao.insert(StudentEntity(firstName = "Alice", className = "CE2"))
            val futurePayload = BackupPayload(schemaVersion = BackupPayload.SCHEMA_VERSION + 1)
            val source =
                ByteArrayInputStream(
                    Json.encodeToString(BackupPayload.serializer(), futurePayload).toByteArray(),
                )

            // When / Then
            assertFailsWith<IllegalArgumentException> {
                repository.importBackup(source)
            }
            assertEquals(
                studentId,
                studentDao
                    .observeAll()
                    .first()
                    .single()
                    .id,
            )
        }

    @Test
    fun `should throw and leave the database untouched when the backup content is malformed`() =
        runTest {
            // Given
            studentDao.insert(StudentEntity(firstName = "Alice", className = "CE2"))
            val source = ByteArrayInputStream("not valid json".toByteArray())

            // When / Then
            assertTrue(runCatching { repository.importBackup(source) }.isFailure)
            assertEquals(listOf("Alice"), studentDao.observeAll().first().map { it.firstName })
        }
}
