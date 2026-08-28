package xx.steps.steps

import xx.steps.MINUTES_PER_DAY
import xx.steps.SLOTS_PER_DAY
import xx.steps.SLOT_MINUTES

/**
 * Where the last reading left the hardware counter. The counter is cumulative since boot, so a
 * delta is only meaningful against the previous reading of the same boot — [lastUptimeMillis],
 * taken from `SystemClock.elapsedRealtime()`, is what says whether it *is* the same boot.
 */
data class SyncState(
    val lastRaw: Long,
    val lastUptimeMillis: Long,
)

/**
 * Result of folding one raw reading: steps to add to the current day, and the state to persist.
 *
 * [windowMillis] is the stretch of time those steps had to happen in — since the previous reading,
 * or since boot when the uptime clock itself restarted in between. The day total does not care, but the
 * intra-day breakdown spreads the steps over exactly this window, so it is decided here, next to
 * the reboot rule, rather than guessed at again by the caller.
 */
data class SyncOutcome(
    val addedSteps: Int,
    val newState: SyncState,
    val windowMillis: Long,
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
    if (previous == null) return SyncOutcome(0, newState, uptimeMillis)

    // Uptime runs from zero at every boot, so a value below the last one means the phone restarted
    // and the sensor restarted with it; a counter below its previous value says the same thing.
    val clockRestarted = uptimeMillis < previous.lastUptimeMillis
    val counterRestarted = rawCount < previous.lastRaw
    val rawDelta = if (clockRestarted || counterRestarted) rawCount else rawCount - previous.lastRaw

    // The two restarts are read apart on purpose. A counter that starts over while the clock keeps
    // running — a sensor-hub reset, or a reading that reached the transaction out of order — is a
    // fresh counter over the ordinary interval, not over the whole time since boot: only a clock
    // that restarted proves nothing else could have been measured from. Handing the window the
    // whole uptime there would raise the ceiling below to thousands of steps and smear them flat
    // across the day chart, which measures its spread back from the same number.
    val windowMillis = if (clockRestarted) uptimeMillis else uptimeMillis - previous.lastUptimeMillis

    // A counter that survives a reboot (some vendor firmware keeps it) would otherwise be read as
    // millions of fresh steps. Nobody outruns four steps a second, so the elapsed time is the
    // honest ceiling on any reading, whatever the sensor claims.
    // coerceAtLeast keeps the ceiling non-negative: an absurd uptime would overflow the sum, and a
    // negative ceiling would make coerceIn throw rather than clamp.
    val cap = ((windowMillis + STEP_WINDOW_SLACK_MS) / 1000L * MAX_STEPS_PER_SECOND).coerceAtLeast(0L)
    val credited = rawDelta.coerceIn(0L, minOf(cap, Int.MAX_VALUE.toLong()))

    return SyncOutcome(credited.toInt(), newState, windowMillis)
}

/** Steps landing in one slot of the day; the intra-day breakdown is written and read in these. */
data class SlotShare(val slot: Int, val steps: Int)

/**
 * Splits [steps] over the day's slots in proportion to [weights], one weight per slot.
 *
 * The shares add up to exactly [steps]: each slot takes the difference between two running totals
 * rather than its own rounded fraction, so nothing is lost or invented by rounding. Slots that
 * would take nothing are left out entirely.
 */
fun distributeOverSlots(steps: Int, weights: IntArray): List<SlotShare> {
    val total = weights.sumOf { it.toLong().coerceAtLeast(0L) }
    if (steps <= 0 || total <= 0L) return emptyList()

    val shares = ArrayList<SlotShare>()
    var running = 0L
    var placed = 0
    weights.forEachIndexed { slot, weight ->
        running += weight.coerceAtLeast(0)
        val cumulative = (running * steps / total).toInt()
        val share = cumulative - placed
        placed = cumulative
        if (share > 0) shares += SlotShare(slot, share)
    }
    return shares
}

/**
 * Spreads one reading's [steps] over the slots its interval covered, from [fromMinute] to
 * [toMinute] as minutes past midnight.
 *
 * The sensor gives no timing breakdown, so all that is known is that the steps happened somewhere
 * inside the interval between two readings; spreading them evenly across it is the only claim the
 * data supports. It also bounds the damage when Android defers the periodic sync: an hour-late
 * reading paints an even hour rather than a spike at the moment it happened.
 *
 * [fromMinute] is clipped to the start of the day, so a reading whose interval reaches back over
 * midnight keeps everything on the day it is credited to — the breakdown always adds up to the day.
 */
fun spreadOverSlots(steps: Int, fromMinute: Int, toMinute: Int): List<SlotShare> {
    if (steps <= 0) return emptyList()

    val end = toMinute.coerceIn(0, MINUTES_PER_DAY - 1)
    val start = fromMinute.coerceIn(0, end)
    // Two readings inside one minute say nothing about where in it the steps fell.
    if (start == end) return listOf(SlotShare(end / SLOT_MINUTES, steps))

    val weights = IntArray(SLOTS_PER_DAY) { slot ->
        val slotStart = slot * SLOT_MINUTES
        (minOf(slotStart + SLOT_MINUTES, end) - maxOf(slotStart, start)).coerceAtLeast(0)
    }
    return distributeOverSlots(steps, weights)
}
