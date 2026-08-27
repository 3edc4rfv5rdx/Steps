package xx.steps.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xx.steps.EXPORT_DIR
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writing the history out to a file anyone can read, and reading one back in.
 *
 * Exports land in the shared `Documents/Steps` folder through MediaStore: the file survives the
 * app being uninstalled, is visible to any file manager, and needs no storage permission. Imports
 * come from wherever the system picker points, so they can also come from a cloud folder or a
 * download.
 */

private const val CSV_MIME = "text/csv"

/** A corrupt or enormous file must not be pulled into memory whole. */
private const val MAX_IMPORT_BYTES = 8L * 1024L * 1024L

/** Export file name: steps-YYYYMMDD-HHMMSS.csv. */
private fun exportFileName(now: Long = System.currentTimeMillis()): String =
    "steps-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))}.csv"

/** What an export produced: the display name written, for telling the user where it went. */
data class ExportResult(val fileName: String, val days: Int)

/**
 * What an import found and did. [written] counts days actually changed, not lines read.
 *
 * [breakdownsDropped] counts the days that lost their hour-by-hour breakdown to it: a file carries
 * day totals and nothing else, so a day whose total the import raises can keep no breakdown of the
 * old one. It is worth telling the user about — it is the one thing an import destroys.
 */
data class ImportResult(
    val written: Int,
    val skipped: Int,
    val read: Int,
    val breakdownsDropped: Int = 0,
)

/**
 * Writes every recorded day to `Documents/Steps`. Returns null only if MediaStore refuses to
 * create the file at all — a full disk, or a device with the folder locked down.
 */
suspend fun exportCsv(context: Context, days: List<DaySteps>): ExportResult? =
    withContext(Dispatchers.IO) {
        val name = exportFileName()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, CSV_MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, EXPORT_DIR)
            // Kept pending until the bytes are there, so nothing reads a half-written file.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val target: Uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
            ?: return@withContext null

        try {
            resolver.openOutputStream(target)?.use { stream ->
                stream.write(formatCsv(days).toByteArray())
            } ?: run {
                resolver.delete(target, null, null)
                return@withContext null
            }
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            throw e
        }

        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        ExportResult(fileName = name, days = days.size)
    }

/**
 * Reads a chosen file and folds it into the database. Days already fuller in the database are left
 * alone, so importing the same file twice changes nothing the second time — and a day left alone
 * keeps its breakdown, which only the days actually overwritten lose.
 */
suspend fun importCsv(
    context: Context,
    source: Uri,
    repository: StepsRepository,
    fallbackGoal: Int,
): ImportResult = withContext(Dispatchers.IO) {
    val text = context.contentResolver.openInputStream(source)?.use { stream ->
        String(stream.readNBytes(MAX_IMPORT_BYTES.toInt()))
    } ?: return@withContext ImportResult(written = 0, skipped = 0, read = 0)

    val parsed = parseCsv(text)
    val existing = repository.allDays()
    val toWrite = mergeDays(existing = existing, imported = parsed.days, fallbackGoal = fallbackGoal)

    val breakdownsDropped = repository.setDays(toWrite)

    ImportResult(
        written = toWrite.size,
        skipped = parsed.skipped,
        read = parsed.days.size,
        breakdownsDropped = breakdownsDropped,
    )
}
