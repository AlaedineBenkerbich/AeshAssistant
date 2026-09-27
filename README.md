# AESH Assistant

Offline-first Android app (Kotlin + Jetpack Compose) that helps **AESH**
(*Accompagnants des Élèves en Situation de Handicap* — special needs teaching
assistants) manage their daily work:

- 🗓️ Weekly schedule management, with an optional on-device AI feature to
  pre-fill the schedule from a photo
- 📝 Daily student observation logging (mood, focus, social interactions, notes)
- 🔔 Local reminders to fill out daily reports
- 📄 AI-assisted **ESS** (*Équipe de Suivi de Scolarisation*) report generation,
  fully offline, with PDF export
- 💾 Local-only storage (Room) with manual backup/restore — **no backend, no
  cloud, no accounts**

## Tech stack

- Kotlin, Jetpack Compose, Jetpack Navigation (type-safe routes)
- Dependency Injection ([Koin](https://insert-koin.io/))
- Room (local persistence)
- WorkManager (daily reminder notifications)
- CameraX + ML Kit Text Recognition (on-device schedule scanning)
- ML Kit GenAI Prompt API — Gemini Nano via Android's AICore system service
  (on-device ESS report generation)

## Architecture

The codebase follows Clean Architecture, with dependencies always pointing
inward:

- `presentation` — Compose screens, `ViewModel`s and immutable UI state.
  Contains no business logic, only rendering + user input handling.
- `domain` — use cases and business models (framework-agnostic Kotlin).
- `data` — repositories and data sources (Room, DataStore, on-device ML)
  implementing domain interfaces.

`domain` and `data` will grow as the corresponding milestones land; today
`domain` exposes a `Student` model and `StudentRepository` contract, `data`
implements it on top of Room (`AeshDatabase`, `StudentDao`,
`StudentRepositoryImpl`), and `presentation` hosts a `Dashboard`
(`HomeScreen` + `HomeViewModel`) and a placeholder `Settings` screen,
connected through a Jetpack Navigation Compose graph (`AeshNavHost`) and
resolved via Koin (`presentationModule`, `dataModule`).

Koin was chosen over Hilt for dependency injection: it needs no annotation
processor (no KAPT/KSP), which keeps build times low and setup simple for
this project's scope.

## Project status

This project is under active development. See the [issues](../../issues) and
[milestones](../../milestones) for the roadmap:

1. Project Setup & Architecture
2. Core Data & Student Management
3. Daily Tracking
4. Schedule & Reminders
5. Security & Backup
6. AI Features

## Getting started

Open the project in Android Studio and run the `app` module on a device or
emulator running API 26+.

```bash
./gradlew ktlintCheck         # formatting check (ktlint)
./gradlew ktlintFormat        # auto-fix formatting
./gradlew testDebugUnitTest   # unit tests
./gradlew lintDebug           # static analysis
./gradlew assembleDebug       # debug APK
```

## Continuous integration & releases

- **`.github/workflows/android.yml`** checks Kotlin formatting, builds,
  lints and unit-tests every pull request targeting `main` (and pushes to
  `main`).
- **`.github/workflows/release.yml`** publishes signed, installable APKs as
  [GitHub Releases](../../releases). Trigger it either by running the
  workflow manually from the Actions tab, or by pushing a tag matching
  `v*.*.*`.

### Versioning

The app uses [Calendar Versioning](https://calver.org) with the
`YYYY.MM.MICRO` scheme (e.g. `2026.09.0`, `2026.09.1`, ...). The release
workflow computes the next version automatically from existing tags, so no
manual bookkeeping is required.

### Installing a release

Download the latest `.apk` from the [Releases page](../../releases),
transfer it to an Android 8.0+ (API 26+) device, and open it (enabling
"install from unknown sources" if prompted). Every release is signed with the
same dedicated key (stored as encrypted GitHub secrets, not committed to the
repo), so newer releases can be installed over older ones without
uninstalling first. This signing key is meant for sideloaded distribution
only — a Play Store submission would require its own dedicated upload key.

You can verify a downloaded APK is genuinely signed with that key using
[`apksigner`](https://developer.android.com/tools/apksigner) (bundled with
the Android SDK build-tools):

```bash
apksigner verify --print-certs AeshAssistant-<version>.apk
# Expect SHA-256 digest:
# 971e4db2b2444bab573b18d5dd9fe88f22b8481d6e313624eb305769f612939d
```

## Privacy

All data stays on-device. No network backend is used; the optional AI
features rely exclusively on on-device models (ML Kit, Gemini Nano / AICore) —
nothing is ever sent to a server.
