package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xx.steps.data.DaySlot
import xx.steps.data.DaySteps
import xx.steps.data.usableRows

/**
 * What a restore is allowed to take out of a database this app did not necessarily write. Every
 * reader of a stored date parses it, so a row that cannot be parsed must never reach the database.
 */
class UsableRowsTest {

    private fun day(date: String, steps: Int = 1_000, goal: Int = 8_000) = DaySteps(date, steps, goal)

    @Test
    fun `a date this app cannot read back is dropped with its breakdown`() {
        val rows = usableRows(
            days = listOf(day("2026-08-25"), day("not-a-date"), day("25/08/2026")),
            slots = listOf(
                DaySlot("2026-08-25", 32, 400),
                DaySlot("not-a-date", 32, 400),
            ),
        )

        assertEquals(listOf("2026-08-25"), rows.days.map { it.date })
        assertEquals(listOf("2026-08-25"), rows.slots.map { it.date })
        assertEquals(2, rows.daysDropped)
    }

    @Test
    fun `a negative step count is not a day`() {
        val rows = usableRows(days = listOf(day("2026-08-25", steps = -500)), slots = emptyList())

        assertTrue(rows.days.isEmpty())
        assertEquals(1, rows.daysDropped)
    }

    @Test
    fun `a goal outside the editor's bounds is clamped rather than losing the day`() {
        val rows = usableRows(
            days = listOf(day("2026-08-25", goal = 0), day("2026-08-24", goal = 900_000)),
            slots = emptyList(),
        )

        // The steps of those days are still true; only the line they are judged against was not.
        assertEquals(listOf(MIN_GOAL, MAX_GOAL), rows.days.map { it.goal })
        assertEquals(0, rows.daysDropped)
    }

    @Test
    fun `a slot outside the day, or with no day above it, is dropped`() {
        val rows = usableRows(
            days = listOf(day("2026-08-25")),
            slots = listOf(
                DaySlot("2026-08-25", slot = 0, steps = 10),
                DaySlot("2026-08-25", slot = SLOTS_PER_DAY, steps = 10),
                DaySlot("2026-08-25", slot = -1, steps = 10),
                DaySlot("2026-08-25", slot = 5, steps = -10),
                DaySlot("2026-08-20", slot = 5, steps = 10),
            ),
        )

        assertEquals(listOf(0), rows.slots.map { it.slot })
    }

    @Test
    fun `a backup this app wrote comes through untouched`() {
        val days = listOf(day("2026-08-25", 4_000, 8_000), day("2026-08-24", 9_100, 6_000))
        val slots = listOf(DaySlot("2026-08-25", 32, 400), DaySlot("2026-08-24", 72, 500))

        val rows = usableRows(days, slots)

        assertEquals(days, rows.days)
        assertEquals(slots, rows.slots)
        assertEquals(0, rows.daysDropped)
    }
}
