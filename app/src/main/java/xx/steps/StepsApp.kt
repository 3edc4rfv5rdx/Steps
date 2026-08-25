package xx.steps

import android.app.Application
import xx.steps.settings.AppSettings
import xx.steps.work.StepsSyncWorker

/**
 * Process entry point: brings persisted settings into memory before any screen reads them, and
 * makes sure the periodic counter sync is scheduled.
 */
class StepsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.load(this)
        StepsSyncWorker.schedule(this)
    }
}
