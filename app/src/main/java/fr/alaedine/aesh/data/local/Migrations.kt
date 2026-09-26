package fr.alaedine.aesh.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the `daily_reports` table (see
 * [fr.alaedine.aesh.data.local.entity.DailyReportEntity]) introduced for the
 * Daily Tracking milestone (#5). The existing `students` table is left
 * untouched, so upgrading preserves any students already saved on-device.
 */
val MIGRATION_1_2: Migration = object : Migration(startVersion = 1, endVersion = 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `daily_reports` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `studentId` INTEGER NOT NULL,
            `date` TEXT NOT NULL,
            `moodLevel` INTEGER NOT NULL,
            `focusLevel` INTEGER NOT NULL,
            `socialInteractions` INTEGER NOT NULL,
            `freeNotes` TEXT NOT NULL,
            FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_reports_studentId_date` " +
                "ON `daily_reports` (`studentId`, `date`)",
        )
    }
}
