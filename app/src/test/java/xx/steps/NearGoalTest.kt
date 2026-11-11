package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the "almost there" sound plays: once, on the reading that crosses 85 % of the goal. */
class NearGoalTest {

    @Test
    fun `the mark is 1500 steps short of a 10000 goal`() {
        assertEquals(8_500, nearGoalMark(10_000))
    }

    @Test
    fun `the reading that crosses the mark sounds`() {
        assertTrue(reachesNearGoal(before = 8_400, after = 8_600, goal = 10_000))
        assertTrue(reachesNearGoal(before = 8_499, after = 8_500, goal = 10_000))
    }

    @Test
    fun `readings on either side of the mark stay silent`() {
        assertFalse(reachesNearGoal(before = 8_000, after = 8_499, goal = 10_000))
        assertFalse(reachesNearGoal(before = 8_500, after = 9_000, goal = 10_000))
    }

    @Test
    fun `a reading that jumps past the goal itself stays silent`() {
        assertFalse(reachesNearGoal(before = 8_000, after = 10_000, goal = 10_000))
    }

    @Test
    fun `a zero goal never sounds`() {
        assertFalse(reachesNearGoal(before = 0, after = 100, goal = 0))
    }
}
