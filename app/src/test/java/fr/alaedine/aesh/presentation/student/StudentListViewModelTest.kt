package fr.alaedine.aesh.presentation.student

import fr.alaedine.aesh.domain.model.Student
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [StudentListViewModel] reads [fr.alaedine.aesh.domain.repository.StudentRepository]
 * through `viewModelScope`, which requires the `Main` dispatcher to be
 * available; [UnconfinedTestDispatcher] makes coroutines launched on it run
 * eagerly so state updates are visible immediately, without needing manual
 * virtual-time advancement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudentListViewModelTest {
    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should expose the repository students when the view model is initialized`() =
        runTest {
            // Given
            val alice = Student(id = 1L, firstName = "Alice", className = "CE2")

            // When
            val viewModel = StudentListViewModel(FakeStudentRepository(initialStudents = listOf(alice)))

            // Then
            assertEquals(listOf(alice), viewModel.uiState.value.students)
        }

    @Test
    fun `should stage the student for deletion when deletion is requested`() =
        runTest {
            // Given
            val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
            val viewModel = StudentListViewModel(FakeStudentRepository(initialStudents = listOf(alice)))

            // When
            viewModel.onDeleteRequested(alice)

            // Then
            assertEquals(alice, viewModel.uiState.value.pendingDeletion)
        }

    @Test
    fun `should clear the pending deletion when deletion is cancelled`() =
        runTest {
            // Given
            val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
            val viewModel = StudentListViewModel(FakeStudentRepository(initialStudents = listOf(alice)))
            viewModel.onDeleteRequested(alice)

            // When
            viewModel.onDeleteCancelled()

            // Then
            assertNull(viewModel.uiState.value.pendingDeletion)
        }

    @Test
    fun `should remove the student and clear the pending deletion when deletion is confirmed`() =
        runTest {
            // Given
            val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
            val viewModel = StudentListViewModel(FakeStudentRepository(initialStudents = listOf(alice)))
            viewModel.onDeleteRequested(alice)

            // When
            viewModel.onDeleteConfirmed()

            // Then
            assertEquals(emptyList(), viewModel.uiState.value.students)
            assertNull(viewModel.uiState.value.pendingDeletion)
        }

    @Test
    fun `should leave state unchanged when deletion is confirmed without a pending student`() =
        runTest {
            // Given
            val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
            val viewModel = StudentListViewModel(FakeStudentRepository(initialStudents = listOf(alice)))

            // When
            viewModel.onDeleteConfirmed()

            // Then
            assertEquals(listOf(alice), viewModel.uiState.value.students)
        }
}
