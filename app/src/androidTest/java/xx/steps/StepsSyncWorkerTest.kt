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
    fun missingHardwareOutranksTheMissingPermission() = runBlocking {
        if (StepSensor(context).isAvailable) return@runBlocking

        StepAccessState.refresh(context)

        // A phone with no counter must say so, not ask for a permission that would change nothing.
        assertEquals(StepAccess.SENSOR_MISSING, StepAccessState.access.first())
    }
}
