package xx.steps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xx.steps.steps.StepAccess
import xx.steps.steps.StepAccessState
import xx.steps.steps.StepSensor
import xx.steps.steps.hasStepPermission
import xx.steps.work.StepsSyncWorker

/**
 * The background sync on a device that cannot count — the emulator has no step counter, which is
 * exactly the path a phone without the hardware, or without the permission, takes.
 */
@RunWith(AndroidJUnit4::class)
class StepsSyncWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theWorkerSucceedsWithNothingToRead() = runBlocking {
        val worker = TestListenableWorkerBuilder<StepsSyncWorker>(context).build()

        // Retrying would burn battery waiting for a sensor that is never going to answer.
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }

    @Test
    fun readingsCompleteAtOnceWithoutASensor() = runBlocking {
        val sensor = StepSensor(context)
        if (sensor.isAvailable) return@runBlocking

        // Completing rather than hanging is what lets collectors skip a separate availability check.
        assertTrue(sensor.readings().toList().isEmpty())
        assertNull(sensor.readOnce(timeoutMillis = 1_000))
    }

    @Test
    fun theMissingPermissionOutranksTheMissingHardware() = runBlocking {
        // This is the state a fresh install is in, and the emulator's default.
        if (hasStepPermission(context)) return@runBlocking

        StepAccessState.refresh(context)

        // Android hides the counter from an app that has not been allowed activity data, so a
        // phone that has one is indistinguishable from a phone that has none. Answered the other
        // way round, a fresh install would claim the hardware was missing and offer nothing to fix
        // it — which is what this test used to assert.
        //
        // The other two answers of the rule are pinned by StepAccessRuleTest, on the JVM, where the
        // facts can be stated instead of depending on how this device happens to be set up.
        assertEquals(StepAccess.PERMISSION_MISSING, StepAccessState.access.first())
    }
}
