package xx.steps.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xx.steps.CHART_BUCKET_MINUTES
import xx.steps.DEFAULT_CHART_BUCKET_MINUTES
import xx.steps.MINUTES_PER_DAY
import xx.steps.MINUTES_PER_HOUR
import xx.steps.R
import xx.steps.SECONDS_PER_MINUTE
import xx.steps.SLOT_MINUTES
import xx.steps.data.StepsRepository
import xx.steps.formatDayLabel
import xx.steps.formatMinuteOfDay
import xx.steps.formatSteps
import xx.steps.percentOfGoal
import xx.steps.settings.AppSettings
import java.time.LocalDate
import java.time.LocalTime

/**
 * One day taken apart: its steps hour by hour, or half hour by half hour, with a pointer to read
 * the chart with and the day's figures under it.
 *
 * Everything is observed rather than passed in, so the dialog opened on today keeps counting while
 * it is up. A day walked before the app recorded a breakdown has bars of zero and says so — its
 * total is still true, and still shown.
 */
@Composable
fun DayDetailDialog(date: LocalDate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { StepsRepository.get(context) }

    val currentGoal by AppSettings.goal.collectAsState()
    val stepLength by AppSettings.stepLengthCm.collectAsState()
    val day by remember(repository, date) { repository.observeDay(date) }
        .collectAsState(initial = null)
    val slots by remember(repository, date) { repository.observeSlots(date) }
        .collectAsState(initial = emptyList())

    var bucketMinutes by rememberSaveable { mutableIntStateOf(DEFAULT_CHART_BUCKET_MINUTES) }
    // Where the pointer stands, as a fraction of the day — a time, so changing the bar width keeps
    // it in place. Below zero means "not placed yet", filled in once there is a day to place it on.
    var pointer by rememberSaveable { mutableFloatStateOf(UNPLACED_POINTER) }
    // The stretch of the day on show, held here rather than in the chart so that changing the bar
    // width does not throw a zoom away. Kept as two numbers because that is what survives a
    // rememberSaveable without a Saver of its own.
    var viewStart by rememberSaveable { mutableFloatStateOf(ChartView.WholeDay.start) }
    var viewWidth by rememberSaveable { mutableFloatStateOf(ChartView.WholeDay.width) }
    var controlsOpen by rememberSaveable { mutableStateOf(false) }

    fun moveView(zoom: Float, pan: Float) {
        // Zooming is anchored on the pointer, so the moment being read stays put as the axis
        // stretches; panning has no such anchor and takes the middle.
        val focus = if (zoom == 1f) {
            0.5f
        } else {
            ((pointer.coerceAtLeast(0f) - viewStart) / viewWidth).coerceIn(0f, 1f)
        }
        val next = zoomedView(ChartView(viewStart, viewWidth), zoom, focus, pan)
        viewStart = next.start
        viewWidth = next.width
    }

    val buckets = remember(slots, bucketMinutes) { buildDayBuckets(slots, bucketMinutes) }
    val stats = remember(buckets) { dayStats(buckets) }
    val picked = buckets.getOrNull(bucketAt(buckets, pointer))

    LaunchedEffect(date, stats.peak?.startMinute) {
        if (pointer >= 0f) return@LaunchedEffect
        val minute = when {
            date == LocalDate.now() -> LocalTime.now().toSecondOfDay() / SECONDS_PER_MINUTE
            // On a past day the busiest stretch is what the day is about; start the reading there.
            stats.peak != null -> stats.peak.startMinute + stats.peak.minutes / 2
            else -> return@LaunchedEffect
        }
        pointer = minute.toFloat() / MINUTES_PER_DAY
    }

    val goal = day?.goal ?: currentGoal
    val totalSteps = day?.steps ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            // The controls unfold over the date rather than beside it. Sharing the row squeezed the
            // date into three lines the moment they appeared, and the whole dialog jumped; laid on
            // top, they cost no height at all. The strip carries the dialog's own background so the
            // date does not show through it.
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatDayLabel(date),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = MENU_RESERVE),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .background(
                            color = AlertDialogDefaults.containerColor,
                            shape = RoundedCornerShape(percent = 50),
                        )
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                if (controlsOpen) {
                    ChartMenuButton(
                        icon = Icons.Filled.Add,
                        label = stringResource(R.string.chart_zoom_in),
                    ) { moveView(zoom = BUTTON_ZOOM_STEP, pan = 0f) }
                    Spacer(modifier = Modifier.width(CONTROL_GAP))
                    ChartMenuButton(
                        icon = Icons.Filled.Remove,
                        label = stringResource(R.string.chart_zoom_out),
                    ) { moveView(zoom = 1f / BUTTON_ZOOM_STEP, pan = 0f) }
                    Spacer(modifier = Modifier.width(CONTROL_GAP))
                    ChartMenuButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = stringResource(R.string.chart_earlier),
                    ) { moveView(zoom = 1f, pan = BUTTON_PAN_STEP) }
                    Spacer(modifier = Modifier.width(CONTROL_GAP))
                    ChartMenuButton(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        label = stringResource(R.string.chart_later),
                    ) { moveView(zoom = 1f, pan = -BUTTON_PAN_STEP) }
                    Spacer(modifier = Modifier.width(CONTROL_GAP))
                }
                ChartMenuButton(
                    icon = Icons.Filled.MoreVert,
                    label = stringResource(R.string.chart_controls),
                    // Always in the accent colour, open or shut: it is the only hint that the chart
                    // has controls at all, and a dim button on a title line is not one. Whether the
                    // strip is open is said by the strip being there.
                    active = true,
                ) { controlsOpen = !controlsOpen }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BucketChoice(selected = bucketMinutes, onSelect = { bucketMinutes = it })
                Spacer(modifier = Modifier.height(12.dp))

                PointerReadout(bucket = picked, hasBreakdown = stats.steps > 0)
                DayChart(
                    buckets = buckets,
                    pointer = pointer.coerceAtLeast(0f),
                    onPointer = { pointer = it },
                    view = ChartView(viewStart, viewWidth),
                    onView = { viewStart = it.start; viewWidth = it.width },
                    onTouch = { controlsOpen = false },
                )

                Spacer(modifier = Modifier.height(12.dp))
                StatRow(
                    label = stringResource(R.string.stat_total),
                    value = formatSteps(totalSteps) + " / " + distanceLabel(totalSteps, stepLength),
                )
                StatRow(
                    label = stringResource(R.string.goal_label),
                    value = formatSteps(goal) + " · " +
                        stringResource(R.string.percent_of_goal, percentOfGoal(totalSteps, goal)),
                )
                stats.peak?.let { peak ->
                    StatRow(
                        label = stringResource(R.string.stat_peak),
                        value = timeRange(peak.startMinute, peak.endMinute) + " · " + formatSteps(peak.steps),
                    )
                }
                if (stats.firstMinute != null && stats.lastMinute != null) {
                    StatRow(
                        label = stringResource(R.string.stat_active),
                        value = timeRange(stats.firstMinute, stats.lastMinute),
                    )
                }
            }
        },
        confirmButton = { DialogConfirmButton(stringResource(R.string.close), onDismiss) },
    )
}

