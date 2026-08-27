package xx.steps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xx.steps.steps.shouldFold

/**
 * How often a reading is written to the database. The counter is cumulative, so a reading held back
 * loses nothing — the next one carries its steps — which is what lets a screen the user is looking
 * at be written on a floor of its own rather than on every event the sensor sends.
 */
class FoldCadenceTest {

    private val minute = BACKGROUND_FOLD_INTERVAL_MS
    private val second = FOREGROUND_FOLD_INTERVAL_MS / 2

    @Test
    fun `with a screen open a reading inside the short floor is held back`() {
        assertFalse(shouldFold(lastFoldMillis = 0L, now = 1L, screenOpen = true, paused = false))
        assertFalse(
            shouldFold(
                lastFoldMillis = 1_000L,
                now = 1_000L + FOREGROUND_FOLD_INTERVAL_MS - 1,
                screenOpen = true,
                paused = false,
            ),
        )
    }

    @Test
    fun `with a screen open the short floor is enough`() {
        assertTrue(
            shouldFold(
                lastFoldMillis = 1_000L,
                now = 1_000L + FOREGROUND_FOLD_INTERVAL_MS,
                screenOpen = true,
                paused = false,
            ),
        )
        assertTrue(shouldFold(lastFoldMillis = 1_000L, now = 1_000L + minute, screenOpen = true, paused = false))
    }

    @Test
    fun `an open screen is written far more often than none`() {
        // The same gap, either side of the two floors: what the screen is for is the number on it
        // growing as the user walks, and a minute of that is not a live count.
        val gap = 10 * second
        assertTrue(shouldFold(lastFoldMillis = 0L, now = gap, screenOpen = true, paused = false))
        assertFalse(shouldFold(lastFoldMillis = 0L, now = gap, screenOpen = false, paused = false))
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
        assertTrue(
            shouldFold(lastFoldMillis = 10 * minute, now = 10 * minute + 1, screenOpen = true, paused = true),
        )
    }

    @Test
    fun `the interval itself is long enough`() {
        assertTrue(shouldFold(lastFoldMillis = 10 * minute, now = 11 * minute, screenOpen = false, paused = false))
        assertTrue(shouldFold(lastFoldMillis = 10 * minute, now = 40 * minute, screenOpen = false, paused = false))
    }
}
