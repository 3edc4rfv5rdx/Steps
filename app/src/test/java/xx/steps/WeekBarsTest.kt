package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Test
import xx.steps.data.DaySteps
import xx.steps.ui.buildWeekBars
import java.time.LocalDate

/** Building the week under the ring: seven slots, gaps filled, each day judged by its own goal. */
class WeekBarsTest {

    private val today = LocalDate.of(2026, 8, 25)

    private fun day(date: LocalDate, steps: Int, goal: Int = 8_000) =
        DaySteps(date = date.toIso(), steps = steps, goal = goal)

    /** Monday of the week containing [today] — the bars span a calendar week, not a rolling one. */
    private val weekStart = LocalDate.of(2026, 8, 24)

    @Test
    fun `a week with no records is still seven days of zero`() {
        val bars = buildWeekBars(recorded = emptyList(), first = weekStart, days = 7, currentGoal = 8_000)

        assertEquals(7, bars.size)
        assertEquals(List(7) { 0 }, bars.map { it.steps })
        assertEquals(weekStart, bars.first().date)
        assertEquals(weekStart.plusDays(6), bars.last().date)
    }

    @Test
    fun `days still to come in this week are shown as zero`() {
        // Tuesday: the rest of the week has no rows yet and must not be dropped from the chart.
        val bars = buildWeekBars(listOf(day(today, 3_000)), first = weekStart, days = 7, currentGoal = 8_000)

        assertEquals(listOf(0, 3_000, 0, 0, 0, 0, 0), bars.map { it.steps })
    }

    @Test
    fun `days without a row are filled in between recorded ones`() {
        val bars = buildWeekBars(
            recorded = listOf(day(weekStart, 4_000), day(weekStart.plusDays(3), 6_200)),
            first = weekStart,
            days = 7,
            currentGoal = 8_000,
        )

        assertEquals(listOf(4_000, 0, 0, 6_200, 0, 0, 0), bars.map { it.steps })
    }

    @Test
    fun `each recorded day keeps its own goal and gaps take the current one`() {
        val bars = buildWeekBars(
            recorded = listOf(day(weekStart, 9_000, goal = 6_000)),
            first = weekStart,
            days = 7,
            currentGoal = 12_000,
        )

        // Monday was walked against 6 000 and stays a day the goal was met.
        assertEquals(6_000, bars[0].goal)
        assertEquals(12_000, bars[1].goal)
    }

    @Test
    fun `rows outside the window are ignored`() {
        val bars = buildWeekBars(
            recorded = listOf(day(weekStart.minusDays(30), 5_000), day(today, 1_000)),
            first = weekStart,
            days = 7,
            currentGoal = 8_000,
        )

        assertEquals(7, bars.size)
        assertEquals(1_000, bars[1].steps)
        assertEquals(0, bars.first().steps)
    }
}
