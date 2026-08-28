package xx.steps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.annotation.StringRes
import xx.steps.CHART_BUCKET_MINUTES
import xx.steps.HOURS_PER_DAY
import xx.steps.MINUTES_PER_DAY
import xx.steps.MINUTES_PER_HOUR
import xx.steps.R
import xx.steps.SLOT_MINUTES
import xx.steps.formatMinuteOfDay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** Height of the bars themselves, and the band under them the hour labels are written in. */
private val PLOT_HEIGHT = 190.dp
private val LABEL_BAND = 26.dp

/** Share of a bar's slot taken by the bar; the rest is the gap to the next one. */
private const val BAR_WIDTH_SHARE = 0.88f

/** No bar is drawn thinner than this: at quarter-hour resolution a slot is only a few dp wide. */
private val MIN_BAR_WIDTH = 3.dp
private val BAR_CORNER = 2.dp

/** The pointer's dot, sitting on top of the bar it has picked. */
private val POINTER_DOT = 5.dp


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
 * The pointer stands on the middle of the bar under the finger, its dot on that bar's top: it is
 * a whole bar that is read out, so it moves from one to the next rather than sliding between them.
 * Dragging walks it along the day, a tap puts it on the bar that was hit, and lifting the finger
 * leaves it there to be read. Only horizontal drags are taken, so the dialog around the chart can
 * still be scrolled.
 *
 * [onTouch] fires on every touch that lands on the chart, whatever it turns into — the dialog uses
 * it to fold the controls away.
 */
