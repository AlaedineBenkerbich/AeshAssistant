package fr.alaedine.aesh.domain.repository

import java.io.InputStream
import java.io.OutputStream

/**
 * Framework-agnostic contract for exporting/importing the entire local
 * database (students, daily reports, schedule slots) as a single JSON
 * document, backing the Settings screen's manual backup/restore feature.
 *
 * Deliberately expressed over plain [OutputStream]/[InputStream] rather
 * than `android.net.Uri`: the Storage Access Framework picker that lets the
 * user choose *where* to save/read the file is a presentation-layer
 * concern (see `fr.alaedine.aesh.presentation.settings.SettingsRoute`,
 * which resolves the user-picked file into a stream via `ContentResolver`
 * and hands it here), keeping this contract free of Android imports like
 * every other `domain` interface.
 *
 * Implemented by [fr.alaedine.aesh.data.repository.BackupRepositoryImpl] on
 * top of Room.
 */
interface BackupRepository {
    /** Serializes every student, daily report and schedule slot as JSON into [destination]. */
    suspend fun exportBackup(destination: OutputStream)

    /**
     * Replaces the entire local database with the content read from
     * [source], which must have been produced by [exportBackup].
     *
     * @throws Exception if [source] isn't valid backup JSON (including an
     * unsupported schema version); the local database is left untouched in
     * that case.
     */
    suspend fun importBackup(source: InputStream)
}
