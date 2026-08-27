package xx.steps.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import xx.steps.DATE_TICK_MS
import java.time.LocalDate

/**
 * The current date, re-read every [DATE_TICK_MS] so an open screen survives midnight.
 *
 * Shared rather than kept by the screen that needed it first: two screens showing two answers to
 * "what day is it" is the drift this exists to prevent. Today reads it for its header and its week
 * bars, History for its period totals and for which row wears the band.
 */
@Composable
fun rememberCurrentDate(): State<LocalDate> = produceState(initialValue = LocalDate.now()) {
    while (true) {
        delay(DATE_TICK_MS)
        val now = LocalDate.now()
        if (now != value) value = now
    }
}