@Composable
fun DayChart(
    buckets: List<DayBucket>,
    pointer: Float,
    onPointer: (Float) -> Unit,
    view: ChartView,
    onView: (ChartView) -> Unit,
    modifier: Modifier = Modifier,
    onTouch: () -> Unit = {},
) {
    if (buckets.isEmpty()) return

    // The gesture below outlives the composition that started it, so it reads these rather than
    // the values captured when it was set up — otherwise a pinch would keep zooming from where the
    // window stood when the finger landed.
    val haptic = LocalHapticFeedback.current
    val currentView by rememberUpdatedState(view)
    val onViewNow by rememberUpdatedState(onView)
    val onPointerNow by rememberUpdatedState(onPointer)
    val onTouchNow by rememberUpdatedState(onTouch)

    val measurer = rememberTextMeasurer()
    // Every bar is the full accent, as in the week. Which stretch is picked is said by the pointer
    // standing on it — a line and a dot on the bar's top — not by dimming the rest of the day.
    val barColor = MaterialTheme.colorScheme.primary
    val pointerColor = MaterialTheme.colorScheme.onSurface
    val gridColor = ChartGridAmber
    val baselineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CHART_LINE_ALPHA)
    val labelStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
    )

    val tallest = maxOf(buckets.maxOf { it.steps }, 1)
    val picked = bucketAt(buckets, pointer)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(PLOT_HEIGHT + LABEL_BAND)
            // One gesture handler for all three readings of a touch, because they have to agree
            // on which is happening: a tap places the pointer, one finger dragged scrubs along the
            // day, and a second finger turns the whole thing into a pinch. Split across separate
            // detectors, the drag would scrub while the other hand was still zooming.
            .pointerInput(buckets.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Reaching for the chart is the same as putting the controls away: they sit
                    // over the date, and whoever is touching the bars is done with them.
                    onTouchNow()
                    val slop = viewConfiguration.touchSlop
                    var mode = ChartGesture.UNDECIDED
                    var last = down.position

                    /** Screen x to a fraction of the day, through whatever window is showing. */
                    fun dayAt(x: Float): Float =
                        (currentView.start + (x / size.width) * currentView.width).coerceIn(0f, 1f)

                    // Which gesture this is has to be settled before the long-press timeout,
                    // because running out of time is itself one of the answers: a finger that
                    // neither moved nor was joined by a second one is asking to drag the window.
                    val settled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (mode == ChartGesture.UNDECIDED) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            when {
                                pressed.size >= 2 -> mode = ChartGesture.PINCH
                                // Lifted already: a tap, placed after the loop.
                                pressed.isEmpty() -> return@withTimeoutOrNull Unit
                                else -> {
                                    val change = pressed.first()
                                    last = change.position
                                    if (abs(change.position.x - down.position.x) > slop) {
                                        mode = ChartGesture.SCRUB
                                        onPointerNow(dayAt(change.position.x))
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
                    if (settled == null) {
                        // Held still: hand the finger over to the window, and say so by touch,
                        // since nothing on screen has moved yet to show what changed.
                        mode = ChartGesture.PAN
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    if (mode == ChartGesture.UNDECIDED) {
                        onPointerNow(dayAt(last.x))
                        return@awaitEachGesture
                    }

                    var panFrom = last.x
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) mode = ChartGesture.PINCH

                        when (mode) {
                            ChartGesture.PINCH -> {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoom != 1f || pan.x != 0f) {
                                    val focus = event.calculateCentroid().x / size.width
                                    onViewNow(
                                        zoomedView(currentView, zoom, focus, pan.x / size.width),
                                    )
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }

                            ChartGesture.SCRUB -> {
                                val change = pressed.first()
                                if (change.positionChanged()) {
                                    onPointerNow(dayAt(change.position.x))
                                    change.consume()
                                }
                            }

                            // Dragging the window itself: the chart follows the finger, so
                            // pushing right walks back towards the start of the day.
                            ChartGesture.PAN -> {
                                val change = pressed.first()
                                if (change.positionChanged()) {
                                    val dx = change.position.x - panFrom
                                    panFrom = change.position.x
                                    onViewNow(
                                        zoomedView(
                                            currentView,
                                            zoom = 1f,
                                            focus = 0.5f,
                                            pan = dx / size.width,
                                        ),
                                    )
                                    change.consume()
                                }
                            }

                            // Settled above; nothing reaches here undecided.
                            ChartGesture.UNDECIDED -> Unit
                        }
                    }
                }
            },
    ) {
        val plotHeight = size.height - LABEL_BAND.toPx()

        /** Where a fraction of the day falls across the chart, through the window on show. */
        fun xOf(dayFraction: Float): Float = (dayFraction - view.start) / view.width * size.width

        /** The same, for a whole hour of the day. */
        fun hourX(hour: Int): Float = xOf(hour.toFloat() / HOURS_PER_DAY)

        // A slot is as wide as the window makes it: zoomed in, the same bars simply spread out.
        val slot = size.width / (buckets.size * view.width)
        val barWidth = maxOf(slot * BAR_WIDTH_SHARE, MIN_BAR_WIDTH.toPx())
        val corner = CornerRadius(BAR_CORNER.toPx())
        val hairline = 1.dp.toPx()
        val hours = gridHours(view.width)

        val gridWidth = GRID_WIDTH.toPx()
        hours.forEach { hour ->
            // Midnight and midnight again sit on the very edge; pulled in by half a stroke, they
            // are drawn whole instead of half outside the chart.
            val x = hourX(hour).coerceIn(gridWidth / 2, size.width - gridWidth / 2)
            drawLine(gridColor, Offset(x, 0f), Offset(x, plotHeight), gridWidth)
        }
        drawLine(baselineColor, Offset(0f, plotHeight), Offset(size.width, plotHeight), hairline)

        fun barHeight(steps: Int): Float = plotHeight * steps / tallest

        buckets.forEach { bucket ->
            if (bucket.steps == 0) return@forEach
            val height = barHeight(bucket.steps)
            val left = xOf(bucket.startMinute.toFloat() / MINUTES_PER_DAY) + (slot - barWidth) / 2
            // Off either edge of the window: nothing to draw, and at full zoom most bars are.
            if (left + barWidth < 0f || left > size.width) return@forEach
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, plotHeight - height),
                size = Size(barWidth, height),
                cornerRadius = corner,
            )
        }

        // Snapped to the middle of the bar it has picked: the readout names one bar, so a line
        // standing between two of them would name a stretch of the day it is not reading.
        val pointerX = xOf((picked + 0.5f) / buckets.size)
        drawLine(pointerColor, Offset(pointerX, 0f), Offset(pointerX, plotHeight), hairline)
        drawCircle(
            color = pointerColor,
            radius = POINTER_DOT.toPx(),
            center = Offset(pointerX, plotHeight - barHeight(buckets[picked].steps)),
        )

        hours.forEach { hour ->
            val ruleX = hourX(hour)
            // Zoomed in, most of the day's hours are off screen; only the ones on it are written.
            if (ruleX < 0f || ruleX > size.width) return@forEach
            val text = measurer.measure(hourLabel(hour), labelStyle)
            val x = (ruleX - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(x, plotHeight + hairline * 2))
        }
    }
}


/** "06" — the hour alone, since a full 06:00 five times over would not fit under the chart. */
private fun hourLabel(hour: Int): String =
    formatMinuteOfDay(hour * MINUTES_PER_HOUR).substringBefore(':')

/**
 * Which reading of a touch on the chart won: place the pointer, scrub along the day, drag the
 * window along it, or pinch it open.
 */
private enum class ChartGesture { UNDECIDED, SCRUB, PINCH, PAN }

