package xx.steps.steps

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import xx.steps.data.StepsRepository
import xx.steps.logSteps
import xx.steps.settings.AppSettings

/**
 * Reads the counter for as long as the process lives, and folds every reading into today's row.
 *
 * It has to be the process rather than the open screen. The hardware counter does not accumulate
 * on its own: it advances while some app holds a registration on it and stands still otherwise, so
 * an app that only listens while its screen is on records only the steps taken in front of it. That
 * was invisible for as long as another pedometer was installed and kept the sensor awake for
 * everybody — and it surfaced the day that app was uninstalled.
 *
 * The registration is cheap: an on-change, non-wakeup, low-power sensor, delivering an event every
 * few steps and nothing at all while the phone is still.
 *
 * A killed process still leaves a gap, which the periodic worker closes when it starts the process
 * up again. Counting is not stopped while paused: readings are consumed and thrown away, so a
 * pause discards steps rather than postponing them.
 */
object StepCounting {

    private var started = false

    /** Starts the readings. Called once, from the application object. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(context: Context) {
        if (started) return
        started = true

        val app = context.applicationContext
        val repository = StepsRepository.get(app)
        val sensor = StepSensor(app)

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            combine(StepAccessState.access, AppSettings.demoMode, ::Pair)
                .flatMapLatest { (access, demo) ->
                    logSteps("counting: access=$access demo=$demo")
                    when {
                        demo -> DemoSteps.readings()
                        access == StepAccess.READY -> sensor.readings()
                        // Nothing to read yet; the flow restarts by itself once the permission
                        // answer reaches StepAccessState.
                        else -> emptyFlow()
                    }
                }
                .collect { raw ->
                    repository.recordReading(
                        rawCount = raw,
                        goal = AppSettings.goal.value,
                        credit = !AppSettings.paused.value,
                    )
                }
        }
    }
}
