package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xx.steps.data.DaySlot
import xx.steps.ui.buildDayBuckets
import xx.steps.ui.dayStats

/** The bars of the day chart and the figures under them. */
class DayModelTest {

    private fun slot(minute: Int, steps: Int) =
        DaySlot(date = "2026-08-25", slot = minute / SLOT_MINUTES, steps = steps)

    @Test
    fun `an hourly chart is a full day of bars whatever was walked`() {
        val buckets = buildDayBuckets(listOf(slot(9 * 60, 500)), bucketMinutes = 60)

        assertEquals(24, buckets.size)
        assertEquals(0, buckets.first().startMinute)
        assertEquals(60, buckets.first().minutes)
        assertEquals(500, buckets[9].steps)
        assertEquals(500, buckets.sumOf { it.steps })
    }

    @Test
    fun `the quarter hours of a bar add up into it`() {
        val slots = listOf(slot(9 * 60, 100), slot(9 * 60 + 15, 200), slot(9 * 60 + 45, 300))

        assertEquals(600, buildDayBuckets(slots, bucketMinutes = 60)[9].steps)

        val halves = buildDayBuckets(slots, bucketMinutes = 30)
        assertEquals(48, halves.size)
        assertEquals(300, halves[18].steps)
        assertEquals(300, halves[19].steps)
    }

    @Test
    fun `a slot number out of range is dropped rather than trusted`() {
        val buckets = buildDayBuckets(
            listOf(DaySlot("2026-08-25", slot = SLOTS_PER_DAY, steps = 900), slot(0, 10)),
            bucketMinutes = 60,
        )

        assertEquals(24, buckets.size)
        assertEquals(10, buckets.sumOf { it.steps })
    }

    @Test
    fun `a bar width that does not divide the day still yields a whole chart`() {
        val buckets = buildDayBuckets(
            listOf(slot(0, 10), slot(23 * 60 + 45, 20)),
            bucketMinutes = 50,
        )

        assertEquals(MINUTES_PER_DAY / 50, buckets.size)
        assertEquals(30, buckets.sumOf { it.steps })
        assertEquals(20, buckets.last().steps)
    }

    @Test
    fun `the statistics describe the bars being looked at`() {
        val slots = listOf(slot(7 * 60 + 30, 400), slot(18 * 60, 900), slot(18 * 60 + 30, 100))
        val stats = dayStats(buildDayBuckets(slots, bucketMinutes = 60))

        assertEquals(1_400, stats.steps)
        assertEquals(18 * 60, stats.peak?.startMinute)
        assertEquals(1_000, stats.peak?.steps)
        assertEquals(7 * 60, stats.firstMinute)
        assertEquals(19 * 60, stats.lastMinute)
    }

    @Test
    fun `a day with no breakdown has no peak and no walking hours`() {
        val stats = dayStats(buildDayBuckets(emptyList(), bucketMinutes = 60))

        assertEquals(0, stats.steps)
        assertNull(stats.peak)
        assertNull(stats.firstMinute)
        assertNull(stats.lastMinute)
    }

    @Test
    fun `midnight and the end of the day read as the clock shows them`() {
        assertEquals("00:00", formatMinuteOfDay(0))
        assertEquals("07:15", formatMinuteOfDay(7 * 60 + 15))
        assertEquals("24:00", formatMinuteOfDay(MINUTES_PER_DAY))
    }
}
