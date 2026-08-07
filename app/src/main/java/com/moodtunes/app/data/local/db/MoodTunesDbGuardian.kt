package com.moodtunes.app.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import timber.log.Timber
import java.io.File

/**
 * Protects against database corruption (no data loss):
 *  - Keeps a backup of the last-known-good database file.
 *  - On open, runs an integrity check; a corrupt database is flagged so the
 *    backup can be restored on the next launch instead of silently losing data.
 */
object MoodTunesDbGuardian {

    fun databaseFile(context: Context): File =
        context.getDatabasePath(MoodTunesDatabase.DATABASE_NAME)

    fun backupFile(context: Context): File =
        context.getDatabasePath("${MoodTunesDatabase.DATABASE_NAME}_backup")

    fun corruptMarkerFile(context: Context): File =
        File(context.filesDir, "moodtunes_db_corrupt")

    /**
     * Called before the Room builder runs. If the previous session ended with a
     * failed integrity check, restore the last-known-good backup.
     */
    fun ensureHealthy(context: Context) {
        val marker = corruptMarkerFile(context)
        if (!marker.exists()) return
        val dbFile = databaseFile(context)
        val backup = backupFile(context)
        if (backup.exists() && backup.length() > 0) {
            runCatching {
                dbFile.parentFile?.mkdirs()
                backup.copyTo(dbFile, overwrite = true)
                listOf(
                    File(dbFile.path + "-wal"),
                    File(dbFile.path + "-shm"),
                    File(dbFile.path + "-journal")
                ).forEach { if (it.exists()) it.delete() }
                Timber.w("Restored music database from backup after corruption")
            }.onFailure {
                Timber.e(it, "Failed to restore database from backup")
            }
        }
        marker.delete()
    }

    /** Flags the database as corrupt so [ensureHealthy] restores it next launch. */
    fun markCorrupt(context: Context) {
        runCatching { corruptMarkerFile(context).writeText("corrupt") }
    }

    fun clearCorruptMarker(context: Context) {
        runCatching { corruptMarkerFile(context).delete() }
    }

    /**
     * Checkpoints WAL and copies the verified-good database to the backup path.
     * Only rewrites the backup if the file actually changed.
     */
    fun backupGoodDatabase(context: Context) {
        val dbFile = databaseFile(context)
        if (!dbFile.exists()) return
        val backup = backupFile(context)
        if (backup.exists() && backup.length() == dbFile.length()) return
        runCatching {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                runCatching { db.rawQuery("PRAGMA wal_checkpoint(FULL)", null)?.close() }
            }
            backup.parentFile?.mkdirs()
            dbFile.copyTo(backup, overwrite = true)
        }.onFailure {
            Timber.e(it, "Failed to back up music database")
        }
    }
}
