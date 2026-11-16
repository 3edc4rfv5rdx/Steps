package xx.steps.ui

import xx.steps.data.DaySteps
import xx.steps.isoToDateOrNull
import xx.steps.localeWeek
import xx.steps.startOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The history tree and its totals, kept free of Compose so both can be tested on the JVM. The
 * shape mirrors BikeTracker's history (year > month > day), one level shorter: a day is a leaf,
 * since a day holds one number rather than a list of rides. Switched to weeks, the middle level is
 * the week instead of the month.
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

/** What a middle-level row stands for: a calendar month, or a week as the locale numbers it. */
sealed interface Period {
    data class Month(val month: YearMonth) : Period

    /** Week [number] of its week-based year, starting on [start]. */
    data class Week(val number: Int, val start: LocalDate) : Period
}

/** A month or a week of days — the level between a year and its days. */
data class GroupNode(
    val key: String,
    val period: Period,
    val steps: Int,
    val days: List<DayNode>,
)

data class YearNode(
    val key: String,
    val year: Int,
    val steps: Int,
    val groups: List<GroupNode>,
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
 * Groups recorded days into year > month > day, or year > week > day when [byWeek] is set, newest
 * first at every level.
 *
 * A week is never cut in two: weeks are grouped under their week-based year, so the few days
 * around New Year that belong to the neighbour's first or last week sit under that year. The
 * current week is a week like any other, totalled over the days it has so far.
 *
 * Node keys are stable and hierarchical (`y2026`, `y2026-m8`, `y2026-m8-d25`; `y2026-w35`,
 * `y2026-w35-m8-d25`), so the expansion state survives recomposition and rotation, and collapsing
 * a node can close its descendants by prefix.
 */
fun buildHistoryTree(recorded: List<DaySteps>, byWeek: Boolean = false): List<YearNode> {
    val dated = recorded
        .mapNotNull { row ->
            // A restore checks what it stores, but a database written by an older build of this app
            // may already hold a date that cannot be parsed. Skipping it costs that one day; letting
            // it through costs the whole screen, on every visit.
            val date = isoToDateOrNull(row.date) ?: return@mapNotNull null
            date to row
        }
        .sortedByDescending { it.first }

    return dated
        .groupBy { (date, _) -> treeYear(date, byWeek) }
        .map { (year, rowsOfYear) ->
            val groups = rowsOfYear
                .groupBy { (date, _) -> periodOf(date, byWeek) }
                .map { (period, rowsOfGroup) ->
                    val key = groupKey(year, period)
                    val days = rowsOfGroup.map { (date, row) ->
                        DayNode(key = dayKey(key, period, date), date = date, steps = row.steps, goal = row.goal)
                    }
                    GroupNode(key = key, period = period, steps = days.sumOf { it.steps }, days = days)
                }

            YearNode(
                key = yearKey(year),
                year = year,
                steps = groups.sumOf { it.steps },
                groups = groups,
            )
        }
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

/** The keys leading to [date] in the tree [byWeek] picks — used to open the tree on today. */
fun pathToDay(date: LocalDate, byWeek: Boolean = false): List<String> {
    val year = treeYear(date, byWeek)
    val period = periodOf(date, byWeek)
    val group = groupKey(year, period)
    return listOf(yearKey(year), group, dayKey(group, period, date))
}

/**
 * The node keys the tree renders, in order, for the current [expanded] set. Scrolling to a node
 * means finding it here: the list mirrors exactly what the screen lays out.
 */
fun visibleKeys(years: List<YearNode>, expanded: List<String>): List<String> {
    val keys = ArrayList<String>()
    for (year in years) {
        keys += year.key
        if (year.key !in expanded) continue
        for (group in year.groups) {
            keys += group.key
            if (group.key !in expanded) continue
            group.days.forEach { keys += it.key }
        }
    }
    return keys
}

/** The year a day is filed under: the calendar year, or the year its week is numbered in. */
private fun treeYear(date: LocalDate, byWeek: Boolean): Int =
    if (byWeek) date.get(localeWeek().weekBasedYear()) else date.year

private fun periodOf(date: LocalDate, byWeek: Boolean): Period =
    if (byWeek) {
        Period.Week(number = date.get(localeWeek().weekOfWeekBasedYear()), start = startOfWeek(date))
    } else {
        Period.Month(YearMonth.from(date))
    }

private fun yearKey(year: Int) = "y$year"

private fun groupKey(year: Int, period: Period) = when (period) {
    is Period.Month -> "y$year-m${period.month.monthValue}"
    is Period.Week -> "y$year-w${period.number}"
}

private fun dayKey(groupKey: String, period: Period, date: LocalDate) = when (period) {
    is Period.Month -> "$groupKey-d${date.dayOfMonth}"
    // A week can span two months, so its days carry the month as well.
    is Period.Week -> "$groupKey-m${date.monthValue}-d${date.dayOfMonth}"
}
