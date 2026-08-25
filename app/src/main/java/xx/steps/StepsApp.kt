package xx.steps

import android.app.Application
import xx.steps.settings.AppSettings
import xx.steps.steps.DemoSteps
import xx.steps.work.StepsSyncWorker

/**
 * Process entry point: brings persisted settings into memory before any screen reads them, and
 * makes sure the periodic counter sync is scheduled.
 */
class StepsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.load(this)
        // The setting persists, the emulator does not: an install carried to a real phone would
        // otherwise keep counting simulated steps with no way on screen to turn them off.
        if (AppSettings.demoMode.value && !DemoSteps.isEmulator) {
            AppSettings.setDemoMode(this, false)
        }
        StepsSyncWorker.schedule(this)
    }
}
