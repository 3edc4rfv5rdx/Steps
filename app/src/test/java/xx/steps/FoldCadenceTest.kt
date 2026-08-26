package xx.steps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xx.steps.steps.shouldFold

/**
 * How often a reading is written to the database. The counter is cumulative, so a reading held back
 * loses nothing — the next one carries its steps — but a screen the user is looking at has to grow
 * as they walk, which is the one case where nothing may be held back.
 */
class FoldCadenceTest {

    private val minute = BACKGROUND_FOLD_INTERVAL_MS

    @Test
    fun `nothing is held back while a screen is open`() {
        assertTrue(shouldFold(lastFoldMillis = 0L, now = 1L, screenOpen = true, paused = false))
        assertTrue(shouldFold(lastFoldMillis = 1_000L, now = 1_001L, screenOpen = true, paused = false))
    }

    @Test
    fun `the first reading of a process is written at once`() {
        assertTrue(shouldFold(lastFoldMillis = null, now = 0L, screenOpen = false, paused = false))
        assertTrue(shouldFold(lastFoldMillis = null, now = 9_999_999L, screenOpen = false, paused = false))
    }

    @Test
    fun `with no screen open a reading inside the interval is held back`() {
        assertFalse(shouldFold(lastFoldMillis = 10 * minute, now = 10 * minute + 1, screenOpen = false, paused = false))
        assertFalse(shouldFold(lastFoldMillis = 10 * minute, now = 11 * minute - 1, screenOpen = false, paused = false))
    }

    @Test
    fun `a pause moves the baseline on every reading, screen or no screen`() {
        // A pause throws steps away, and where the baseline stands when it ends decides which ones.
        // A baseline a minute behind the counter would hand a minute of the bus ride to the walk.
        assertTrue(
            shouldFold(lastFoldMillis = 10 * minute, now = 10 * minute + 1, screenOpen = false, paused = true),
        )
    }

    @Test
    fun `the interval itself is long enough`() {
        assertTrue(shouldFold(lastFoldMillis = 10 * minute, now = 11 * minute, screenOpen = false, paused = false))
        assertTrue(shouldFold(lastFoldMillis = 10 * minute, now = 40 * minute, screenOpen = false, paused = false))
    }
}
