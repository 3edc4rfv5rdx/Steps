package xx.steps

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import xx.steps.settings.AppSettings

/**
 * Writes one line to the counting journal. Free to call from anywhere, including the sensor
 * callback: the line is queued and written on a background thread.
 */
fun logSteps(message: String) = StepLog.write(message)

/**
 * The counting journal, as a plain text file the user can open in any file manager:
 * `Documents/Steps/steps-<date>.log`, one file per day, one line per event.
 *
 * It exists because the sensor is the one part of this app that cannot be reasoned about from the
 * code: whether a reading arrives at all is the phone's decision, and nothing else records it.
 * Lines are queued and appended in batches, so a burst of sensor events during a walk costs one
 * file open rather than one per step.
 */
object StepLog {

    private const val TAG = "Steps"

    /** Where the files land, spelled the way MediaStore spells a public folder. */
    private const val RELATIVE_PATH = "Documents/Steps/"

    /** How long a batch is allowed to gather before it is written. */
    private const val BATCH_MS = 1_000L

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    /** Unbounded on purpose: lines queued before [start] runs are kept, not dropped. */
    private val pending = Channel<String>(Channel.UNLIMITED)

    private var started = false

    /** The day's file, remembered between batches; dropped whenever a write fails. */
    private var cached: Pair<String, Uri>? = null

    /** Starts the writer. Called once, from the application object. */
    fun start(context: Context) {
        if (started) return
        started = true
        val resolver = context.applicationContext.contentResolver
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (first in pending) {
                // Wait once, then take everything that arrived meanwhile: during a walk the events
                // come twice a second, and each one on its own would mean reopening the file.
                delay(BATCH_MS)
                val batch = StringBuilder().appendLine(first)
                while (true) {
                    val next = pending.tryReceive().getOrNull() ?: break
                    batch.appendLine(next)
                }
                append(resolver, batch.toString())
            }
        }
    }

    fun write(message: String) {
        if (!AppSettings.journalEnabled.value) return
        pending.trySend("${LocalDateTime.now().format(STAMP)} $message")
    }

    /**
     * Turns the journal on or off, leaving a line on whichever side of the switch is still
     * recording: a file that simply stops has a gap in it that nothing explains.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        if (!enabled) write("journal: switched off")
        AppSettings.setJournalEnabled(context, enabled)
        if (enabled) write("journal: switched on")
    }

    private fun append(resolver: ContentResolver, text: String) {
        val today = LocalDate.now().toIso()
        val uri = uriFor(resolver, today) ?: return
        runCatching {
            // "wa" appends; anything else would truncate the day's file on every batch.
            resolver.openOutputStream(uri, "wa")?.use { it.write(text.toByteArray()) }
                ?: error("no stream for $uri")
        }.onFailure {
            // The file may have been deleted or moved from under us; the next batch makes a new one.
            cached = null
            Log.w(TAG, "journal append failed", it)
        }
    }

    /** The day's file, found if it is already there and created if it is not. */
    private fun uriFor(resolver: ContentResolver, day: String): Uri? {
        cached?.let { (cachedDay, uri) -> if (cachedDay == day) return uri }

        // Named .txt, not .log: MediaStore appends an extension of its own choosing to a name whose
        // one does not match the MIME type, and the file it then stores is not the file we look for
        // next time — which quietly starts a new one on every process start.
        val name = "steps-$day.txt"
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val found = runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                // The name is matched exactly and the folder only loosely: MediaStore is free to
                // store the path with or without its trailing slash, and a miss here would not fail
                // loudly — it would quietly start a second file beside the first one every day.
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf(name, "$RELATIVE_PATH%"),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
            }
        }.getOrNull()

        val uri = found ?: runCatching {
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
                },
            )
        }.onFailure { Log.w(TAG, "journal file could not be created", it) }.getOrNull()

        if (uri != null) cached = day to uri
        return uri
    }
}
