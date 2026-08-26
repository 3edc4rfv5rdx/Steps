package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xx.steps.steps.DemoSteps
import kotlin.random.Random

/**
 * Seeding the demo history. The rule that matters is that it is total: the goal is the user's, it
 * runs from [MIN_GOAL] to [MAX_GOAL], and the seeding runs after the database has been wiped — a
 * goal it cannot produce a day for is not a dull demo but a wipe with nothing put back.
 */
class DemoSeedTest {

    /** The ends of the editor's range, the old fixed bounds, and the default in between. */
    private val goals = listOf(MIN_GOAL, 1_200, 8_000, 15_500, MAX_GOAL)

    private val draws = 200

    @Test
    fun `every goal the editor accepts seeds a day without throwing`() {
        val random = Random(1)

        goals.forEach { goal ->
            repeat(draws) {
                DemoSteps.seededSteps(goal, lazy = true, random = random)
                DemoSteps.seededSteps(goal, lazy = false, random = random)
            }
        }
    }

    @Test
    fun `a lazy day misses the goal and an ordinary one meets it`() {
        val random = Random(2)

        goals.forEach { goal ->
            repeat(draws) {
                val lazy = DemoSteps.seededSteps(goal, lazy = true, random = random)
                val ordinary = DemoSteps.seededSteps(goal, lazy = false, random = random)

                // The history has to show visibly missed goals as well as met ones, at every goal.
                assertTrue("lazy $lazy against goal $goal", lazy < goal)
                assertTrue("lazy $lazy against goal $goal", lazy > 0)
                assertTrue("ordinary $ordinary against goal $goal", ordinary >= goal)
            }
        }
    }

    @Test
    fun `a goal from outside the editor's bounds is clamped rather than thrown at`() {
        val random = Random(3)

        // Nothing calls it this way today, but it runs after clearAll: it must not be the one
        // place a stored nonsense goal turns into a wipe with nothing seeded back.
        listOf(Int.MIN_VALUE, -5, 0, 1, Int.MAX_VALUE).forEach { goal ->
            val lazy = DemoSteps.seededSteps(goal, lazy = true, random = random)
            val ordinary = DemoSteps.seededSteps(goal, lazy = false, random = random)
            val clamped = clampGoal(goal)

            assertTrue("lazy $lazy against clamped $clamped", lazy in 1 until clamped)
            assertTrue("ordinary $ordinary against clamped $clamped", ordinary >= clamped)
        }
    }

    @Test
    fun `a demo history is built whole, every day it was asked for`() {
        val today = java.time.LocalDate.of(2026, 8, 26)
        val history = DemoSteps.demoHistory(goal = 8_000, days = 70, today = today)

        // Built as one value and written in one transaction, so there is no halfway: either every
        // one of these rows lands or none does.
        assertEquals(70, history.days.size)
        assertEquals(70, history.days.map { it.date }.distinct().size)
        assertEquals(today.minusDays(1).toIso(), history.days.first().date)
        assertEquals(today.minusDays(70).toIso(), history.days.last().date)
        // Today is left alone for the live demo counter to fill.
        assertTrue(history.days.none { it.date == today.toIso() })
    }

    @Test
    fun `every day of a demo history carries its own breakdown, adding up to it`() {
        val history = DemoSteps.demoHistory(goal = MIN_GOAL, days = 12)
        val byDate = history.slots.groupBy { it.date }

        assertEquals(history.days.map { it.date }.toSet(), byDate.keys)
        history.days.forEach { day ->
            assertEquals(day.steps, byDate.getValue(day.date).sumOf { it.steps })
        }
        // A stamped goal a day was never drawn against would make the history judge itself wrongly.
        assertTrue(history.days.all { it.goal == MIN_GOAL })
    }

    @Test
    fun `a seeded day's breakdown adds up to its total`() {
        val random = Random(4)

        goals.forEach { goal ->
            repeat(10) {
                val steps = DemoSteps.seededSteps(goal, lazy = it % 4 == 0, random = random)
                assertEquals(steps, DemoSteps.demoSlots(steps).sumOf { share -> share.steps })
            }
        }
    }
}
