package xx.steps

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xx.steps.ui.BannerKind
import xx.steps.ui.BannerMessage
import xx.steps.ui.SettingsWork

/**
 * The work the Settings screen starts outlives the screen. A tab switch used to take the scope an
 * import was running in, so a confirmed operation was cancelled halfway with nothing said.
 */
class SettingsWorkTest {

    private fun message(text: String) = BannerMessage(BannerKind.SUCCESS, text)

    @Before
    fun setUp() {
        // The object is process-wide, so each test starts from a known state.
        runBlocking {
            SettingsWork.clear()
            withTimeout(TIMEOUT) { SettingsWork.running.first { !it } }
        }
    }

    @Test
    fun `a result finished with nobody looking waits for the next observer`() = runBlocking {
        assertTrue(SettingsWork.run { message("done") })

        // Nothing is collecting while it runs — which is exactly the tab switch this exists for.
        val shown = withTimeout(TIMEOUT) { SettingsWork.message.first { it != null } }
        assertEquals("done", shown?.text)
    }

    @Test
    fun `a second job is refused while one is still running`() = runBlocking {
        val gate = CompletableDeferred<Unit>()

        assertTrue(SettingsWork.run { gate.await(); message("first") })
        assertFalse(SettingsWork.run { message("second") })

        gate.complete(Unit)
        val shown = withTimeout(TIMEOUT) { SettingsWork.message.first { it != null } }
        assertEquals("first", shown?.text)
    }

    @Test
    fun `a job that throws does not leave the screen refusing every one after it`() = runBlocking {
        assertTrue(SettingsWork.run { error("no disk") })
        withTimeout(TIMEOUT) { SettingsWork.running.first { !it } }

        assertTrue(SettingsWork.run { message("after") })
        val shown = withTimeout(TIMEOUT) { SettingsWork.message.first { it != null } }
        assertEquals("after", shown?.text)
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
