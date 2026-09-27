package fr.alaedine.aesh.presentation.home

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.ScheduleSlot
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.report.FakeDailyReportRepository
import fr.alaedine.aesh.presentation.schedule.FakeScheduleSlotRepository
import fr.alaedine.aesh.presentation.student.FakeStudentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [HomeViewModel] reads [fr.alaedine.aesh.domain.repository.StudentRepository],
 * [fr.alaedine.aesh.domain.repository.DailyReportRepository] and
 * [fr.alaedine.aesh.domain.repository.ScheduleSlotRepository] through
 * `viewModelScope`, which requires the `Main` dispatcher to be available;
 * [UnconfinedTestDispatcher] makes coroutines launched on it run eagerly so
 * state updates are visible immediately, without needing manual virtual-time
 * advancement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
    private val amir = Student(id = 2L, firstName = "Amir", className = "CM2")
    private val today = LocalDate.now()

    /** A schedule slot on [dayOfWeek] (today's by default) assigned to [studentIds], for building [today]'s lesson blocks. */
    private fun scheduleSlot(
        id: Long,
        studentIds: List<Long>,
        startTime: LocalTime = LocalTime.of(9, 0),
        endTime: LocalTime = LocalTime.of(10, 0),
        subject: String = "Mathématiques",
        dayOfWeek: DayOfWeek = today.dayOfWeek,
    ) = ScheduleSlot(
        id = id,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        subject = subject,
        studentIds = studentIds,
    )

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should expose todays date when the view model is initialized`() =
        runTest {
            // Given / When
            val viewModel = HomeViewModel(FakeStudentRepository(), FakeDailyReportRepository(), FakeScheduleSlotRepository())

            // Then
            assertEquals(today, viewModel.uiState.value.date)
        }

    @Test
    fun `should expose no lesson blocks when no class is scheduled today`() =
        runTest {
            // Given / When
            val viewModel =
                HomeViewModel(
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice, amir)),
                    dailyReportRepository = FakeDailyReportRepository(),
                    scheduleSlotRepository = FakeScheduleSlotRepository(),
                )

            // Then
            assertTrue(
                viewModel.uiState.value.lessonBlocks
                    .isEmpty(),
            )
        }

    @Test
    fun `should group todays schedule slots into lesson blocks with their assigned students`() =
        runTest {
            // Given
            val mathsSlot = scheduleSlot(id = 1L, studentIds = listOf(alice.id, amir.id))

            // When
            val viewModel =
                HomeViewModel(
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice, amir)),
                    dailyReportRepository = FakeDailyReportRepository(),
                    scheduleSlotRepository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(mathsSlot)),
                )

            // Then
            val lessonBlocks = viewModel.uiState.value.lessonBlocks
            assertEquals(1, lessonBlocks.size)
            assertEquals("Mathématiques", lessonBlocks.first().subject)
            assertEquals(listOf(alice.id, amir.id), lessonBlocks.first().studentStatuses.map { it.student.id })
        }

    @Test
    fun `should order todays lesson blocks by start time regardless of input order`() =
        runTest {
            // Given
            val afternoonSlot =
                scheduleSlot(
                    id = 1L,
                    studentIds = listOf(alice.id),
                    startTime = LocalTime.of(14, 0),
                    endTime = LocalTime.of(15, 0),
                    subject = "EPS",
                )
            val morningSlot = scheduleSlot(id = 2L, studentIds = listOf(alice.id))

            // When
            val viewModel =
                HomeViewModel(
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    dailyReportRepository = FakeDailyReportRepository(),
                    scheduleSlotRepository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(afternoonSlot, morningSlot)),
                )

            // Then
            assertEquals(
                listOf("Mathématiques", "EPS"),
                viewModel.uiState.value.lessonBlocks
                    .map { it.subject },
            )
        }

    @Test
    fun `should exclude schedule slots for other days of the week from todays lesson blocks`() =
        runTest {
            // Given
            val tomorrowsSlot = scheduleSlot(id = 1L, studentIds = listOf(alice.id), dayOfWeek = today.dayOfWeek.plus(1))

            // When
            val viewModel =
                HomeViewModel(
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    dailyReportRepository = FakeDailyReportRepository(),
                    scheduleSlotRepository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(tomorrowsSlot)),
                )

            // Then
            assertTrue(
                viewModel.uiState.value.lessonBlocks
                    .isEmpty(),
            )
        }

    @Test
    fun `should exclude a student with no class scheduled today from every lesson block`() =
        runTest {
            // Given: only Alice is scheduled today; Amir has no class at all.
            val mathsSlot = scheduleSlot(id = 1L, studentIds = listOf(alice.id))

            // When
            val viewModel =
                HomeViewModel(
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice, amir)),
                    dailyReportRepository = FakeDailyReportRepository(),
                    scheduleSlotRepository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(mathsSlot)),
                )

            // Then
            val studentIdsShownToday =
                viewModel.uiState.value.lessonBlocks
                    .flatMap { it.studentStatuses }
                    .map { it.student.id }
            assertEquals(listOf(alice.id), studentIdsShownToday)
        }

    @Test
    fun `should mark every scheduled student as missing todays report when none exist`() =
        runTest {
            // Given
            val mathsSlot = scheduleSlot(id = 1L, studentIds = listOf(alice.id, amir.id))

            // When
            val viewModel =
                HomeViewModel(
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice, amir)),
                    dailyReportRepository = FakeDailyReportRepository(),
                    scheduleSlotRepository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(mathsSlot)),
                )

            // Then
            val state = viewModel.uiState.value
            assertTrue(
                state.lessonBlocks
                    .single()
                    .studentStatuses
                    .all { !it.hasReportToday },
            )
            assertTrue(state.hasMissingReports)
            assertEquals(2, state.missingReportCount)
        }

    @Test
    fun `should mark a student as reported when they already have todays report`() =
        runTest {
            // Given
            val mathsSlot = scheduleSlot(id = 1L, studentIds = listOf(alice.id, amir.id))
            val todaysReport =
                DailyReport(id = 1L, date = today, studentId = alice.id, moodLevel = 4, focusLevel = 4, socialInteractions = 4)

            // When
            val viewModel =
                HomeViewModel(
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice, amir)),
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(todaysReport)),
                    scheduleSlotRepository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(mathsSlot)),
                )

            // Then
            val statuses =
                viewModel.uiState.value.lessonBlocks
                    .single()
                    .studentStatuses
            assertTrue(statuses.first { it.student.id == alice.id }.hasReportToday)
            assertFalse(statuses.first { it.student.id == amir.id }.hasReportToday)
            assertEquals(1, viewModel.uiState.value.missingReportCount)
        }

    @Test
    fun `should list a student in every lesson block they are scheduled for today`() =
        runTest {
            // Given
            val morningSlot = scheduleSlot(id = 1L, studentIds = listOf(alice.id))
            val afternoonSlot =
                scheduleSlot(
                    id = 2L,
                    studentIds = listOf(alice.id),
                    startTime = LocalTime.of(14, 0),
                    endTime = LocalTime.of(15, 0),
                    subject = "EPS",
                )
            val todaysReport =
                DailyReport(id = 1L, date = today, studentId = alice.id, moodLevel = 4, focusLevel = 4, socialInteractions = 4)

            // When
            val viewModel =
                HomeViewModel(
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(todaysReport)),
                    scheduleSlotRepository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(morningSlot, afternoonSlot)),
                )

            // Then
            val state = viewModel.uiState.value
            assertEquals(2, state.lessonBlocks.size)
            assertTrue(state.lessonBlocks.all { block -> block.studentStatuses.single().hasReportToday })
        }

    @Test
    fun `should ignore reports from other days when computing todays status`() =
        runTest {
            // Given
            val mathsSlot = scheduleSlot(id = 1L, studentIds = listOf(alice.id))
            val yesterdaysReport =
                DailyReport(id = 1L, date = today.minusDays(1), studentId = alice.id, moodLevel = 4, focusLevel = 4, socialInteractions = 4)

            // When
            val viewModel =
                HomeViewModel(
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(yesterdaysReport)),
                    scheduleSlotRepository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(mathsSlot)),
                )

            // Then
            assertFalse(
                viewModel.uiState.value.lessonBlocks
                    .single()
                    .studentStatuses
                    .single()
                    .hasReportToday,
            )
        }

    @Test
    fun `should not warn about missing reports when every scheduled student already has one today`() =
        runTest {
            // Given
            val mathsSlot = scheduleSlot(id = 1L, studentIds = listOf(alice.id))
            val todaysReport =
                DailyReport(id = 1L, date = today, studentId = alice.id, moodLevel = 4, focusLevel = 4, socialInteractions = 4)

            // When
            val viewModel =
                HomeViewModel(
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(todaysReport)),
                    scheduleSlotRepository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(mathsSlot)),
                )

            // Then
            assertFalse(viewModel.uiState.value.hasMissingReports)
        }

    @Test
    fun `should not warn about missing reports when no class is scheduled today`() =
        runTest {
            // Given / When
            val viewModel =
                HomeViewModel(
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    dailyReportRepository = FakeDailyReportRepository(),
                    scheduleSlotRepository = FakeScheduleSlotRepository(),
                )

            // Then
            assertFalse(viewModel.uiState.value.hasMissingReports)
        }

    @Test
    fun `should expose the days of week with at least one scheduled class`() =
        runTest {
            // Given
            val mondayMaths =
                ScheduleSlot(
                    id = 1L,
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(10, 0),
                    subject = "Mathématiques",
                )
            val wednesdayPe =
                ScheduleSlot(
                    id = 2L,
                    dayOfWeek = DayOfWeek.WEDNESDAY,
                    startTime = LocalTime.of(14, 0),
                    endTime = LocalTime.of(15, 0),
                    subject = "EPS",
                )

            // When
            val viewModel =
                HomeViewModel(
                    studentRepository = FakeStudentRepository(),
                    dailyReportRepository = FakeDailyReportRepository(),
                    scheduleSlotRepository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(mondayMaths, wednesdayPe)),
                )

            // Then
            assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), viewModel.uiState.value.daysWithScheduledClasses)
        }

    @Test
    fun `should expose no days with scheduled classes when the weekly schedule is empty`() =
        runTest {
            // Given / When
            val viewModel = HomeViewModel(FakeStudentRepository(), FakeDailyReportRepository(), FakeScheduleSlotRepository())

            // Then
            assertTrue(
                viewModel.uiState.value.daysWithScheduledClasses
                    .isEmpty(),
            )
        }
}
