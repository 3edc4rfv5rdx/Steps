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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import xx.steps.R
import xx.steps.DATE_TICK_MS
import xx.steps.WEEK_DAYS
import xx.steps.startOfWeek
import xx.steps.data.StepsRepository
import xx.steps.formatDayLabel
import xx.steps.settings.AppSettings
import xx.steps.steps.StepAccess
import xx.steps.steps.StepAccessState
import java.time.LocalDate

/**
 * Today's step count: progress ring over the daily goal, with the past week under it.
 *
 * [onCountingAllowed] is called once the user allows activity data. What follows from that — the
 * service, the battery exemption, the notification permission — is the activity's to run: this
 * screen's permission button is gone the moment the answer arrives, and a chain hosted on it would
 * stop at the first hand-off.
 */
@Composable
fun TodayScreen(onCountingAllowed: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { StepsRepository.get(context) }

    val today by rememberCurrentDate()
    val goal by AppSettings.goal.collectAsState()
    val paused by AppSettings.paused.collectAsState()
    val stepLength by AppSettings.stepLengthCm.collectAsState()
    val access by StepAccessState.access.collectAsState()

    // The current calendar week, re-subscribed when the date rolls over into the next one.
    val weekStart = remember(today) { startOfWeek(today) }
    val week by remember(repository, weekStart) {
        repository.observeDaysFrom(weekStart, WEEK_DAYS)
    }.collectAsState(initial = emptyList())

    val bars = remember(week, weekStart, goal) { buildWeekBars(week, weekStart, WEEK_DAYS, goal) }
    val todaySteps = bars.firstOrNull { it.date == today }?.steps ?: 0

    // The day whose hour-by-hour breakdown is open over the screen, if any.
    var opened by remember { mutableStateOf<LocalDate?>(null) }

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
        Actions(access = access, onCountingAllowed = onCountingAllowed)

        Spacer(modifier = Modifier.height(20.dp))
        WeekBars(days = bars, goalLine = goal, today = today, onDayClick = { opened = it.date })
    }

    opened?.let { date ->
        DayDetailDialog(date = date, onDismiss = { opened = null })
    }
}

/**
 * What the user can do from here. Pausing lives on the circle itself and the demo on the top bar,
 * which leaves the permission as the only thing needing a button of its own.
 */
@Composable
private fun Actions(access: StepAccess, onCountingAllowed: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (access == StepAccess.PERMISSION_MISSING) PermissionButton(onCountingAllowed)
    }
}

/**
 * Asks for the permission, or sends the user to system settings once Android stops showing the
 * dialog — a second tap that visibly does nothing is worse than no button at all.
 *
 * A granted permission hands over to [onCountingAllowed] and this button goes away: the questions
 * that follow are asked one at a time by the activity, while the user is still answering for this
 * app. The exemption is the first of them, because it lives on a system screen nobody opens
 * unprompted, and without it Android defers the quarter-hourly read.
 */
@Composable
private fun PermissionButton(onCountingAllowed: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var deniedForGood by rememberSaveable { mutableStateOf(false) }

    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        StepAccessState.refresh(context)
        if (granted) {
            onCountingAllowed()
        } else if (activity != null) {
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
