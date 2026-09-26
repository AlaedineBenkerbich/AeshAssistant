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

- Kotlin, Jetpack Compose, Jetpack Navigation
- Dependency Injection (Hilt or Koin — see #2)
- Room (local persistence)
- WorkManager (daily reminder notifications)
- CameraX + ML Kit Text Recognition (on-device schedule scanning)
- AICore / Gemini Nano (on-device ESS report generation)

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

## Privacy

All data stays on-device. No network backend is used; the optional AI
features rely exclusively on on-device models (ML Kit, Gemini Nano / AICore) —
nothing is ever sent to a server.
