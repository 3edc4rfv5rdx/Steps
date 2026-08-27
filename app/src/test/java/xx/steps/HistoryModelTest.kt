package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xx.steps.data.DaySteps
import xx.steps.ui.buildHistoryTree
import xx.steps.ui.historyTotals
import xx.steps.ui.pathToDay
import java.time.LocalDate
import java.time.YearMonth

/** Grouping recorded days into the history tree, and the calendar totals above it. */
class HistoryModelTest {

    /** A Tuesday, so the locale's week start is a day or two back rather than today. */
    private val today = LocalDate.of(2026, 8, 25)

    private fun row(date: LocalDate, steps: Int, goal: Int = 8_000) =
        DaySteps(date = date.toIso(), steps = steps, goal = goal)

    @Test
    fun `a date that cannot be parsed is skipped rather than thrown on`() {
        // Only a database from an older build, or a hand-edited one, can hold such a row — and the
        // whole screen is what it costs if this throws.
        val rows = listOf(
            row(LocalDate.of(2026, 8, 25), 4_000),
            DaySteps(date = "not-a-date", steps = 900, goal = 8_000),
        )

        val tree = buildHistoryTree(rows)
        assertEquals(1, tree.size)
        assertEquals(4_000, tree[0].steps)

        val totals = historyTotals(rows, today)
        assertEquals(4_000, totals.allTime.steps)
        assertEquals(1, totals.allTime.days)
    }

    @Test
    fun `days are grouped newest first at every level`() {
        val tree = buildHistoryTree(
            listOf(
                row(LocalDate.of(2025, 12, 31), 1_000),
                row(LocalDate.of(2026, 1, 5), 2_000),
                row(LocalDate.of(2026, 8, 24), 3_000),
                row(LocalDate.of(2026, 8, 25), 4_000),
            ),
        )

        assertEquals(listOf(2026, 2025), tree.map { it.year })
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 1)), tree[0].months.map { it.month })
        assertEquals(
            listOf(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 8, 24)),
            tree[0].months[0].days.map { it.date },
        )
    }

    @Test
    fun `sums roll up from days to months to years`() {
        val tree = buildHistoryTree(
            listOf(
                row(LocalDate.of(2026, 8, 24), 3_000),
                row(LocalDate.of(2026, 8, 25), 4_000),
                row(LocalDate.of(2026, 7, 1), 5_000),
            ),
        )

        assertEquals(7_000, tree[0].months[0].steps)
        assertEquals(12_000, tree[0].steps)
    }

    @Test
    fun `a day is judged by the goal stored on it`() {
        val tree = buildHistoryTree(
            listOf(
                row(LocalDate.of(2026, 8, 24), 7_000, goal = 6_000),
                row(LocalDate.of(2026, 8, 25), 7_000, goal = 12_000),
            ),
        )

        val days = tree[0].months[0].days
        assertFalse(days[0].reached) // 25th: 7 000 against a 12 000 goal
        assertTrue(days[1].reached) // 24th: 7 000 against a 6 000 goal
    }

    @Test
    fun `totals cover the calendar week month and year`() {
        val totals = historyTotals(
            listOf(
                row(today, 4_000),
                row(today.minusDays(1), 3_000), // still this week
                row(LocalDate.of(2026, 8, 3), 5_000), // this month, earlier week
                row(LocalDate.of(2026, 2, 2), 6_000), // this year, earlier month
                row(LocalDate.of(2025, 5, 5), 7_000), // a previous year
            ),
            today = today,
        )

        assertEquals(7_000, totals.week.steps)
        assertEquals(12_000, totals.month.steps)
        assertEquals(18_000, totals.year.steps)
        assertEquals(25_000, totals.allTime.steps)
        assertEquals(5, totals.allTime.days)
    }

    @Test
    fun `a day in the future belongs to no period`() {
        // A timezone move can leave a row dated tomorrow; it must not inflate this week.
        val totals = historyTotals(listOf(row(today.plusDays(1), 9_000)), today = today)

        assertEquals(0, totals.week.steps)
        assertEquals(0, totals.allTime.steps)
        assertEquals(0, totals.allTime.days)
    }

    @Test
    fun `empty history totals to zero`() {
        val totals = historyTotals(emptyList(), today = today)

        assertEquals(0, totals.allTime.steps)
        assertEquals(0, totals.allTime.days)
        assertTrue(buildHistoryTree(emptyList()).isEmpty())
    }

    @Test
    fun `the path to a day matches the keys the tree builds`() {
        val tree = buildHistoryTree(listOf(row(today, 1_000)))
        val path = pathToDay(today)

        assertEquals(tree[0].key, path[0])
        assertEquals(tree[0].months[0].key, path[1])
        assertEquals(tree[0].months[0].days[0].key, path[2])
    }
}
