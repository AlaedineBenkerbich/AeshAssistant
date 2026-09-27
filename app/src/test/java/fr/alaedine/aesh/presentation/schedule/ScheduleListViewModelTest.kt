package fr.alaedine.aesh.presentation.schedule

import fr.alaedine.aesh.domain.model.ScheduleSlot
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.student.FakeStudentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [ScheduleListViewModel] reads
 * [fr.alaedine.aesh.domain.repository.ScheduleSlotRepository] and
 * [fr.alaedine.aesh.domain.repository.StudentRepository] through
 * `viewModelScope`, which requires the `Main` dispatcher to be available;
 * [UnconfinedTestDispatcher] makes coroutines launched on it run eagerly so
 * state updates are visible immediately, without needing manual
 * virtual-time advancement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleListViewModelTest {
    private val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
    private val mathSlot =
        ScheduleSlot(
            id = 1L,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(9, 0),
            subject = "Mathématiques",
            studentIds = listOf(alice.id),
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
    fun `should expose the repository schedule slots when the view model is initialized`() =
        runTest {
            // Given / When
            val viewModel =
                ScheduleListViewModel(
                    FakeScheduleSlotRepository(initialScheduleSlots = listOf(mathSlot)),
                    FakeStudentRepository(initialStudents = listOf(alice)),
                )

            // Then
            assertEquals(listOf(mathSlot), viewModel.uiState.value.scheduleSlots)
        }

    @Test
    fun `should expose students keyed by id to resolve assigned students when the view model is initialized`() =
        runTest {
            // Given / When
            val viewModel =
                ScheduleListViewModel(
                    FakeScheduleSlotRepository(initialScheduleSlots = listOf(mathSlot)),
                    FakeStudentRepository(initialStudents = listOf(alice)),
                )

            // Then
            assertEquals(mapOf(alice.id to alice), viewModel.uiState.value.studentsById)
        }

    @Test
    fun `should stage the schedule slot for deletion when deletion is requested`() =
        runTest {
            // Given
            val viewModel =
                ScheduleListViewModel(
                    FakeScheduleSlotRepository(initialScheduleSlots = listOf(mathSlot)),
                    FakeStudentRepository(initialStudents = listOf(alice)),
                )

            // When
            viewModel.onDeleteRequested(mathSlot)

            // Then
            assertEquals(mathSlot, viewModel.uiState.value.pendingDeletion)
        }

    @Test
    fun `should clear the pending deletion when deletion is cancelled`() =
        runTest {
            // Given
            val viewModel =
                ScheduleListViewModel(
                    FakeScheduleSlotRepository(initialScheduleSlots = listOf(mathSlot)),
                    FakeStudentRepository(initialStudents = listOf(alice)),
                )
            viewModel.onDeleteRequested(mathSlot)

            // When
            viewModel.onDeleteCancelled()

            // Then
            assertNull(viewModel.uiState.value.pendingDeletion)
        }

    @Test
    fun `should remove the schedule slot and clear the pending deletion when deletion is confirmed`() =
        runTest {
            // Given
            val viewModel =
                ScheduleListViewModel(
                    FakeScheduleSlotRepository(initialScheduleSlots = listOf(mathSlot)),
                    FakeStudentRepository(initialStudents = listOf(alice)),
                )
            viewModel.onDeleteRequested(mathSlot)

            // When
            viewModel.onDeleteConfirmed()

            // Then
            assertEquals(emptyList(), viewModel.uiState.value.scheduleSlots)
            assertNull(viewModel.uiState.value.pendingDeletion)
        }

    @Test
    fun `should leave state unchanged when deletion is confirmed without a pending schedule slot`() =
        runTest {
            // Given
            val viewModel =
                ScheduleListViewModel(
                    FakeScheduleSlotRepository(initialScheduleSlots = listOf(mathSlot)),
                    FakeStudentRepository(initialStudents = listOf(alice)),
                )

            // When
            viewModel.onDeleteConfirmed()

            // Then
            assertEquals(listOf(mathSlot), viewModel.uiState.value.scheduleSlots)
        }
}
