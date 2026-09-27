# AESH Assistant — Contributor & AI Agent Skill Guide

This document captures the conventions that contributors — human or AI —
must follow when working in this repository.

## Architecture — Clean Architecture

The codebase follows **Clean Architecture**, with dependencies always
pointing inward:

- `presentation` — Compose screens, `ViewModel`s and immutable UI state.
  Contains no business logic, only rendering and user input handling.
- `domain` — use cases and business models (framework-agnostic Kotlin, no
  Android/framework imports).
- `data` — repositories and data sources (Room, DataStore, on-device ML)
  implementing interfaces defined in `domain`.

Outer layers may depend on inner layers, never the other way around: `data`
and `presentation` depend on `domain`, but `domain` never depends on `data`
or `presentation`.

## Code style — Clean Code

- Favor small, single-responsibility functions and classes.
- Use descriptive, intention-revealing names for classes, functions and
  variables; avoid abbreviations and vague names.
- Keep functions short and focused; extract helpers instead of nesting or
  duplicating logic.
- Keep `domain` free of side effects and framework code; push I/O and
  Android-specific concerns to `data`/`presentation`.
- Prefer immutability (`val`, immutable data classes, unidirectional state
  flow) over mutable state.
- Write self-documenting code. Only add comments to explain the *why* behind
  a non-obvious decision, not to restate *what* the code does.

## Unit testing conventions

1. **Imports** — use `kotlin.test` (e.g. `kotlin.test.Test`,
   `kotlin.test.assertEquals`), not `org.junit.Test` / `org.junit.Assert.*`.
2. **Naming** — name test functions `` `should xxx when xxx` `` (backtick
   function names), describing the expected behavior and the condition that
   triggers it.
3. **Structure** — mark each phase of the test body with `// Given`,
   `// When`, `// Then` comments for setup, action, and assertion.

### Before / after example

Before:

```kotlin
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {

    @Test
    fun `initial state exposes the default welcome content`() {
        val viewModel = HomeViewModel()

        val state = viewModel.uiState.value

        assertEquals(HomeUiState(), state)
    }
}
```

After:

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeViewModelTest {

    @Test
    fun `should expose default welcome content when view model is initialized`() {
        // Given
        val viewModel = HomeViewModel()

        // When
        val state = viewModel.uiState.value

        // Then
        assertEquals(HomeUiState(), state)
    }
}
```

Before opening a PR, run the unit tests locally with `./gradlew test` (or
`./gradlew testDebugUnitTest`, as run in CI) to make sure they still pass.

## Pull requests

- **PRs must be squash-merged** (not "Create a merge commit", not "Rebase
  and merge") so `main` keeps a linear, single-commit-per-change history.
- **Auto-merge must be enabled on every PR when it is opened** (e.g. via
  `gh pr merge --squash --auto`), so it merges on its own as soon as CI
  passes and required reviews are satisfied, without needing a manual
  follow-up step.
