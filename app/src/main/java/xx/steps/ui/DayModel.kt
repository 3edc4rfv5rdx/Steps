package xx.steps.ui

import xx.steps.MINUTES_PER_DAY
import xx.steps.SLOTS_PER_DAY
import xx.steps.SLOT_MINUTES
import xx.steps.data.DaySlot

/**
 * The day chart's bars and the figures under it, kept free of Compose so both are covered by plain
 * JVM tests — the same split the history tree uses. The screen only draws what these return.
 */

/** One bar: where in the day it starts, how wide it is in minutes, and the steps inside it. */
data class DayBucket(val startMinute: Int, val minutes: Int, val steps: Int) {
    val endMinute: Int get() = startMinute + minutes
}

/**
 * Every bar of the day at [bucketMinutes] resolution, midnight first, gaps filled with zeroes: the
 * chart spans a whole day whatever was walked in it, so the shape of one day compares with another.
 *
 * [bucketMinutes] is meant to be a whole number of stored slots ([SLOT_MINUTES]) that divides the
 * day; one that does not still returns a whole chart, with the remainder folded into the last bar.
 */
fun buildDayBuckets(slots: List<DaySlot>, bucketMinutes: Int): List<DayBucket> {
    val width = bucketMinutes.coerceIn(SLOT_MINUTES, MINUTES_PER_DAY)
    val steps = IntArray(MINUTES_PER_DAY / width)

    slots.forEach { slot ->
        // A slot number out of range can only come from a hand-edited database; drop it rather
        // than let it decide the size of the chart. A width that does not divide the day leaves a
        // remainder with no bar of its own, which the last bar takes rather than the array end.
        if (slot.slot in 0 until SLOTS_PER_DAY) {
            steps[(slot.slot * SLOT_MINUTES / width).coerceAtMost(steps.lastIndex)] += slot.steps
        }
    }

    return steps.mapIndexed { index, inBucket ->
        DayBucket(startMinute = index * width, minutes = width, steps = inBucket)
    }
}

/**
 * What the dialog says about the day underneath the chart. Every figure comes from the breakdown
 * itself, so it always describes the bars being looked at.
 *
 * [peak] is the busiest bar, [firstMinute] and [lastMinute] the edges of the walking day. All three
 * are null for a day with no breakdown at all — days walked before the app started recording one.
 */
data class DayStats(
    val steps: Int,
    val peak: DayBucket?,
    val firstMinute: Int?,
    val lastMinute: Int?,
)

fun dayStats(buckets: List<DayBucket>): DayStats {
    val walked = buckets.filter { it.steps > 0 }
    return DayStats(
        steps = buckets.sumOf { it.steps },
        peak = walked.maxByOrNull { it.steps },
        firstMinute = walked.firstOrNull()?.startMinute,
        lastMinute = walked.lastOrNull()?.endMinute,
    )
}
