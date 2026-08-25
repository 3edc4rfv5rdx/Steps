package xx.steps.steps

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xx.steps.settings.AppSettings

/** Whether the app can count right now, and if not, why. */
enum class StepAccess {
    /** Sensor present, permission granted — readings flow. */
    READY,

    /** The phone can count, but the user has not allowed it yet. */
    PERMISSION_MISSING,

    /** No step counter in this phone; nothing the user can do about it. */
    SENSOR_MISSING,
}

/**
 * Process-wide view of that state, so the screens and the live reading agree on it without each
 * asking the framework. Refreshed by the activity on every start, after a permission answer, and
 * whenever demo mode is switched.
 */
object StepAccessState {

    private val _access = MutableStateFlow(StepAccess.PERMISSION_MISSING)
    val access: StateFlow<StepAccess> = _access.asStateFlow()

    fun refresh(context: Context) {
        _access.value = when {
            // The demo feeds its own readings, so neither the hardware nor the permission matters.
            AppSettings.demoMode.value -> StepAccess.READY
            !StepSensor(context).isAvailable -> StepAccess.SENSOR_MISSING
            !hasStepPermission(context) -> StepAccess.PERMISSION_MISSING
            else -> StepAccess.READY
        }
    }
}
