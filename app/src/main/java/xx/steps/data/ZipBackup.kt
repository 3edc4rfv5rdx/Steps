package xx.steps.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xx.steps.DATABASE_NAME
import xx.steps.EXPORT_DIR
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Whole-database backup and restore. A backup is a ZIP holding one SQLite file — the complete step
 * history byte for byte — written to `Documents/Steps`, where it survives the app being
 * uninstalled and is easy to copy off the phone.
 *
 * This is deliberately distinct from the CSV export: CSV is readable and merges, a ZIP is an exact
 * snapshot that replaces. Restoring one is therefore destructive, and the UI asks first.
 */

private const val ZIP_MIME = "application/zip"

/** Name of the single SQLite entry inside the archive. */
private const val DB_ENTRY_NAME = DATABASE_NAME

/** A corrupt or malicious archive must not fill internal storage while being unpacked. */
private const val MAX_RESTORE_BYTES = 64L * 1024L * 1024L

private fun backupFileName(now: Long = System.currentTimeMillis()): String =
    "steps-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))}.zip"

/** Where a backup landed, for telling the user. */
data class BackupResult(val fileName: String)

/** Why a restore refused, or null when it worked. */
enum class RestoreFailure { NOT_AN_ARCHIVE, NO_DATABASE_INSIDE, TOO_LARGE, NOT_A_DATABASE }

/**
 * How a restore ended: [failure] null when it went through, and [daysDropped] the days the archive
 * held that this app could not read back. A restore that drops days is not a failure, but it is not
 * a clean success either — the user is told the number.
 */
data class RestoreResult(val failure: RestoreFailure?, val daysDropped: Int)

/**
 * Zips the live database into `Documents/Steps`. The write-ahead log is checkpointed first, or the
 * copy would miss everything recorded since the last checkpoint.
 */
suspend fun exportZip(context: Context, database: AppDatabase): BackupResult? =
    withContext(Dispatchers.IO) {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }

        val dbFile = context.getDatabasePath(DATABASE_NAME)
        if (!dbFile.exists()) return@withContext null

        val name = backupFileName()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, ZIP_MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, EXPORT_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val target: Uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
            ?: return@withContext null

        try {
            resolver.openOutputStream(target)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry(DB_ENTRY_NAME))
                    dbFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            } ?: run {
                resolver.delete(target, null, null)
                return@withContext null
            }
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            throw e
        }

        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        BackupResult(fileName = name)
    }

/**
 * Replaces the database with the one inside [source]. Returns null on success, or the reason it
 * refused — nothing is touched until the archive has been unpacked and proven to be a database, so
 * a bad file leaves the existing history exactly as it was.
 */
suspend fun importZip(context: Context, source: Uri, repository: StepsRepository): RestoreResult =
    withContext(Dispatchers.IO) {
        val staged = File(context.cacheDir, "restore-$DATABASE_NAME")
        staged.delete()

        try {
            val extractionFailure = try {
                extractDatabase(context, source, staged)
            } catch (_: Exception) {
                return@withContext RestoreResult(RestoreFailure.NOT_AN_ARCHIVE, 0)
            }
            if (extractionFailure != null) return@withContext RestoreResult(extractionFailure, 0)

            val snapshot = readSnapshot(staged)
                ?: return@withContext RestoreResult(RestoreFailure.NOT_A_DATABASE, 0)

            // Having our table names is not proof of holding our rows. What cannot be read back is
            // dropped here rather than stored and crashed on later — see usableRows.
            val usable = usableRows(snapshot.days, snapshot.slots)
            // An archive whose every day was unreadable is not ours at all, and a restore that put
            // nothing in place of everything would be the worst possible reading of "replace".
            if (usable.days.isEmpty() && snapshot.days.isNotEmpty()) {
                return@withContext RestoreResult(RestoreFailure.NOT_A_DATABASE, usable.daysDropped)
            }

            // The rows are poured into the live database rather than the file being swapped
            // underneath it: the open connection keeps serving, every screen updates on its own,
            // and there is no window where the app holds a database that no longer exists.
            //
            // The counter baseline is not restored, only cleared: it describes where *this* phone's
            // sensor stood, and a figure from another phone (or another install) would credit or
            // swallow a chunk of steps on the next reading.
            repository.restoreAll(days = usable.days, slots = usable.slots)
            RestoreResult(failure = null, daysDropped = usable.daysDropped)
        } finally {
            staged.delete()
        }
    }

/** Everything a restore carries over: the days and, where the archive has them, their breakdown. */
private class BackupSnapshot(val days: List<DaySteps>, val slots: List<DaySlot>)

/**
 * Reads the rows out of a staged database file, or null if it is not one of ours.
 *
 * Both tables are read through one connection. An archive written before the breakdown existed has
 * no `day_slots` table at all: that is a backup without a breakdown, not a broken backup, so the
 * query for it is allowed to fail on its own while the days still come through.
 */
private fun readSnapshot(file: File): BackupSnapshot? = runCatching {
    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        val days = db.rawQuery("SELECT date, steps, goal FROM day_steps", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DaySteps(
                            date = cursor.getString(0),
                            steps = cursor.getInt(1),
                            goal = cursor.getInt(2),
                        ),
                    )
                }
            }
        }

        val slots = runCatching {
            db.rawQuery("SELECT date, slot, steps FROM day_slots", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            DaySlot(
                                date = cursor.getString(0),
                                slot = cursor.getInt(1),
                                steps = cursor.getInt(2),
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())

        BackupSnapshot(days = days, slots = slots)
    }
}.getOrNull()

/** Unpacks the database entry into [into]; returns a failure reason, or null when it worked. */
private fun extractDatabase(context: Context, source: Uri, into: File): RestoreFailure? {
    var entriesSeen = 0

    context.contentResolver.openInputStream(source)?.use { input ->
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entriesSeen++
                // Match by name only, and ignore any path in it: an entry called ../../something
                // must never decide where this file lands.
                if (!entry.isDirectory && File(entry.name).name == DB_ENTRY_NAME) {
                    into.outputStream().use { out ->
                        var total = 0L
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_RESTORE_BYTES) return RestoreFailure.TOO_LARGE
                            out.write(buffer, 0, read)
                        }
                    }
                    return null
                }
                entry = zip.nextEntry
            }
        }
    } ?: return RestoreFailure.NOT_AN_ARCHIVE

    // A file that is not a ZIP yields no entries at all rather than throwing, so "no entries" and
    // "entries, but not ours" are different answers — and the user is told which.
    return if (entriesSeen == 0) RestoreFailure.NOT_AN_ARCHIVE else RestoreFailure.NO_DATABASE_INSIDE
}

