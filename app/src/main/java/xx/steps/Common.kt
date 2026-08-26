package xx.steps

import android.content.Context
import android.os.SystemClock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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

/**
 * Percentage of the goal a day's steps come to, rounded to the nearest whole percent. A goal of
 * zero can only come from a hand-edited file; it reads as nothing walked rather than dividing by it.
 */
fun percentOfGoal(steps: Int, goal: Int): Int =
    if (goal > 0) (steps.toFloat() / goal * 100).roundToInt() else 0

/** Days in a week — the bars under the ring show the current one, Monday through Sunday. */
const val WEEK_DAYS = 7

/**
 * First day of the calendar week containing [date], as the user's locale reckons it. Both the week
 * bars and the history's weekly total start here, so they can never disagree about what "this
 * week" means.
 */
fun startOfWeek(date: LocalDate): LocalDate =
    date.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)

/** Time arithmetic, named where a bare 60 would not say which unit is being converted. */
const val SECONDS_PER_MINUTE = 60
const val MINUTES_PER_HOUR = 60
const val MILLIS_PER_MINUTE = 60_000L

/** Sensor event timestamps arrive in nanoseconds; the rest of the app counts milliseconds. */
const val NANOS_PER_MILLI = 1_000_000L

/** Minutes in a day — the width of the intra-day chart, and the bound on any minute-of-day. */
const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

/**
 * Resolution the intra-day breakdown is stored at. A quarter hour is the sync interval, so it is
 * also the finest slot a reading can honestly fill: nothing is known about where inside the
 * interval the steps fell. The chart builds its coarser bars out of these.
 */
const val SLOT_MINUTES = 15
const val SLOTS_PER_DAY = MINUTES_PER_DAY / SLOT_MINUTES

/**
 * Bar widths the day chart can be read at, in minutes, coarsest first. The finest is [SLOT_MINUTES]
 * itself — nothing below it is recorded, so nothing below it can be drawn.
 */
val CHART_BUCKET_MINUTES = listOf(60, DEFAULT_CHART_BUCKET_MINUTES, SLOT_MINUTES)

/**
 * Bar width the day chart opens at. Half an hour shows the shape of a day — when it started, where
 * the walks were — without thinning the bars to the point where a quiet stretch is unreadable.
 */
const val DEFAULT_CHART_BUCKET_MINUTES = 30

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

/**
 * How often anything showing "today" re-reads the date, so it survives midnight without being
 * rebuilt. A minute is far below the error the counting itself carries.
 */
const val DATE_TICK_MS = 60_000L

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
 * A time of day as minutes past midnight, on the 24-hour clock the day chart is drawn on. The end
 * of the last bar is 1440, and it reads as 24:00 rather than wrapping round to midnight.
 */
fun formatMinuteOfDay(minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", minute / MINUTES_PER_HOUR, minute % MINUTES_PER_HOUR)

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

/**
 * Distance walked, with its unit — "4,4 km" or "850 m". One place builds this string, so the ring,
 * the totals card and the notification all read the same way. [decimals] is 0 where whole
 * kilometres are enough, as in the history table.
 *
 * The Compose screens reach this through the `distanceLabel` in `ui/`, which supplies the context.
 */
fun distanceLabel(context: Context, steps: Int, stepLengthCm: Int, decimals: Int = 1): String {
    val meters = distanceMeters(steps, stepLengthCm)
    val unit = context.getString(if (isKilometres(meters)) R.string.unit_km else R.string.unit_m)
    return "${formatDistanceValue(meters, decimals)} $unit"
}

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
