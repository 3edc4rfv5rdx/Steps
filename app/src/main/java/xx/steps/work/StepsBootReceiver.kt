package xx.steps.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import xx.steps.logSteps

/**
 * Puts counting back on its feet after the two things that stop it without anybody noticing: the
 * phone restarting, and the app itself being replaced by a new build.
 *
 * Both leave the app installed, permitted and scheduled, but not running — and nothing brings it
 * back until the user opens it or the quarter-hourly worker comes round. That is up to fifteen
 * minutes of walking past a counter that is switched off, every reboot. Starting a foreground
 * service is allowed from both of these broadcasts, which is what makes this possible at all.
 */
class StepsBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        logSteps("boot: ${intent.action}, starting the service")
        StepsService.start(context)
        // The schedule survives both events on its own, but asking again costs nothing and covers
        // the case where it did not: the policy is KEEP, so an existing schedule is left alone.
        StepsSyncWorker.schedule(context)
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
