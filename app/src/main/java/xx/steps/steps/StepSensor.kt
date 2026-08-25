package xx.steps.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import xx.steps.SENSOR_READ_TIMEOUT_MS

/** True when the user has granted the permission the step counter is gated behind. */
fun hasStepPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * The only place that talks to `SensorManager`. Everything it hands out is a raw cumulative
 * counter value; turning that into steps is `foldReading`'s job.
 */
class StepSensor(context: Context) {

    private val manager = context.applicationContext.getSystemService(SensorManager::class.java)
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    /** False on phones with no step counter at all — the screens say so instead of showing zero. */
    val isAvailable: Boolean get() = sensor != null

    /**
     * Counter readings for as long as the flow is collected. The counter is an on-change sensor, so
     * the current value normally arrives right after registering and then on every few steps.
     *
     * Completes at once when there is no sensor, so a collector needs no separate check. Readings
     * are conflated: only the newest value matters, an older one is already contained in it.
     */
    fun readings(): Flow<Long> = callbackFlow {
        val target = sensor
        if (target == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // The framework types the counter as a float; above ~16.7M steps it would start
                // losing single steps, which is decades of walking without a reboot.
                trySend(event.values[0].toLong())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager?.registerListener(listener, target, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { manager?.unregisterListener(listener) }
    }.conflate()

    /**
     * One reading for the background sync, or null when none arrives: either the sensor stayed
     * silent for [timeoutMillis] — on some devices that just means nobody has taken a step since
     * the last event — or there is no sensor at all.
     *
     * `firstOrNull`, not `first`: the latter throws on a flow that completes empty, which is
     * exactly what a phone without a step counter produces.
     */
    suspend fun readOnce(timeoutMillis: Long = SENSOR_READ_TIMEOUT_MS): Long? =
        withTimeoutOrNull(timeoutMillis) { readings().firstOrNull() }
}
