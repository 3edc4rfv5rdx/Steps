package xx.steps.ui

import xx.steps.HOURS_PER_DAY
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

/**
 * The stretch of the day the chart is showing, as fractions of it: [start] is where the left edge
 * falls, [width] how much of the day fits across. The whole day is (0, 1); pinching narrows the
 * width and pans the start.
 */
data class ChartView(val start: Float, val width: Float) {
    companion object {
        /** The whole day, which is what the chart opens at. */
        val WholeDay = ChartView(start = 0f, width = 1f)
    }
}

/**
 * Furthest the X axis can be stretched. Eight puts three hours across the chart, by which point a
 * quarter-hour bar is wide enough to hit with a finger; past that there is nothing left to gain.
 */
const val CHART_MAX_ZOOM = 8f

/**
 * The window after pinching by [zoom] around the screen fraction [focus] and panning by [pan],
 * itself a fraction of the chart's width.
 *
 * The moment under the fingers stays under them: that is what [focus] anchors. Panning is scaled by
 * the new width, so dragging the chart a third of the way across always moves it a third of what is
 * on screen, whatever the zoom.
 */
fun zoomedView(view: ChartView, zoom: Float, focus: Float, pan: Float): ChartView {
    val width = (view.width / zoom).coerceIn(1f / CHART_MAX_ZOOM, 1f)
    val anchor = view.start + focus.coerceIn(0f, 1f) * view.width
    val start = (anchor - focus.coerceIn(0f, 1f) * width - pan * width).coerceIn(0f, 1f - width)
    return ChartView(start, width)
}

/**
 * Hours to rule and label the chart at, for a window [viewWidth] of the day wide.
 *
 * Quarters of the day are right when the whole day is on screen and useless once it is stretched:
 * three hours across would carry no label at all. The step therefore follows the window, and always
 * divides 24, so midnight and the end of the day stay on it.
 */
fun gridHours(viewWidth: Float): List<Int> {
    val step = when {
        viewWidth <= 0.2f -> 1
        viewWidth <= 0.4f -> 2
        viewWidth <= 0.8f -> 3
        else -> 6
    }
    return (0..HOURS_PER_DAY step step).toList()
}