/**
 * How much one tap of + or - stretches the axis. Gentler than a pinch on purpose: the buttons are
 * for arriving somewhere exactly, which takes more than one step whatever the size of it.
 */
const val BUTTON_ZOOM_STEP = 1.5f

/**
 * How far one tap of the arrows walks the window, as a share of what is on screen. Half a window
 * keeps a landmark from the previous view in sight, which is what says where the step landed.
 */
const val BUTTON_PAN_STEP = 0.5f

/**
 * One control from the strip the ⋮ button unfolds. Filled, like every other button in the app —
 * an outlined one on a dialog's surface is the grey-on-grey this app does not use.
 */
@Composable
fun ChartMenuButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(MENU_BUTTON_SIZE),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        ),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(MENU_ICON_SIZE))
    }
}

/**
 * Sized to be hit without aiming, and no larger: five of these and the gaps between them have to
 * fit across a dialog's title, and a Row that runs out of room squeezes the ⋮ at the end of it.
 */
private val MENU_BUTTON_SIZE = 44.dp
private val MENU_ICON_SIZE = 24.dp

/**
 * The bar width in force, and the menu that changes it. It stands in the dialog's button row beside
 * Close rather than on a row of its own under the chart: three buttons cost the dialog a whole
 * line, and the bars are worth more than that line was.
 */
@Composable
fun ChartBucketMenu(selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(
            onClick = { open = true },
            contentPadding = BUCKET_BUTTON_PADDING,
        ) {
            Text(stringResource(bucketLabel(selected)), maxLines = 1)
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = stringResource(R.string.chart_bucket),
                modifier = Modifier.size(MENU_ICON_SIZE),
            )
        }
        if (open) {
            // Material's own DropdownMenu drops downwards whenever there is room below the anchor,
            // and below this button there is the whole rest of the screen — the menu would cover
            // the Close button standing next to it. A popup of its own, told where to go, is what
            // keeps the list above the line it belongs to.
            val gap = with(LocalDensity.current) { MENU_GAP.roundToPx() }
            Popup(
                popupPositionProvider = remember(gap) { AboveAnchor(gap) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                // The stock menu fill is the container tone the dialog under it already uses, so
                // the menu would be a shadow's worth of difference from its background. A wash of
                // the accent over that fill, and a line of the accent around it, tell the two
                // apart in either theme without a colour of their own.
                val accent = MaterialTheme.colorScheme.primary
                Surface(
                    shape = MenuDefaults.shape,
                    color = accent.copy(alpha = MENU_TINT).compositeOver(MenuDefaults.containerColor),
                    tonalElevation = MenuDefaults.TonalElevation,
                    shadowElevation = MenuDefaults.ShadowElevation,
                    modifier = Modifier.border(
                        width = MENU_BORDER,
                        color = accent.copy(alpha = MENU_BORDER_TINT),
                        shape = MenuDefaults.shape,
                    ),
                ) {
                    // A menu item fills the width it is given, and the popup is given the whole
                    // window — so the width has to come from the widest label instead.
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .padding(vertical = MENU_EDGE),
                    ) {
                        CHART_BUCKET_MINUTES.forEach { minutes ->
                            DropdownMenuItem(
                                text = {
                                    // The width in force is named in the accent colour rather than
                                    // by a tick beside it: the menu is three lines long, and a
                                    // colour is read faster.
                                    Text(
                                        text = stringResource(bucketLabel(minutes)),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (minutes == selected) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        color = if (minutes == selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                },
                                onClick = {
                                    onSelect(minutes)
                                    open = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The menu clears the button by this much, and keeps the same distance inside its own edges. */
private val MENU_GAP = 4.dp
private val MENU_EDGE = 8.dp

/** How much of the accent goes into the menu's fill, and into the line around it. */
private const val MENU_TINT = 0.14f
private const val MENU_BORDER_TINT = 0.45f
private val MENU_BORDER = 1.dp

/**
 * Puts a popup directly above whatever opened it, left edges aligned, pushed back onto the screen
 * if it would hang off either end of it.
 */
private class AboveAnchor(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = anchorBounds.left.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width)),
        y = (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(0),
    )
}

/** The label carries its own trailing arrow, so the room on its right is the arrow's, not padding. */
private val BUCKET_BUTTON_PADDING = PaddingValues(start = 14.dp, end = 4.dp)

/** The name of a bar width: an hour, half of one, or the quarter hour the steps are recorded in. */
@StringRes
private fun bucketLabel(minutes: Int): Int = when {
    minutes >= MINUTES_PER_HOUR -> R.string.chart_hour
    minutes > SLOT_MINUTES -> R.string.chart_half_hour
    else -> R.string.chart_quarter_hour
}
