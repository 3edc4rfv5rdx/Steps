package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Test
import xx.steps.steps.MAX_STEPS_PER_SECOND
import xx.steps.steps.STEP_WINDOW_SLACK_MS
import xx.steps.steps.SyncState
import xx.steps.steps.foldReading

/** The counting rule: turning cumulative sensor readings into steps credited to the current day. */
class StepSyncTest {

    /** Two hours of uptime — far enough in that the physical cap never binds by accident. */
    private val upFor2h = 2 * 60 * 60 * 1000L

    private val quarterHour = 15 * 60 * 1000L

    @Test
    fun `first reading only establishes the baseline`() {
        val outcome = foldReading(previous = null, rawCount = 12_345, uptimeMillis = upFor2h)

        // The counter has been running since before the app existed; crediting its accumulated
        // value would invent a day's worth of steps out of nothing.
        assertEquals(0, outcome.addedSteps)
        assertEquals(SyncState(12_345, upFor2h), outcome.newState)
    }

    @Test
    fun `ordinary growth counts the difference`() {
        val outcome = foldReading(
            SyncState(1_000, upFor2h),
            rawCount = 1_250,
            uptimeMillis = upFor2h + quarterHour,
        )

        assertEquals(250, outcome.addedSteps)
        assertEquals(SyncState(1_250, upFor2h + quarterHour), outcome.newState)
    }

    @Test
    fun `an unchanged counter credits nothing`() {
        val outcome = foldReading(
            SyncState(1_000, upFor2h),
            rawCount = 1_000,
            uptimeMillis = upFor2h + quarterHour,
        )

        assertEquals(0, outcome.addedSteps)
    }

    @Test
    fun `a lower counter means a reboot and credits the whole value`() {
        // The sensor restarts at zero on reboot, so 40 is 40 new steps, not a 960-step loss.
        val outcome = foldReading(SyncState(1_000, upFor2h), rawCount = 40, uptimeMillis = 30_000)

        assertEquals(40, outcome.addedSteps)
        assertEquals(SyncState(40, 30_000), outcome.newState)
    }

    @Test
    fun `uptime going backwards means a reboot even when the counter is higher`() {
        // Rebooted twice in a day: the fresh counter passed the pre-reboot reading, and only the
        // uptime restart gives it away. Counting the difference would lose the steps before it.
        val outcome = foldReading(
            SyncState(1_000, upFor2h),
            rawCount = 1_500,
            uptimeMillis = quarterHour,
        )

        assertEquals(1_500, outcome.addedSteps)
        assertEquals(SyncState(1_500, quarterHour), outcome.newState)
    }

    @Test
    fun `a counter surviving a reboot is capped by the time since boot`() {
        // Vendor firmware that keeps the counter across a reboot would otherwise dump its whole
        // lifetime total onto today. Fifteen minutes of uptime cannot hold three million steps.
        val outcome = foldReading(
            SyncState(1_000, upFor2h),
            rawCount = 3_000_000,
            uptimeMillis = quarterHour,
        )

        val cap = (quarterHour + STEP_WINDOW_SLACK_MS) / 1000L * MAX_STEPS_PER_SECOND
        assertEquals(cap.toInt(), outcome.addedSteps)
        // The baseline still moves to the reading, so the same steps are not offered again.
        assertEquals(SyncState(3_000_000, quarterHour), outcome.newState)
    }

    @Test
    fun `a sensor spike within one boot is capped too`() {
        val outcome = foldReading(
            SyncState(1_000, upFor2h),
            rawCount = 900_000,
            uptimeMillis = upFor2h + quarterHour,
        )

        val cap = (quarterHour + STEP_WINDOW_SLACK_MS) / 1000L * MAX_STEPS_PER_SECOND
        assertEquals(cap.toInt(), outcome.addedSteps)
    }

    @Test
    fun `batched readings milliseconds apart are not clipped`() {
        // Two events 40 ms apart still carry real steps: the sensor batches. Without the window
        // slack the cap would round to zero and those steps would be lost for good.
        val outcome = foldReading(
            SyncState(1_000, upFor2h),
            rawCount = 1_012,
            uptimeMillis = upFor2h + 40,
        )

        assertEquals(12, outcome.addedSteps)
    }

    @Test
    fun `a long quiet stretch still credits a plausible walk`() {
        // Doze can hold the worker off for hours; the steps taken meanwhile must all land.
        val eightHours = 8 * 60 * 60 * 1000L

        val outcome = foldReading(
            SyncState(1_000, upFor2h),
            rawCount = 13_000,
            uptimeMillis = upFor2h + eightHours,
        )

        assertEquals(12_000, outcome.addedSteps)
    }

    @Test
    fun `an absurd delta is capped instead of overflowing`() {
        val outcome = foldReading(SyncState(0, 0), rawCount = Long.MAX_VALUE, uptimeMillis = Long.MAX_VALUE / 2)

        // Both the physical cap and the Int ceiling apply; neither may wrap around.
        assertEquals(Int.MAX_VALUE, outcome.addedSteps)
    }
}
