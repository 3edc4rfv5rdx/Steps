package xx.steps

import android.os.SystemClock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared constants and helpers. Everything used by more than one screen or layer lives here. */

/** Room database file name. */
const val DATABASE_NAME = "steps.db"

/** SharedPreferences file holding the daily goal; the sensor baseline lives in the database. */
const val PREFS_NAME = "steps_prefs"

/** Daily step goal used until the user picks their own. */
const val DEFAULT_GOAL = 8000

/** Lowest and highest goal the editor accepts — below is meaningless, above is a typo. */
const val MIN_GOAL = 500
const val MAX_GOAL = 100_000

/** Keeps a goal inside those bounds wherever one enters: the editor, prefs, an imported file. */
fun clampGoal(steps: Int): Int = steps.coerceIn(MIN_GOAL, MAX_GOAL)

/** Days in a week — the bars under the ring show the current one, Monday through Sunday. */
const val WEEK_DAYS = 7

/**
 * First day of the calendar week containing [date], as the user's locale reckons it. Both the week
 * bars and the history's weekly total start here, so they can never disagree about what "this
 * week" means.
 */
fun startOfWeek(date: LocalDate): LocalDate =
    date.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)

/** Step length in centimetres until the user measures their own — an average adult stride. */
const val DEFAULT_STEP_LENGTH_CM = 70

/** Bounds a step length must stay in: below is a shuffle, above is a leap. */
const val MIN_STEP_LENGTH_CM = 30
const val MAX_STEP_LENGTH_CM = 120

fun clampStepLength(cm: Int): Int = cm.coerceIn(MIN_STEP_LENGTH_CM, MAX_STEP_LENGTH_CM)

/**
 * Distance those steps cover, in metres, at the configured step length. The multiplication is in
 * doubles on purpose: `steps * stepLengthCm` stays in Int and overflows on a large all-time total.
 */
fun distanceMeters(steps: Int, stepLengthCm: Int): Double = steps.toDouble() * stepLengthCm / 100.0

/** Primary key of the single sync-state row. */
const val SYNC_STATE_ID = 0

/**
 * How often the background sync reads the counter. Fifteen minutes is WorkManager's floor for
 * periodic work, and it bounds both the midnight error and what a reboot can take away.
 */
const val SYNC_INTERVAL_MINUTES = 15L

/**
 * How long a background read waits for the sensor to report. Some devices only deliver the counter
 * on the next step rather than replaying the cached value, so a read while standing still can time
 * out — harmlessly, since a still phone has no new steps to lose.
 */
const val SENSOR_READ_TIMEOUT_MS = 10_000L

/** Dates are stored as ISO yyyy-MM-dd strings so they sort lexicographically in SQL. */
private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun LocalDate.toIso(): String = format(ISO_DATE)

fun isoToDate(iso: String): LocalDate = LocalDate.parse(iso, ISO_DATE)

/**
 * Milliseconds since boot, sleep included. Monotonic and reset by a reboot, which is exactly what
 * the counting rule needs: it both detects the restart and bounds how many steps can be real.
 * Wall-clock time is unusable here — it jumps when the clock is corrected or the zone changes.
 */
fun uptimeMillis(): Long = SystemClock.elapsedRealtime()

/** Thousands-separated step count, e.g. 8 342 — the only number formatting the UI needs. */
fun formatSteps(steps: Int): String = String.format(Locale.getDefault(), "%,d", steps)

/**
 * Distance as a bare number, without a unit: metres below a kilometre, kilometres with one decimal
 * above it. The unit is a separate localized string, so the caller pairs the two.
 */
fun formatDistanceValue(meters: Double, decimals: Int = 1): String =
    if (meters < 1_000) {
        String.format(Locale.getDefault(), "%.0f", meters)
    } else {
        String.format(Locale.getDefault(), "%,.${decimals}f", meters / 1_000)
    }

/** True when [formatDistanceValue] rendered kilometres rather than metres. */
fun isKilometres(meters: Double): Boolean = meters >= 1_000

/** Short day label for the history rows, e.g. "Mon, 24 Aug", in the user's locale. */
private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")

fun formatDayLabel(date: LocalDate): String =
    date.format(DAY_LABEL.withLocale(Locale.getDefault()))

/**
 * Month name alone, capitalised — the year is already the row above it in the history tree.
 * The standalone pattern letter matters for Russian and Ukrainian: "август", not "августа".
 */
private val MONTH_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL")

fun formatMonthName(month: YearMonth): String =
    month.format(MONTH_LABEL.withLocale(Locale.getDefault()))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
