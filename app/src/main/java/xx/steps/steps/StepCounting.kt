package xx.steps.steps

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import xx.steps.BACKGROUND_FOLD_INTERVAL_MS
import xx.steps.FOREGROUND_FOLD_INTERVAL_MS
import xx.steps.data.StepsRepository
import xx.steps.logSteps
import xx.steps.settings.AppSettings
import xx.steps.uptimeMillis

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
 * Holding it is necessary but not sufficient. Android stops delivering events to an app whose UID
 * has gone idle, leaving the registration in place and marked `disabled`, so what keeps this flow
 * fed with the screen off is StepsService — see it for why there is no cheaper way.
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

        watchScreens(app)

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            // When the last reading was written, how many arrived meanwhile without being, and
            // whether the demo has yet had a reading folded in this process. All three are read and
            // written only by this collector, on one coroutine.
            var lastFold: Long? = null
            var heldBack = 0
            var demoFoldPending = true

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
                    val now = uptimeMillis()
                    val paused = AppSettings.paused.value
                    val demo = AppSettings.demoMode.value
                    if (!shouldFold(lastFold, now, screenOpen.value, paused)) {
                        heldBack++
                        return@collect
                    }
                    // Said once per fold rather than once per reading: a line for every held-back
                    // reading would cost exactly what holding them back is meant to save, and the
                    // sensor's own line is written for every event either way.
                    if (heldBack > 0) logSteps("counting: $heldBack readings held back since the last fold")
                    lastFold = now
                    heldBack = 0
                    val credit = creditsSteps(paused, demoFirstOfProcess = demo && demoFoldPending)
                    if (demo) demoFoldPending = false
                    repository.recordReading(
                        rawCount = raw,
                        goal = AppSettings.goal.value,
                        credit = credit,
                    )
                }
        }
    }

    /**
     * Whether a screen of this app is in front of the user, watched on the application rather than
     * asked of a screen: the readings belong to the process, and all a screen changes is how often
     * the number it shows has to be right.
     */
    private val screenOpen = MutableStateFlow(true)

    /** Started activities, touched only from the main thread, which is where the callbacks land. */
    private var startedActivities = 0

    private fun watchScreens(app: Context) {
        val application = app as? Application
        if (application == null) {
            // Nothing to watch it with: leave the flag saying a screen is open, so readings are
            // folded at the short cadence. Wrong on the side of writing too often, never too rarely.
            logSteps("counting: no application to watch screens on, folding at the foreground cadence")
            return
        }
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    startedActivities++
                    screenOpen.value = true
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities--
                    screenOpen.value = startedActivities > 0
                }

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
        // Nothing is on screen until an activity says so — the process is often started by the
        // worker, with nobody looking at it.
        screenOpen.value = false
    }
}

/**
 * Whether a reading taken at [now] is written now or held back for the next one.
 *
 * Both cadences exist for the same reason: an on-change counter reports every step or two, and
 * every reading written is a transaction. With a screen in front of the user the floor is
 * [FOREGROUND_FOLD_INTERVAL_MS], short enough that the count on it still grows as they walk; with
 * none, [BACKGROUND_FOLD_INTERVAL_MS]. Nothing is lost either way — the counter is cumulative, so
 * the next reading carries the steps of every one held back, and the quarter-hourly worker closes
 * the day whatever happens.
 *
 * [paused] is the exception to the cadence. A pause throws steps away rather than postponing them,
 * and which steps it throws away is decided by where the baseline stands when it ends: let the
 * baseline lag a minute behind the counter and a minute of the bus ride is credited on resuming.
 * So while it lasts — a ride, not a day — every reading moves the baseline, as it did before there
 * was a cadence at all.
 *
 * [lastFoldMillis] and [now] both come from `uptimeMillis()`, monotonic within one process; a fresh
 * process has no last fold and writes at once.
 */
fun shouldFold(lastFoldMillis: Long?, now: Long, screenOpen: Boolean, paused: Boolean): Boolean {
    if (paused || lastFoldMillis == null) return true
    val floor = if (screenOpen) FOREGROUND_FOLD_INTERVAL_MS else BACKGROUND_FOLD_INTERVAL_MS
    return now - lastFoldMillis >= floor
}

/**
 * Whether a folded reading adds its steps to the day, or only moves the baseline.
 *
 * [paused] consumes the reading without crediting it — a pause throws steps away rather than
 * postponing them, which is why the readings are never stopped.
 *
 * [demoFirstOfProcess] is the same move made for a different reason. The demo counter starts from
 * the same constant in every process, while the baseline it left behind persists, so the first
 * reading of a restarted demo stands below the stored value and `foldReading` — rightly, for the
 * real sensor — reads that as a reboot and offers the whole of it. Twenty thousand steps would land
 * on today at once, smeared flat back across the day chart. Discarding that one reading leaves the
 * demo doing exactly what an install does: the first reading establishes a baseline and credits
 * nothing.
 *
 * Once per process is enough. Switching the demo off and on again empties the database, baseline
 * included, so a demo started inside a process has nothing to fold against either way.
 */
fun creditsSteps(paused: Boolean, demoFirstOfProcess: Boolean): Boolean = !paused && !demoFirstOfProcess
