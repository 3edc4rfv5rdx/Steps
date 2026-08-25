package xx.steps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xx.steps.data.DaySteps
import xx.steps.toIso
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** One bar: a day, what was walked, and the goal that day was judged by. */
data class DayBar(
    val date: LocalDate,
    val steps: Int,
    val goal: Int,
)

/**
 * One bar per day of the week beginning at [first], filling the gaps: a day with no steps has no
 * row at all, and days still to come have none yet, but the chart shows all seven either way. A
 * filled-in day carries [currentGoal], since no goal was ever pinned for it.
 */
fun buildWeekBars(
    recorded: List<DaySteps>,
    first: LocalDate,
    days: Int,
    currentGoal: Int,
): List<DayBar> {
    val byDate = recorded.associateBy { it.date }
    return (0 until days).map { offset ->
        val date = first.plusDays(offset.toLong())
        val row = byDate[date.toIso()]
        DayBar(date = date, steps = row?.steps ?: 0, goal = row?.goal ?: currentGoal)
    }
}

private val BARS_HEIGHT = 96.dp
private val BAR_CORNER = 4.dp

/** Share of a day's slot taken by its bar; the rest is the gap to the next one. */
private const val BAR_WIDTH_SHARE = 0.55f

/**
 * The past week as bars, with the goal drawn across them as a dashed line.
 *
 * Bars are scaled against the taller of the best day and the goal, so the goal line stays on the
 * chart even in a week where it was never met — otherwise it would sit off the top edge and the
 * week would look complete.
 */
@Composable
fun WeekBars(days: List<DayBar>, goalLine: Int, modifier: Modifier = Modifier) {
    if (days.isEmpty()) return

    val scale = maxOf(days.maxOf { it.steps }, goalLine, 1)
    val reachedColor = GoalReachedGreen
    val barColor = MaterialTheme.colorScheme.primary
    val dimmedBar = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val today = LocalDate.now()

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(BARS_HEIGHT)) {
            val slot = size.width / days.size
            val barWidth = slot * BAR_WIDTH_SHARE
            val corner = CornerRadius(BAR_CORNER.toPx())

            days.forEachIndexed { index, day ->
                val barHeight = size.height * (day.steps.toFloat() / scale)
                val left = slot * index + (slot - barWidth) / 2
                val color = when {
                    day.goal > 0 && day.steps >= day.goal -> reachedColor
                    day.date == today -> barColor
                    else -> dimmedBar
                }
                // A day with no steps still gets a hairline, so the week reads as seven days.
                val drawnHeight = maxOf(barHeight, 2.dp.toPx())

                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, size.height - drawnHeight),
                    size = Size(barWidth, drawnHeight),
                    cornerRadius = corner,
                )
            }

            val goalY = size.height * (1f - (goalLine.toFloat() / scale))
            drawLine(
                color = lineColor,
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            days.forEach { day ->
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = if (day.date == today) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
