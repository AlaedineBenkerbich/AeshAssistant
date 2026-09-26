package fr.alaedine.aesh.data.repository

import androidx.room.withTransaction
import fr.alaedine.aesh.data.backup.BackupPayload
import fr.alaedine.aesh.data.backup.toBackup
import fr.alaedine.aesh.data.backup.toEntity
import fr.alaedine.aesh.data.local.AeshDatabase
import fr.alaedine.aesh.data.local.dao.DailyReportDao
import fr.alaedine.aesh.data.local.dao.ScheduleSlotDao
import fr.alaedine.aesh.data.local.dao.StudentDao
import fr.alaedine.aesh.domain.repository.BackupRepository
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * [BackupRepository] backed by Room: serializes/restores every
 * [StudentDao], [DailyReportDao] and [ScheduleSlotDao] row as a single JSON
 * document (see [BackupPayload]).
 *
 * Needs [database] itself (in addition to the individual DAOs) to wrap the
 * multi-table restore in [withTransaction], so a failure partway through
 * never leaves the local database with only some tables replaced.
 */
class BackupRepositoryImpl(
    private val database: AeshDatabase,
    private val studentDao: StudentDao,
    private val dailyReportDao: DailyReportDao,
    private val scheduleSlotDao: ScheduleSlotDao,
) : BackupRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override suspend fun exportBackup(destination: OutputStream) {
        val payload = BackupPayload(
            students = studentDao.observeAll().first().map { it.toBackup() },
            dailyReports = dailyReportDao.observeAll().first().map { it.toBackup() },
            scheduleSlots = scheduleSlotDao.observeAll().first().map { it.toBackup() },
        )
        withContext(Dispatchers.IO) {
            destination.use { stream ->
                stream.write(json.encodeToString(BackupPayload.serializer(), payload).toByteArray())
            }
        }
    }

    override suspend fun importBackup(source: InputStream) {
        val payload = withContext(Dispatchers.IO) {
            source.use { stream ->
                json.decodeFromString(BackupPayload.serializer(), stream.readBytes().decodeToString())
            }
        }
        require(payload.schemaVersion == BackupPayload.SCHEMA_VERSION) {
            "Unsupported backup schema version: ${payload.schemaVersion}"
        }

        database.withTransaction {
            // Children first so foreign keys never dangle mid-restore; schedule
            // slots have no FK but are cleared here too for a fully atomic reset.
            dailyReportDao.deleteAll()
            scheduleSlotDao.deleteAll()
            studentDao.deleteAll()

            studentDao.insertAll(payload.students.map { it.toEntity() })
            dailyReportDao.insertAll(payload.dailyReports.map { it.toEntity() })
            scheduleSlotDao.insertAll(payload.scheduleSlots.map { it.toEntity() })
        }
    }
}
