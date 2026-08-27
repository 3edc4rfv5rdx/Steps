package xx.steps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
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

/** The dashed goal line: thick enough to read as a rule across the week, not as a hairline. */
private val GOAL_LINE_WIDTH = 1.5.dp

/** The rule under today's label: how far below the baseline it sits, and how thick it is. */
private val UNDERLINE_GAP = 7.dp
private val UNDERLINE_THICKNESS = 2.dp

/** Share of a day's slot taken by its bar; the rest is the gap to the next one. */
private const val BAR_WIDTH_SHARE = 0.55f

/**
 * The past week as bars, with the goal drawn across them as a dashed line.
 *
 * Bars are scaled against the taller of the best day and the goal, so the goal line stays on the
 * chart even in a week where it was never met — otherwise it would sit off the top edge and the
 * week would look complete.
 *
 * A tap anywhere in a day's column opens that day, [onDayClick]: the columns divide the chart
 * between them, so there is nowhere in it that belongs to no day.
 *
 * [today] is passed in rather than read here: the screen already re-reads the date every minute so
 * it survives midnight, and a second reading of it would let the bar and the label above the ring
 * disagree about which day it is.
 */
@Composable
fun WeekBars(
    days: List<DayBar>,
    goalLine: Int,
    today: LocalDate,
    onDayClick: (DayBar) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return

    val scale = maxOf(days.maxOf { it.steps }, goalLine, 1)
    val reachedColor = GoalReachedGreen
    // Every day of the week is the full accent. Today is told apart by its label, set in bold
    // below the bar, the same way the labels themselves do it: weight, not a faded colour.
    val barColor = MaterialTheme.colorScheme.primary
    val underlineColor = MaterialTheme.colorScheme.onSurface
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CHART_LINE_ALPHA)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(BARS_HEIGHT)
                .pointerInput(days) {
                    detectTapGestures { position ->
                        val index = (position.x / (size.width.toFloat() / days.size)).toInt()
                        onDayClick(days[index.coerceIn(0, days.lastIndex)])
                    }
                },
        ) {
            val slot = size.width / days.size
            val barWidth = slot * BAR_WIDTH_SHARE
            val corner = CornerRadius(BAR_CORNER.toPx())

            days.forEachIndexed { index, day ->
                val barHeight = size.height * (day.steps.toFloat() / scale)
                val left = slot * index + (slot - barWidth) / 2
                val color = if (day.goal > 0 && day.steps >= day.goal) reachedColor else barColor
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
                strokeWidth = GOAL_LINE_WIDTH.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }

        // The bottom padding is the room the underline hangs in: it is drawn below the label's
        // own box, and without it the rule would land on whatever the screen puts underneath.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = UNDERLINE_GAP + UNDERLINE_THICKNESS),
        ) {
            days.forEach { day ->
                val isToday = day.date == today
                // The rule under today's label is drawn rather than asked of the font:
                // TextDecoration.Underline sets its own thickness and sits tight under the
                // letters, and this one is thicker and lower. It runs the width of the text, so
                // the layout is untouched — a fill around the label would widen it enough to wrap
                // inside a seventh of the width.
                var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    // Weight and a rule, not colour: every label carries the same onSurface, and a
                    // dimmed one would be the grey-on-grey this app does not use.
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    onTextLayout = { layout = it },
                    modifier = Modifier
                        .weight(1f)
                        .drawBehind {
                            val line = layout?.takeIf { isToday } ?: return@drawBehind
                            val y = line.firstBaseline + UNDERLINE_GAP.toPx()
                            drawLine(
                                color = underlineColor,
                                start = Offset(line.getLineLeft(0), y),
                                end = Offset(line.getLineRight(0), y),
                                strokeWidth = UNDERLINE_THICKNESS.toPx(),
                            )
                        },
                )
            }
        }
    }
}
