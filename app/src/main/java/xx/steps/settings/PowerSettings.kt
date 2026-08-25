package xx.steps.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Android's battery restrictions, as far as an app is allowed to see and ask about them.
 *
 * Nothing here counts steps — the hardware does that whatever the system decides. What the
 * restrictions bear on is how often the app is woken to read the counter: a phone that has decided
 * this app is asleep defers the quarter-hourly sync or stops running it at all, and then a walk
 * arrives in one lump whenever the app is next opened, on the hour and the day it was opened.
 * Nothing is lost, but the day loses its shape.
 */

/** True when the app is exempt from battery optimisation, so its periodic work runs as scheduled. */
fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

/**
 * Where to send the user. Not yet exempt, Android shows the one-tap dialog for this app; already
 * exempt, there is nothing to ask, so the system's list opens instead — the only place the
 * exemption can be given back.
 *
 * Samsung's own "sleeping apps" list is a separate thing again, reachable by no documented intent;
 * a phone that puts the app to sleep there has to be told not to by hand.
 */
@SuppressLint("BatteryLife")
fun batteryExemptionIntent(context: Context): Intent =
    if (isIgnoringBatteryOptimizations(context)) {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    } else {
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "package:${context.packageName}".toUri(),
        )
    }
