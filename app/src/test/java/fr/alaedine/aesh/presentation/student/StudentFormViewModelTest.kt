package fr.alaedine.aesh.presentation.student

import fr.alaedine.aesh.domain.model.Student
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [StudentFormViewModel] reads/writes [fr.alaedine.aesh.domain.repository.StudentRepository]
 * through `viewModelScope`, which requires the `Main` dispatcher to be
 * available; [UnconfinedTestDispatcher] makes coroutines launched on it run
 * eagerly so state updates are visible immediately, without needing manual
 * virtual-time advancement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudentFormViewModelTest {

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should expose blank fields when adding a new student`() = runTest {
        // Given / When
        val viewModel = StudentFormViewModel(FakeStudentRepository(), studentId = null)

        // Then
        assertEquals(StudentFormUiState(), viewModel.uiState.value)
    }

    @Test
    fun `should load the existing student fields when editing`() = runTest {
        // Given
        val alice = Student(id = 1L, firstName = "Alice", className = "CE2", ppsGoals = "Read aloud daily")
        val repository = FakeStudentRepository(initialStudents = listOf(alice))

        // When
        val viewModel = StudentFormViewModel(repository, studentId = alice.id)

        // Then
        val state = viewModel.uiState.value
        assertEquals(alice.firstName, state.firstName)
        assertEquals(alice.className, state.className)
        assertEquals(alice.ppsGoals, state.ppsGoals)
        assertTrue(state.isEditing)
    }

    @Test
    fun `should not allow saving when the class name is blank`() = runTest {
        // Given
        val viewModel = StudentFormViewModel(FakeStudentRepository(), studentId = null)

        // When
        viewModel.onFirstNameChanged("Alice")

        // Then
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `should allow saving when the required fields are filled in`() = runTest {
        // Given
        val viewModel = StudentFormViewModel(FakeStudentRepository(), studentId = null)

        // When
        viewModel.onFirstNameChanged("Alice")
        viewModel.onClassNameChanged("CE2")

        // Then
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun `should add a new student when saving without a student id`() = runTest {
        // Given
        val repository = FakeStudentRepository()
        val viewModel = StudentFormViewModel(repository, studentId = null)
        viewModel.onFirstNameChanged("Alice")
        viewModel.onClassNameChanged("CE2")

        // When
        viewModel.onSaveClicked()

        // Then
        assertEquals(listOf("Alice"), repository.observeStudents().first().map { it.firstName })
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `should update the existing student when saving with a student id`() = runTest {
        // Given
        val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
        val repository = FakeStudentRepository(initialStudents = listOf(alice))
        val viewModel = StudentFormViewModel(repository, studentId = alice.id)

        // When
        viewModel.onClassNameChanged("CM1")
        viewModel.onSaveClicked()

        // Then
        assertEquals("CM1", repository.getStudentById(alice.id)?.className)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `should not persist anything when saving without the required fields`() = runTest {
        // Given
        val repository = FakeStudentRepository()
        val viewModel = StudentFormViewModel(repository, studentId = null)
        viewModel.onFirstNameChanged("Alice")

        // When
        viewModel.onSaveClicked()

        // Then
        assertEquals(emptyList(), repository.observeStudents().first())
        assertFalse(viewModel.uiState.value.isSaved)
    }
}
