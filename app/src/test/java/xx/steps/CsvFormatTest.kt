package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xx.steps.data.CSV_HEADER
import xx.steps.data.DaySteps
import xx.steps.data.formatCsv
import xx.steps.data.mergeDays
import xx.steps.data.parseCsv

/** The CSV format and the rule that folds an imported file into what is already stored. */
class CsvFormatTest {

    private fun day(date: String, steps: Int, goal: Int = 8_000) = DaySteps(date, steps, goal)

    @Test
    fun `a written file starts with the header and one line per day, oldest first`() {
        val text = formatCsv(listOf(day("2026-08-25", 4_000), day("2026-08-24", 9_100)))

        assertEquals(
            listOf(CSV_HEADER, "2026-08-24,9100,8000", "2026-08-25,4000,8000"),
            text.trim().lines(),
        )
    }

    @Test
    fun `what was written reads back unchanged`() {
        val days = listOf(day("2026-01-01", 1), day("2026-12-31", 30_000, goal = 12_000))

        val parsed = parseCsv(formatCsv(days))

        assertEquals(days.sortedBy { it.date }, parsed.days)
        assertEquals(0, parsed.skipped)
    }

    @Test
    fun `malformed lines are skipped and counted, the rest still imports`() {
        val parsed = parseCsv(
            """
            $CSV_HEADER
            2026-08-24,9100,8000

            not-a-date,10,8000
            2026-08-25,oops,8000
            2026-13-45,10,8000
            2026-08-26,7000,8000
            """.trimIndent(),
        )

        assertEquals(listOf("2026-08-24", "2026-08-26"), parsed.days.map { it.date })
        assertEquals(3, parsed.skipped)
    }

    @Test
    fun `a file without the goal column still imports`() {
        val parsed = parseCsv("$CSV_HEADER\n2026-08-24,5000")

        assertEquals(1, parsed.days.size)
        assertEquals(5_000, parsed.days[0].steps)
        assertEquals(0, parsed.days[0].goal) // filled in on merge, from the goal in force then
    }

    @Test
    fun `a negative step count is not a day`() {
        val parsed = parseCsv("$CSV_HEADER\n2026-08-24,-5,8000")

        assertTrue(parsed.days.isEmpty())
        assertEquals(1, parsed.skipped)
    }

    @Test
    fun `duplicate dates inside one file collapse to the larger count`() {
        val parsed = parseCsv("$CSV_HEADER\n2026-08-24,100,8000\n2026-08-24,900,8000")

        assertEquals(1, parsed.days.size)
        assertEquals(900, parsed.days[0].steps)
    }

    @Test
    fun `merging keeps the fuller record of a day`() {
        val merged = mergeDays(
            existing = listOf(day("2026-08-24", 9_100), day("2026-08-25", 4_000)),
            imported = listOf(day("2026-08-24", 300), day("2026-08-25", 6_500)),
            fallbackGoal = 8_000,
        )

        // The 24th is already fuller in the database, so it is not written at all.
        assertEquals(listOf("2026-08-25"), merged.map { it.date })
        assertEquals(6_500, merged[0].steps)
    }

    @Test
    fun `a stored day keeps the goal it was judged by`() {
        val merged = mergeDays(
            existing = listOf(day("2026-08-24", 100, goal = 6_000)),
            imported = listOf(day("2026-08-24", 9_000, goal = 20_000)),
            fallbackGoal = 8_000,
        )

        assertEquals(9_000, merged[0].steps)
        assertEquals(6_000, merged[0].goal)
    }

    @Test
    fun `a new day takes the file's goal, or the current one when the file had none`() {
        val merged = mergeDays(
            existing = emptyList(),
            imported = listOf(day("2026-08-24", 5_000, goal = 10_000), day("2026-08-25", 5_000, goal = 0)),
            fallbackGoal = 8_000,
        )

        assertEquals(10_000, merged[0].goal)
        assertEquals(8_000, merged[1].goal)
    }
}
