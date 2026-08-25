package xx.steps.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import xx.steps.SYNC_INTERVAL_MINUTES
import xx.steps.data.StepsRepository
import xx.steps.settings.AppSettings
import xx.steps.steps.StepSensor
import xx.steps.steps.hasStepPermission
import java.util.concurrent.TimeUnit

/**
 * Reads the counter every [SYNC_INTERVAL_MINUTES] and folds the result into today's row.
 *
 * The hardware keeps counting on its own, so nothing has to run while walking — this job exists
 * only to move those steps into the database often enough that a reboot or midnight takes little
 * with it.
 */
class StepsSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val sensor = StepSensor(applicationContext)

        // None of these is a failure to retry: without a sensor there is nothing to read ever,
        // without the permission the user has to act first, and in demo mode the fake counter
        // exists only while a screen is open — a background read would break its illusion.
        if (AppSettings.demoMode.value) return Result.success()
        if (!sensor.isAvailable) return Result.success()
        if (!hasStepPermission(applicationContext)) return Result.success()

        // A silent sensor means no step since the last event on devices that do not replay the
        // cached value; the next run picks the counter up, and no steps are lost meanwhile.
        val raw = sensor.readOnce() ?: return Result.success()

        // While paused the reading is still consumed, moving the baseline without recording:
        // otherwise the pause would only postpone the steps it is meant to discard.
        StepsRepository.get(applicationContext).recordReading(
            rawCount = raw,
            goal = AppSettings.goal.value,
            credit = !AppSettings.paused.value,
        )
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "steps-sync"

        /**
         * Schedules the periodic sync, keeping any existing schedule: replacing it on every launch
         * would restart the interval and could starve the sync on a phone that is opened often.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StepsSyncWorker>(
                SYNC_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
