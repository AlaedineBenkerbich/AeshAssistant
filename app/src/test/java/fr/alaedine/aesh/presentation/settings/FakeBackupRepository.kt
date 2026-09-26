package fr.alaedine.aesh.presentation.settings

import fr.alaedine.aesh.domain.repository.BackupRepository
import java.io.InputStream
import java.io.OutputStream

/**
 * In-memory [BackupRepository] test double, avoiding the need for a
 * mocking library or a real Room database to unit test [SettingsViewModel].
 *
 * @property exportError When non-null, thrown by [exportBackup] instead of
 * writing anything, to simulate an I/O/serialization failure.
 * @property importError When non-null, thrown by [importBackup] instead of
 * reading anything, to simulate an I/O/deserialization failure.
 */
class FakeBackupRepository(
    private val exportError: Throwable? = null,
    private val importError: Throwable? = null,
) : BackupRepository {

    var exportedTo: OutputStream? = null
        private set
    var importedFrom: InputStream? = null
        private set

    override suspend fun exportBackup(destination: OutputStream) {
        exportError?.let { throw it }
        exportedTo = destination
    }

    override suspend fun importBackup(source: InputStream) {
        importError?.let { throw it }
        importedFrom = source
    }
}
