package xx.steps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import xx.steps.MINUTES_PER_HOUR
import xx.steps.formatMinuteOfDay

/** Height of the bars themselves, and the band under them the hour labels are written in. */
private val PLOT_HEIGHT = 140.dp
private val LABEL_BAND = 26.dp

/** Share of a bar's slot taken by the bar; the rest is the gap to the next one. */
private const val BAR_WIDTH_SHARE = 0.72f

/** No bar is drawn thinner than this: at half-hour resolution a slot is only a few dp wide. */
private val MIN_BAR_WIDTH = 2.dp
private val BAR_CORNER = 2.dp

/** The pointer's dot, sitting on top of the bar it has picked. */
private val POINTER_DOT = 5.dp

/**
 * Hours the chart is ruled and labelled at — quarters of the day, enough to place a bar without
 * counting along. Midnight and the end of the day are among them, drawn just inside the edges.
 */
private val GRID_HOURS = listOf(0, 6, 12, 18, 24)

/** Width of an hour rule; the baseline and the pointer stay hairlines beside it. */
private val GRID_WIDTH = 1.5.dp

/**
 * The bar the pointer stands on. The bars cover the whole day edge to edge, so where the finger is
 * across the chart *is* the time of day — one fraction says both.
 */
fun bucketAt(buckets: List<DayBucket>, pointer: Float): Int =
    if (buckets.isEmpty()) 0 else (pointer.coerceIn(0f, 1f) * buckets.size).toInt().coerceIn(0, buckets.lastIndex)

/**
 * One day as bars from midnight to midnight, with a pointer that reads it.
 *
 * The pointer line follows the finger exactly, while its dot snaps to the top of the bar under it:
 * dragging slides along the day and the selection keeps up, a tap puts the pointer where it landed,
 * and lifting the finger leaves it there to be read. Only horizontal drags are taken, so the dialog
 * around the chart can still be scrolled.
 */
@Composable
fun DayChart(
    buckets: List<DayBucket>,
    pointer: Float,
    onPointer: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) return

    val measurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val pickedColor = MaterialTheme.colorScheme.primary
    val pointerColor = MaterialTheme.colorScheme.onSurface
    val gridColor = ChartGridAmber
    val baselineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val labelStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
    )

    val tallest = maxOf(buckets.maxOf { it.steps }, 1)
    val picked = bucketAt(buckets, pointer)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(PLOT_HEIGHT + LABEL_BAND)
            .pointerInput(buckets.size) {
                detectTapGestures { position -> onPointer(position.x / size.width) }
            }
            .pointerInput(buckets.size) {
                detectHorizontalDragGestures(
                    onDragStart = { position -> onPointer(position.x / size.width) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onPointer(change.position.x / size.width)
                    },
                )
            },
    ) {
        val plotHeight = size.height - LABEL_BAND.toPx()
        val slot = size.width / buckets.size
        val barWidth = maxOf(slot * BAR_WIDTH_SHARE, MIN_BAR_WIDTH.toPx())
        val corner = CornerRadius(BAR_CORNER.toPx())
        val hairline = 1.dp.toPx()

        val gridWidth = GRID_WIDTH.toPx()
        GRID_HOURS.forEach { hour ->
            // Midnight and midnight again sit on the very edge; pulled in by half a stroke, they
            // are drawn whole instead of half outside the chart.
            val x = hourX(hour, size.width).coerceIn(gridWidth / 2, size.width - gridWidth / 2)
            drawLine(gridColor, Offset(x, 0f), Offset(x, plotHeight), gridWidth)
        }
        drawLine(baselineColor, Offset(0f, plotHeight), Offset(size.width, plotHeight), hairline)

        fun barHeight(steps: Int): Float = plotHeight * steps / tallest

        buckets.forEachIndexed { index, bucket ->
            if (bucket.steps == 0) return@forEachIndexed
            val height = barHeight(bucket.steps)
            drawRoundRect(
                color = if (index == picked) pickedColor else barColor,
                topLeft = Offset(slot * index + (slot - barWidth) / 2, plotHeight - height),
                size = Size(barWidth, height),
                cornerRadius = corner,
            )
        }

        val pointerX = pointer.coerceIn(0f, 1f) * size.width
        drawLine(pointerColor, Offset(pointerX, 0f), Offset(pointerX, plotHeight), hairline)
        drawCircle(
            color = pointerColor,
            radius = POINTER_DOT.toPx(),
            center = Offset(pointerX, plotHeight - barHeight(buckets[picked].steps)),
        )

        GRID_HOURS.forEach { hour ->
            val text = measurer.measure(hourLabel(hour), labelStyle)
            val x = (hourX(hour, size.width) - text.size.width / 2f)
                .coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(x, plotHeight + hairline * 2))
        }
    }
}

/** Where an hour of the day falls across a chart [width] wide, midnight to midnight. */
private fun hourX(hour: Int, width: Float): Float = width * hour / GRID_HOURS.last()

/** "06" — the hour alone, since a full 06:00 five times over would not fit under the chart. */
private fun hourLabel(hour: Int): String =
    formatMinuteOfDay(hour * MINUTES_PER_HOUR).substringBefore(':')
