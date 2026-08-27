package xx.steps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xx.steps.R
import xx.steps.data.StepsRepository
import xx.steps.formatDayLabel
import xx.steps.formatMonthName
import xx.steps.formatSteps
import xx.steps.settings.AppSettings
import java.time.LocalDate

/** Items the list renders before the tree starts — the totals card. Scrolling offsets by this. */
private const val HEADER_ITEMS = 1

/** Expandable year > month > day tree of past days, with period totals on top. */
@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val repository = remember(context) { StepsRepository.get(context) }
    val recorded by remember(repository) { repository.observeAll() }.collectAsState(initial = emptyList())

    // Re-read on the same tick the Today tab uses: the tree is the day list and does not move at
    // midnight, but which periods the totals cover and which row wears the band both do.
    val today by rememberCurrentDate()
    val years = remember(recorded) { buildHistoryTree(recorded) }
    val totals = remember(recorded, today) { historyTotals(recorded, today) }
    val stepLength by AppSettings.stepLengthCm.collectAsState()

    // Which nodes are open, by the tree's own stable keys, so rotation does not close the tree.
    val expanded = rememberSaveable(saver = stringListSaver) { mutableListOf<String>().toMutableStateList() }

    // The day whose hour-by-hour breakdown is open over the tree, if any.
    var opened by remember { mutableStateOf<LocalDate?>(null) }
    val listState = rememberLazyListState()
    // The command collector below runs for the life of the screen, so what it reads has to be read
    // through rememberUpdatedState: a tab left open across midnight must open onto the new today,
    // not the one that was current when the collector started.
    val currentYears by rememberUpdatedState(years)
    val currentToday by rememberUpdatedState(today)

    fun openToday() {
        val path = pathToDay(currentToday)
        expanded.addAll(path.dropLast(1).filterNot(expanded::contains))
    }

    // Open onto today's branch the first time there is anything to open.
    LaunchedEffect(years.isNotEmpty()) {
        if (years.isNotEmpty() && expanded.isEmpty()) openToday()
    }

    // The top bar owns the buttons; the tree owns the state, so taps arrive as commands.
    LaunchedEffect(Unit) {
        HistoryCommands.commands.collect { command ->
            when (command) {
                HistoryCommands.Command.COLLAPSE_ALL -> expanded.clear()
                HistoryCommands.Command.OPEN_TODAY -> {
                    openToday()
                    val index = visibleKeys(currentYears, expanded).indexOf(pathToDay(currentToday).last())
                    if (index >= 0) listState.animateScrollToItem(index + HEADER_ITEMS)
                }
            }
        }
    }

    // Back closes one level at a time before it leaves the screen.
    BackHandler(enabled = expanded.isNotEmpty()) {
        expanded.removeAt(expanded.lastIndex)
    }

    fun toggle(key: String) {
        if (!expanded.remove(key)) {
            expanded.add(key)
        } else {
            // Closing a node closes everything under it; keys are hierarchical, so a prefix does it.
            expanded.removeAll { it.startsWith("$key-") }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        item { TotalsCard(totals, stepLength) }

        if (years.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        years.forEach { year ->
            item(key = year.key) {
                TreeRow(
                    label = year.year.toString(),
                    steps = year.steps,
                    stepLengthCm = stepLength,
                    depth = 0,
                    expanded = year.key in expanded,
                    onClick = { toggle(year.key) },
                )
            }

            if (year.key in expanded) {
                year.months.forEach { month ->
                    item(key = month.key) {
                        TreeRow(
                            label = formatMonthName(month.month),
                            steps = month.steps,
                            stepLengthCm = stepLength,
                            depth = 1,
                            expanded = month.key in expanded,
                            onClick = { toggle(month.key) },
                        )
                    }

                    if (month.key in expanded) {
                        items(month.days, key = { it.key }) { day ->
                            DayRow(
                                day = day,
                                stepLengthCm = stepLength,
                                isToday = day.date == today,
                                onClick = { opened = day.date },
                            )
                        }
                    }
                }
            }
        }
    }

    opened?.let { date ->
        DayDetailDialog(date = date, onDismiss = { opened = null })
    }
}

/**
 * Week, month and year side by side as columns, with everything ever walked under a rule — the
 * shape BikeTracker's history card uses, so the two apps read the same way.
 */
@Composable
private fun TotalsCard(totals: HistoryTotals, stepLengthCm: Int) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TotalColumn(R.string.total_week, totals.week, stepLengthCm, Modifier.weight(1f))
                TotalColumn(R.string.total_month, totals.month, stepLengthCm, Modifier.weight(1f))
                TotalColumn(R.string.total_year, totals.year, stepLengthCm, Modifier.weight(1f))
            }

            // Full-contrast rule: the default hairline tint is invisible against the card.
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 10.dp),
                thickness = 2.dp,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.total_all),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatSteps(totals.allTime.steps) + " / " +
                        distanceLabel(totals.allTime.steps, stepLengthCm, decimals = 0),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TotalColumn(labelRes: Int, total: PeriodTotal, stepLengthCm: Int, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = formatSteps(total.steps),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = distanceLabel(total.steps, stepLengthCm, decimals = 0),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * "8 342 / 6 km" — steps first, as everywhere else in the app, then the distance they cover.
 * [emphasis] colours and bolds the step count for a day that met its goal.
 */
@Composable
private fun StepsAndDistance(
    steps: Int,
    stepLengthCm: Int,
    emphasis: Boolean = false,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    onBand: Boolean = false,
) {
    // On the inverted band the text takes the surface colour it is standing on top of; the green
    // "goal met" tint would not read against it.
    val plain = if (onBand) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatSteps(steps),
            style = style,
            fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasis && !onBand) GoalReachedGreen else plain,
        )
        Text(
            text = " / " + distanceLabel(steps, stepLengthCm, decimals = 0),
            style = style,
            color = plain,
        )
    }
}

/** A year or a month: chevron, name, steps, distance. Kept tight — the tree is a list to scan. */
@Composable
private fun TreeRow(
    label: String,
    steps: Int,
    stepLengthCm: Int,
    depth: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (depth * 16).dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        StepsAndDistance(steps = steps, stepLengthCm = stepLengthCm)
    }
}

/**
 * A single day. Its number goes green when that day met the goal it was walked against, and a tap
 * takes the day apart hour by hour.
 */
@Composable
private fun DayRow(day: DayNode, stepLengthCm: Int, isToday: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Clipped before it is filled, so the tap ripple keeps to the same rounded shape.
            .clip(RoundedCornerShape(6.dp))
            // Today is inverted: the row's own two colours swap places, so it reads as the same
            // row turned inside out — dark on light becomes light on dark, and the other way in
            // the dark theme.
            .background(color = if (isToday) MaterialTheme.colorScheme.onSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 46.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatDayLabel(day.date),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        StepsAndDistance(
            steps = day.steps,
            stepLengthCm = stepLengthCm,
            emphasis = day.reached,
            style = MaterialTheme.typography.bodyLarge,
            onBand = isToday,
        )
    }
}

/** Keeps the open-node keys across rotation; the list is small and holds plain strings. */
private val stringListSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)
