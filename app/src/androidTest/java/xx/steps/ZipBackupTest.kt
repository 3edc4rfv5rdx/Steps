package xx.steps

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xx.steps.data.AppDatabase
import xx.steps.data.DaySteps
import xx.steps.data.RestoreFailure
import xx.steps.data.StepsRepository
import xx.steps.data.importZip
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Restoring a ZIP backup. The export writes through MediaStore, which an instrumentation test
 * cannot read back cleanly, so the archives here are built by hand from a real database file —
 * which is exactly what the export produces.
 */
@RunWith(AndroidJUnit4::class)
class ZipBackupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: StepsRepository
    private val scratch = File(context.cacheDir, "ziptest").apply { mkdirs() }

    private val today = LocalDate.of(2026, 8, 25)

    @Before
    fun setUp() {
        repository = StepsRepository(AppDatabase.get(context))
        runBlocking { repository.clearAll() }
    }

    @After
    fun tearDown() {
        runBlocking { repository.clearAll() }
        scratch.deleteRecursively()
    }

    /** Builds an archive holding a database with [days] in it, as an export would. */
    private suspend fun archiveOf(days: List<DaySteps>): Uri {
        repository.setDays(days)
        AppDatabase.get(context).openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }

        val zip = File(scratch, "backup.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("steps.db"))
            context.getDatabasePath("steps.db").inputStream().use { it.copyTo(out) }
            out.closeEntry()
        }
        return zip.toUri()
    }

    @Test
    fun aBackupRestoresTheDaysItHolds() = runBlocking {
        val days = listOf(
            DaySteps(today.toIso(), 4_000, 8_000),
            DaySteps(today.minusDays(1).toIso(), 9_100, 6_000),
        )
        val archive = archiveOf(days)

        // Wipe, then restore: the days must come back exactly, goals included.
        repository.clearAll()
        assertNull(importZip(context, archive, repository))

        val restored = repository.allDays()
        assertEquals(2, restored.size)
        assertEquals(9_100, restored.first { it.date == today.minusDays(1).toIso() }.steps)
        assertEquals(6_000, restored.first { it.date == today.minusDays(1).toIso() }.goal)
    }

    @Test
    fun restoringReplacesWhatWasThere() = runBlocking {
        val archive = archiveOf(listOf(DaySteps(today.toIso(), 1_000, 8_000)))

        repository.clearAll()
        repository.setDays(listOf(DaySteps(today.minusDays(5).toIso(), 7_777, 8_000)))

        assertNull(importZip(context, archive, repository))

        // A restore is a replacement, not a merge: the day that was not in the archive is gone.
        assertEquals(listOf(today.toIso()), repository.allDays().map { it.date })
    }

    @Test
    fun theCounterBaselineIsNotCarriedOver() = runBlocking {
        val archive = archiveOf(listOf(DaySteps(today.toIso(), 1_000, 8_000)))

        repository.clearAll()
        // Pretend this phone's sensor has been read before, then restore over it.
        repository.recordReading(rawCount = 50_000, goal = 8_000, today = today, uptimeMillis = 7_200_000)
        assertNull(importZip(context, archive, repository))

        // With the baseline cleared, the next reading only re-establishes it and credits nothing —
        // a figure from another phone would otherwise swallow or invent a chunk of steps.
        repository.recordReading(rawCount = 90_000, goal = 8_000, today = today, uptimeMillis = 7_260_000)
        assertEquals(1_000, repository.observeDay(today).first()?.steps)
    }

    @Test
    fun aFileThatIsNotAnArchiveIsRefused() = runBlocking {
        val notZip = File(scratch, "notes.txt").apply { writeText("just some text") }

        assertEquals(RestoreFailure.NOT_AN_ARCHIVE, importZip(context, notZip.toUri(), repository))
    }

    @Test
    fun anArchiveWithoutTheDatabaseIsRefused() = runBlocking {
        val zip = File(scratch, "other.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("readme.txt"))
            out.write("nothing useful".toByteArray())
            out.closeEntry()
        }

        assertEquals(RestoreFailure.NO_DATABASE_INSIDE, importZip(context, zip.toUri(), repository))
    }

    @Test
    fun aRefusedRestoreLeavesTheHistoryAlone() = runBlocking {
        repository.setDays(listOf(DaySteps(today.toIso(), 5_555, 8_000)))
        val notZip = File(scratch, "broken.zip").apply { writeText("PK not really") }

        assertNotNull(importZip(context, notZip.toUri(), repository))

        assertEquals(5_555, repository.observeDay(today).first()?.steps)
    }
}
