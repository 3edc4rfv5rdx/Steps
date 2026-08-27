package xx.steps

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xx.steps.ui.BannerKind
import xx.steps.ui.BannerMessage
import xx.steps.ui.ScreenWork

/**
 * The work a screen starts outlives the screen. A tab switch used to take the scope an import was
 * running in, so a confirmed operation was cancelled halfway with nothing said.
 */
class ScreenWorkTest {

    private fun message(text: String) = BannerMessage(BannerKind.SUCCESS, text)

    @Before
    fun setUp() {
        // The object is process-wide, so each test starts from a known state.
        runBlocking {
            ScreenWork.clear()
            withTimeout(TIMEOUT) { ScreenWork.running.first { !it } }
        }
    }

    @Test
    fun `a result finished with nobody looking waits for the next observer`() = runBlocking {
        assertTrue(ScreenWork.run(FAILED, BUSY) { message("done") })

        // Nothing is collecting while it runs — which is exactly the tab switch this exists for.
        val shown = withTimeout(TIMEOUT) { ScreenWork.message.first { it != null } }
        assertEquals("done", shown?.text)
    }

    @Test
    fun `a second job is refused while one is still running`() = runBlocking {
        val gate = CompletableDeferred<Unit>()

        assertTrue(ScreenWork.run(FAILED, BUSY) { gate.await(); message("first") })
        assertFalse(ScreenWork.run(FAILED, BUSY) { message("second") })

        gate.complete(Unit)
        val shown = withTimeout(TIMEOUT) { ScreenWork.message.first { it?.text == "first" } }
        assertEquals("first", shown?.text)
    }

    /**
     * The demo switch and a restore both replace every row there is. The refused one has to say so
     * and, above all, has to leave the database alone — its job body must never run.
     */
    @Test
    fun `a refused job says so and never touches anything`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var second = false

        assertTrue(ScreenWork.run(FAILED, BUSY) { gate.await(); message("first") })
        assertFalse(ScreenWork.run(FAILED, BUSY) { second = true; message("second") })

        val refused = withTimeout(TIMEOUT) { ScreenWork.message.first { it != null } }
        assertEquals(BannerKind.WARNING, refused?.kind)
        assertEquals(BUSY, refused?.text)
        assertFalse(second)

        gate.complete(Unit)
        withTimeout(TIMEOUT) { ScreenWork.running.first { !it } }
        assertFalse(second)
    }

    /** The demo switch has nothing to report, and must not wipe what the last job left behind. */
    @Test
    fun `a job with nothing to say leaves the banner before it alone`() = runBlocking {
        ScreenWork.show(message("before"))

        assertTrue(ScreenWork.run(FAILED, BUSY) { null })
        withTimeout(TIMEOUT) { ScreenWork.running.first { !it } }

        assertEquals("before", ScreenWork.message.value?.text)
    }

    @Test
    fun `a job that throws does not leave the screen refusing every one after it`() = runBlocking {
        assertTrue(ScreenWork.run(FAILED, BUSY) { error("no disk") })
        withTimeout(TIMEOUT) { ScreenWork.running.first { !it } }
        ScreenWork.clear()

        assertTrue(ScreenWork.run(FAILED, BUSY) { message("after") })
        val shown = withTimeout(TIMEOUT) { ScreenWork.message.first { it != null } }
        assertEquals("after", shown?.text)
    }

    /**
     * The file the picker handed over can be gone, or behind a provider that fails half way. On
     * Android an exception nobody catches here is not a stack trace in a log — it is the process
     * being killed by `RuntimeInit`, with the import neither done nor reported.
     */
    @Test
    fun `a job that throws answers a red banner and never reaches the uncaught handler`() = runBlocking {
        val seen = withUncaughtHandler {
            assertTrue(ScreenWork.run(FAILED, BUSY) { error("no disk") })
            val shown = withTimeout(TIMEOUT) { ScreenWork.message.first { it != null } }
            assertEquals(BannerKind.ERROR, shown?.kind)
            assertEquals(FAILED, shown?.text)
        }
        assertNull(seen)
    }

    @Test
    fun `a job started with launch that throws never reaches the uncaught handler`() = runBlocking {
        val seen = withUncaughtHandler {
            val started = CompletableDeferred<Unit>()
            ScreenWork.launch { started.complete(Unit); error("no disk") }
            withTimeout(TIMEOUT) { started.await() }
        }
        assertNull(seen)
    }

    /**
     * Runs [body] with the default handler replaced, and answers what that handler saw — null if
     * nothing reached it within [GRACE] of the job failing. The handler is process-wide and is put
     * back whatever happens, so one test cannot blind the next.
     */
    private suspend fun withUncaughtHandler(body: suspend () -> Unit): Throwable? {
        val seen = CompletableDeferred<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> seen.complete(error) }
        try {
            body()
            return withTimeoutOrNull(GRACE) { seen.await() }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    private companion object {
        const val TIMEOUT = 5_000L

        /** How long a failure is given to surface on another thread before it is called absent. */
        const val GRACE = 1_000L

        const val FAILED = "could not read the file"

        const val BUSY = "another operation is still running"
    }
}
