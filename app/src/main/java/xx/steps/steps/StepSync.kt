package xx.steps.steps

/**
 * Where the last reading left the hardware counter. The counter is cumulative since boot, so a
 * delta is only meaningful against the previous reading of the same boot — [lastUptimeMillis],
 * taken from `SystemClock.elapsedRealtime()`, is what says whether it *is* the same boot.
 */
data class SyncState(
    val lastRaw: Long,
    val lastUptimeMillis: Long,
)

/** Result of folding one raw reading: steps to add to the current day, and the state to persist. */
data class SyncOutcome(
    val addedSteps: Int,
    val newState: SyncState,
)

/**
 * Fastest anybody moves on foot, for the sanity cap below. Sprinters reach about five steps per
 * second; four is chosen to sit just under that, so no real walking is ever clipped.
 */
const val MAX_STEPS_PER_SECOND = 4

/**
 * Grace added to the cap's time window. The sensor batches events, so two readings can arrive
 * milliseconds apart carrying steps taken over a longer stretch; without slack the cap would clip
 * them and the steps would be lost for good, the baseline having moved on.
 */
const val STEP_WINDOW_SLACK_MS = 60_000L

/**
 * Turns a cumulative TYPE_STEP_COUNTER reading into the number of new steps since [previous].
 *
 * [uptimeMillis] is `SystemClock.elapsedRealtime()`: milliseconds since boot, including sleep. It
 * serves two purposes — a value below the previous one is proof of a reboot, and the interval
 * between readings bounds how many steps could physically have happened.
 *
 * Every new step is credited to the day the reading happens on. The sensor gives no timing
 * breakdown, so steps taken before midnight but read after it land on the new day; syncing every
 * quarter hour keeps that window short, and few steps are taken in it.
 *
 * With no previous state (first run, reinstall, cleared data) nothing is credited — the reading
 * only establishes the baseline, since the counter's accumulated value predates the app.
 */
fun foldReading(previous: SyncState?, rawCount: Long, uptimeMillis: Long): SyncOutcome {
    val newState = SyncState(rawCount, uptimeMillis)
    if (previous == null) return SyncOutcome(0, newState)

    // Uptime runs from zero at every boot, so a value below the last one means the phone restarted
    // and the sensor restarted with it; a counter below its previous value says the same thing.
    val rebooted = uptimeMillis < previous.lastUptimeMillis || rawCount < previous.lastRaw
    val rawDelta = if (rebooted) rawCount else rawCount - previous.lastRaw
    val windowMillis = if (rebooted) uptimeMillis else uptimeMillis - previous.lastUptimeMillis

    // A counter that survives a reboot (some vendor firmware keeps it) would otherwise be read as
    // millions of fresh steps. Nobody outruns four steps a second, so the elapsed time is the
    // honest ceiling on any reading, whatever the sensor claims.
    // coerceAtLeast keeps the ceiling non-negative: an absurd uptime would overflow the sum, and a
    // negative ceiling would make coerceIn throw rather than clamp.
    val cap = ((windowMillis + STEP_WINDOW_SLACK_MS) / 1000L * MAX_STEPS_PER_SECOND).coerceAtLeast(0L)
    val credited = rawDelta.coerceIn(0L, minOf(cap, Int.MAX_VALUE.toLong()))

    return SyncOutcome(credited.toInt(), newState)
}
