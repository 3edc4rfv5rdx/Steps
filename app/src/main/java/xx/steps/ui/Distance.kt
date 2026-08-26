package xx.steps.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import xx.steps.distanceLabel

/**
 * The Compose face of `distanceLabel`: same string, with the context taken from the composition.
 * The string itself is built in `Common.kt`, so the notification says exactly what the ring says.
 */
@Composable
fun distanceLabel(steps: Int, stepLengthCm: Int, decimals: Int = 1): String =
    distanceLabel(LocalContext.current, steps, stepLengthCm, decimals)