/** Pointer position meaning "nothing chosen yet"; a real one is a fraction of the day, 0 to 1. */
private const val UNPLACED_POINTER = -1f

/** How wide a bar is. The width in force is the accent button, the others are tonal beside it. */
@Composable
private fun BucketChoice(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        CHART_BUCKET_MINUTES.forEach { minutes ->
            val label = stringResource(bucketLabel(minutes))
            if (minutes == selected) {
                DialogConfirmButton(label) { onSelect(minutes) }
            } else {
                DialogDismissButton(label) { onSelect(minutes) }
            }
        }
    }
}

/** The name of a bar width: an hour, half of one, or the quarter hour the steps are recorded in. */
@StringRes
private fun bucketLabel(minutes: Int): Int = when {
    minutes >= MINUTES_PER_HOUR -> R.string.chart_hour
    minutes > SLOT_MINUTES -> R.string.chart_half_hour
    else -> R.string.chart_quarter_hour
}

/** What the pointer is standing on: the stretch of the day, and what was walked in it. */
@Composable
private fun PointerReadout(bucket: DayBucket?, hasBreakdown: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A day recorded before the app kept a breakdown says so in the pointer's place: there is
        // nothing to point at, and an empty chart on its own would read as a day spent sitting.
        Text(
            text = when {
                !hasBreakdown -> stringResource(R.string.day_no_breakdown)
                bucket == null -> ""
                else -> timeRange(bucket.startMinute, bucket.endMinute)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (hasBreakdown && bucket != null) formatSteps(bucket.steps) else "",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A figure about the day: what it is on the left, what it says on the right. */
@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** "07:00 – 08:00" — the en dash and its spaces are punctuation, so they belong in code. */
private fun timeRange(fromMinute: Int, toMinute: Int): String =
    formatMinuteOfDay(fromMinute) + " – " + formatMinuteOfDay(toMinute)

/** Gap between the fold-out controls, wide enough that neighbours are not caught by a thumb. */
private val CONTROL_GAP = 12.dp

/** Room kept clear at the end of the date, so the shut ⋮ button never sits on the text. */
private val MENU_RESERVE = 56.dp
