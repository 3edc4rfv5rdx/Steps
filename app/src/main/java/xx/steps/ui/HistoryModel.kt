package xx.steps.ui

import xx.steps.data.DaySteps
import xx.steps.isoToDateOrNull
import xx.steps.startOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The history tree and its totals, kept free of Compose so both can be tested on the JVM. The
 * shape mirrors BikeTracker's history (year > month > day), one level shorter: a day is a leaf,
 * since a day holds one number rather than a list of rides.
 */

/** A recorded day. [reached] is judged by the goal stored in that day's own row. */
data class DayNode(
    val key: String,
    val date: LocalDate,
    val steps: Int,
    val goal: Int,
) {
    val reached: Boolean get() = goal > 0 && steps >= goal
}

data class MonthNode(
    val key: String,
    val month: YearMonth,
    val steps: Int,
    val days: List<DayNode>,
)

data class YearNode(
    val key: String,
    val year: Int,
    val steps: Int,
    val months: List<MonthNode>,
)

/** One row of the totals card: what was walked in a period and over how many recorded days. */
data class PeriodTotal(val steps: Int, val days: Int)

data class HistoryTotals(
    val week: PeriodTotal,
    val month: PeriodTotal,
    val year: PeriodTotal,
    val allTime: PeriodTotal,
)

/**
 * Groups recorded days into year > month > day, newest first at every level.
 *
 * Node keys are stable and hierarchical (`y2026`, `y2026-m8`, `y2026-m8-d25`), so the expansion
 * state survives recomposition and rotation, and collapsing a node can close its descendants by
 * prefix.
 */
fun buildHistoryTree(recorded: List<DaySteps>): List<YearNode> {
    val days = recorded
        .mapNotNull { row ->
            // A restore checks what it stores, but a database written by an older build of this app
            // may already hold a date that cannot be parsed. Skipping it costs that one day; letting
            // it through costs the whole screen, on every visit.
            val date = isoToDateOrNull(row.date) ?: return@mapNotNull null
            DayNode(key = dayKey(date), date = date, steps = row.steps, goal = row.goal)
        }
        .sortedByDescending { it.date }

    return days
        .groupBy { it.date.year }
        .map { (year, daysOfYear) ->
            val months = daysOfYear
                .groupBy { YearMonth.from(it.date) }
                .map { (month, daysOfMonth) ->
                    MonthNode(
                        key = monthKey(month),
                        month = month,
                        steps = daysOfMonth.sumOf { it.steps },
                        days = daysOfMonth,
                    )
                }
                .sortedByDescending { it.month }

            YearNode(
                key = yearKey(year),
                year = year,
                steps = months.sumOf { it.steps },
                months = months,
            )
        }
        .sortedByDescending { it.year }
}

/**
 * Calendar-period totals as of [today]: this week, this month, this year, and everything recorded.
 * The week starts on whichever day the user's locale says it does.
 */
fun historyTotals(recorded: List<DaySteps>, today: LocalDate = LocalDate.now()): HistoryTotals {
    val weekStart = startOfWeek(today)
    val monthStart = today.withDayOfMonth(1)
    val yearStart = today.withDayOfYear(1)

    val dated = recorded.mapNotNull { row -> isoToDateOrNull(row.date)?.let { it to row.steps } }

    fun total(from: LocalDate?): PeriodTotal {
        val inRange = dated.filter { (date, _) ->
            // Days ahead of today can exist after a timezone move; they belong to no period here.
            !date.isAfter(today) && (from == null || !date.isBefore(from))
        }
        return PeriodTotal(steps = inRange.sumOf { it.second }, days = inRange.size)
    }

    return HistoryTotals(
        week = total(weekStart),
        month = total(monthStart),
        year = total(yearStart),
        allTime = total(null),
    )
}

/** The year > month > day keys leading to [date] — used to open the tree on today. */
fun pathToDay(date: LocalDate): List<String> =
    listOf(yearKey(date.year), monthKey(YearMonth.from(date)), dayKey(date))

/**
 * The node keys the tree renders, in order, for the current [expanded] set. Scrolling to a node
 * means finding it here: the list mirrors exactly what the screen lays out.
 */
fun visibleKeys(years: List<YearNode>, expanded: List<String>): List<String> {
    val keys = ArrayList<String>()
    for (year in years) {
        keys += year.key
        if (year.key !in expanded) continue
        for (month in year.months) {
            keys += month.key
            if (month.key !in expanded) continue
            month.days.forEach { keys += it.key }
        }
    }
    return keys
}

private fun yearKey(year: Int) = "y$year"

private fun monthKey(month: YearMonth) = "y${month.year}-m${month.monthValue}"

private fun dayKey(date: LocalDate) = "y${date.year}-m${date.monthValue}-d${date.dayOfMonth}"
