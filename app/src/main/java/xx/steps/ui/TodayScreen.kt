package xx.steps.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xx.steps.R
import xx.steps.WEEK_DAYS
import xx.steps.startOfWeek
import xx.steps.data.StepsRepository
import xx.steps.formatDayLabel
import xx.steps.settings.AppSettings
import xx.steps.steps.DemoSteps
import xx.steps.steps.StepAccess
import xx.steps.steps.StepAccessState
import java.time.LocalDate

/** How often the screen re-checks the calendar date, so it rolls over at midnight on its own. */
private const val DATE_TICK_MS = 60_000L

/** Today's step count: progress ring over the daily goal, with the past week under it. */
@Composable
fun TodayScreen() {
    val context = LocalContext.current
    val repository = remember(context) { StepsRepository.get(context) }

    val today by rememberCurrentDate()
    val goal by AppSettings.goal.collectAsState()
    val paused by AppSettings.paused.collectAsState()
    val demo by AppSettings.demoMode.collectAsState()
    val stepLength by AppSettings.stepLengthCm.collectAsState()
    val access by StepAccessState.access.collectAsState()

    // The current calendar week, re-subscribed when the date rolls over into the next one.
    val weekStart = remember(today) { startOfWeek(today) }
    val week by remember(repository, weekStart) {
        repository.observeDaysFrom(weekStart, WEEK_DAYS)
    }.collectAsState(initial = emptyList())

    val bars = remember(week, weekStart, goal) { buildWeekBars(week, weekStart, WEEK_DAYS, goal) }
    val todaySteps = bars.firstOrNull { it.date == today }?.steps ?: 0

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = formatDayLabel(today),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(20.dp))

        // The notice circle keeps the ring's footprint, so nothing below it moves when the state
        // changes — and it states the reason in black on amber rather than showing a hollow zero.
        when {
            access == StepAccess.SENSOR_MISSING ->
                NoticeCircle(text = stringResource(R.string.no_sensor))

            access == StepAccess.PERMISSION_MISSING ->
                NoticeCircle(text = stringResource(R.string.permission_needed))

            paused -> NoticeCircle(
                text = stringResource(R.string.counting_paused),
                action = CircleActionSpec(
                    icon = Icons.Filled.PlayArrow,
                    text = stringResource(R.string.resume),
                    onClick = { AppSettings.setPaused(context, false) },
                ),
            )

            else -> StepRing(
                steps = todaySteps,
                goal = goal,
                stepLengthCm = stepLength,
                onPause = { AppSettings.setPaused(context, true) },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Actions(access = access, demo = demo, goal = goal, repository = repository)

        Spacer(modifier = Modifier.height(20.dp))
        WeekBars(days = bars, goalLine = goal)
    }
}

/**
 * What the user can do from here: grant the permission, pause and resume counting, or run the
 * demo. Pausing makes no sense while nothing is being counted, so it is offered only when it is.
 */
@Composable
private fun Actions(
    access: StepAccess,
    demo: Boolean,
    goal: Int,
    repository: StepsRepository,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Pausing lives on the circle itself; only the permission needs a button of its own.
        if (access == StepAccess.PERMISSION_MISSING) PermissionButton()

        // How this interface gets looked at on an emulator or a phone with no counter.
        if (demo || access == StepAccess.SENSOR_MISSING) {
            DemoButton(demo = demo, goal = goal, repository = repository)
        }
    }
}

/**
 * Asks for the permission, or sends the user to system settings once Android stops showing the
 * dialog — a second tap that visibly does nothing is worse than no button at all.
 */
@Composable
private fun PermissionButton() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var deniedForGood by rememberSaveable { mutableStateOf(false) }

    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        StepAccessState.refresh(context)
        if (!granted && activity != null) {
            deniedForGood = !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACTIVITY_RECOGNITION,
            )
        }
    }

    Button(
        onClick = {
            if (deniedForGood) {
                openAppSettings(context)
            } else {
                request.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        },
    ) {
        Text(stringResource(if (deniedForGood) R.string.open_settings else R.string.allow))
    }
}

/**
 * Runs the demo: seeds a couple of months of plausible days and feeds the app a simulated counter.
 * Both starting and stopping wipe the database first — a demo run and real history must never end
 * up mixed together, and on a phone that can actually count there is nothing to lose here anyway.
 */
@Composable
private fun DemoButton(demo: Boolean, goal: Int, repository: StepsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    FilledTonalButton(
        onClick = {
            scope.launch { DemoSteps.toggle(context, repository, turnOn = !demo, goal = goal) }
        },
    ) {
        Text(stringResource(if (demo) R.string.demo_stop else R.string.demo_start))
    }
}

/** The current date, re-read every [DATE_TICK_MS] so an open screen survives midnight. */
@Composable
private fun rememberCurrentDate(): State<LocalDate> = produceState(initialValue = LocalDate.now()) {
    while (true) {
        delay(DATE_TICK_MS)
        val now = LocalDate.now()
        if (now != value) value = now
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri(),
        ),
    )
}
