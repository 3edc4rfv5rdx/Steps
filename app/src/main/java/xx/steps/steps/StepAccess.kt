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
        _access.value = stepAccessOf(
            demo = AppSettings.demoMode.value,
            permitted = hasStepPermission(context),
            sensorPresent = { StepSensor(context).isAvailable },
        )
    }
}

/**
 * Which of the three states a set of facts adds up to. The one place that decision is made — the
 * background worker used to make it again, in the opposite order, and report "no sensor" for a
 * phone that simply had not been allowed one yet.
 *
 * The demo feeds its own readings, so neither the hardware nor the permission matters to it.
 *
 * The permission comes first, and [sensorPresent] is a function so that on a phone without it the
 * sensor is never looked for at all: Android hides the step counter from an app that does not hold
 * ACTIVITY_RECOGNITION, so a phone that has one is indistinguishable from a phone that has none
 * until the permission is granted. Asked the other way round, a fresh install on a phone with a
 * counter would say it has none and never offer the button that would have fixed it.
 */
fun stepAccessOf(demo: Boolean, permitted: Boolean, sensorPresent: () -> Boolean): StepAccess = when {
    demo -> StepAccess.READY
    !permitted -> StepAccess.PERMISSION_MISSING
    !sensorPresent() -> StepAccess.SENSOR_MISSING
    else -> StepAccess.READY
}

/** What the app still has to put to the user once counting itself has been allowed. */
enum class PermissionAsk {
    /** The battery exemption, without which the quarter-hourly read is deferred away. */
    BATTERY_EXEMPTION,

    /** Permission to post the notification the step count lives in. */
    NOTIFICATIONS,

    /** Everything is settled; ask nothing. */
    NOTHING,
}

/**
 * The next question to ask, given what is already settled. One at a time, and the exemption before
 * the notification: it is a system screen the user leaves the app for, and a permission dialog
 * launched while that screen is coming up is dismissed unread. Whatever is left is asked on the way
 * back from it.
 *
 * Pure, so the order is pinned by a JVM test rather than by the sequence of callbacks that runs it.
 */
fun nextPermissionAsk(exempt: Boolean, canPostNotifications: Boolean): PermissionAsk = when {
    !exempt -> PermissionAsk.BATTERY_EXEMPTION
    !canPostNotifications -> PermissionAsk.NOTIFICATIONS
    else -> PermissionAsk.NOTHING
}
