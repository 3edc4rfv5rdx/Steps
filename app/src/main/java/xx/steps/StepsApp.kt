package xx.steps

import android.app.Application
import xx.steps.settings.AppSettings
import xx.steps.steps.DemoSteps
import xx.steps.steps.StepAccessState
import xx.steps.steps.StepCounting
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
        // Both before the readings start: the access state decides what they read, and on a launch
        // that no screen took part in — a worker starting the process — nothing else would set it.
        StepAccessState.refresh(this)
        StepCounting.start(this)
        StepsSyncWorker.schedule(this)
    }
}
