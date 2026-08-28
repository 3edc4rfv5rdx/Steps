package xx.steps

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xx.steps.steps.DemoSteps
import xx.steps.steps.MAX_STEPS_PER_SECOND
import xx.steps.steps.STEP_WINDOW_SLACK_MS
import xx.steps.steps.SyncState
import xx.steps.steps.creditsSteps
import xx.steps.steps.foldReading

/**
 * Which folded readings add their steps to the day. Two of them only move the baseline: the one
 * taken while paused, and the first one a restarted demo produces.
 */
class CreditRuleTest {

    @Test
    fun `an ordinary reading is credited`() {
        assertTrue(creditsSteps(paused = false, demoFirstOfProcess = false))
    }

    @Test
    fun `a reading taken while paused is not`() {
        assertFalse(creditsSteps(paused = true, demoFirstOfProcess = false))
    }

    /**
     * The demo counter starts from the same constant in every process while the baseline it left
     * behind persists, so its first reading after a restart stands below the stored value —
     * indistinguishable from a reboot, and worth the whole fake baseline if it were credited.
     */
    @Test
    fun `a restarted demo credits nothing on its first reading`() = runBlocking {
        val previous = SyncState(lastRaw = 24_000L, lastUptimeMillis = 7_200_000L)
        val firstReading = DemoSteps.readings().first()

        // What the counting rule alone would make of it: a counter below the stored one reads as a
        // restart, so the whole fake baseline is offered. The cap clips it to the minute that
        // actually elapsed, which is still hundreds of steps out of nothing.
        val outcome = foldReading(previous, firstReading, uptimeMillis = 7_260_000L)
        val cap = (60_000L + STEP_WINDOW_SLACK_MS) / 1000L * MAX_STEPS_PER_SECOND
        assertEquals(cap.toInt(), outcome.addedSteps)
        assertTrue(outcome.addedSteps > 0)

        // Which is why that one reading is folded without credit — baseline only, as after install.
        assertFalse(creditsSteps(paused = false, demoFirstOfProcess = true))
    }

    @Test
    fun `the demo is credited again once one reading has been folded`() = runBlocking {
        val firstReading = DemoSteps.readings().first()
        val previous = SyncState(lastRaw = firstReading, lastUptimeMillis = 7_260_000L)

        val outcome = foldReading(previous, firstReading + 30L, uptimeMillis = 7_270_000L)
        assertEquals(30, outcome.addedSteps)
        assertTrue(creditsSteps(paused = false, demoFirstOfProcess = false))
    }
}
