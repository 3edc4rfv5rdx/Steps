package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xx.steps.steps.distributeOverSlots
import xx.steps.steps.spreadOverSlots

/**
 * How one reading's steps are laid out across the day. The rule that matters most is arithmetic:
 * whatever the interval, the shares add up to exactly the steps credited to the day — the chart
 * under a day must never disagree with the number above it.
 */
class SlotSpreadTest {

    @Test
    fun `a reading inside one slot lands whole in it`() {
        val shares = spreadOverSlots(steps = 300, fromMinute = 8 * 60 + 2, toMinute = 8 * 60 + 9)

        assertEquals(1, shares.size)
        assertEquals(8 * 4, shares.first().slot)
        assertEquals(300, shares.first().steps)
    }

    @Test
    fun `two readings in the same minute still credit the slot they fell in`() {
        val shares = spreadOverSlots(steps = 12, fromMinute = 600, toMinute = 600)

        assertEquals(listOf(600 / SLOT_MINUTES to 12), shares.map { it.slot to it.steps })
    }

    @Test
    fun `an interval across slots is split in proportion to the time in each`() {
        // 07:50 to 08:20: ten minutes in the 07:45 slot, fifteen in 08:00, five in 08:15.
        val shares = spreadOverSlots(steps = 300, fromMinute = 7 * 60 + 50, toMinute = 8 * 60 + 20)

        assertEquals(listOf(31, 32, 33), shares.map { it.slot })
        assertEquals(listOf(100, 150, 50), shares.map { it.steps })
        assertEquals(300, shares.sumOf { it.steps })
    }

    @Test
    fun `rounding never loses or invents a step`() {
        val shares = spreadOverSlots(steps = 1_001, fromMinute = 0, toMinute = 7 * 60 + 7)

        assertEquals(1_001, shares.sumOf { it.steps })
        assertTrue(shares.all { it.steps > 0 })
    }

    @Test
    fun `an interval reaching back over midnight keeps everything on this day`() {
        // A reading at 00:10 whose previous one was 40 minutes ago, on the day before.
        val shares = spreadOverSlots(steps = 80, fromMinute = -30, toMinute = 10)

        assertEquals(80, shares.sumOf { it.steps })
        assertTrue(shares.all { it.slot == 0 })
    }

    @Test
    fun `a whole day of one reading fills every slot`() {
        val shares = spreadOverSlots(steps = 9_600, fromMinute = 0, toMinute = MINUTES_PER_DAY - 1)

        assertEquals(SLOTS_PER_DAY, shares.size)
        assertEquals(9_600, shares.sumOf { it.steps })
    }

    @Test
    fun `nothing walked writes nothing`() {
        assertTrue(spreadOverSlots(steps = 0, fromMinute = 100, toMinute = 200).isEmpty())
        assertTrue(distributeOverSlots(steps = 100, weights = IntArray(SLOTS_PER_DAY)).isEmpty())
    }

    @Test
    fun `weights decide the shares and their total stays exact`() {
        val weights = IntArray(SLOTS_PER_DAY) { if (it < 3) it + 1 else 0 }
        val shares = distributeOverSlots(steps = 600, weights = weights)

        assertEquals(listOf(100, 200, 300), shares.map { it.steps })
        assertEquals(600, shares.sumOf { it.steps })
    }
}
