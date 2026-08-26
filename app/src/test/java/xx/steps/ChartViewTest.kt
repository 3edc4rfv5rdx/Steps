package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xx.steps.ui.CHART_MAX_ZOOM
import xx.steps.ui.ChartView
import xx.steps.ui.gridHours
import xx.steps.ui.zoomedView

/** The window the day chart is read through, and the hours it is ruled at. */
class ChartViewTest {

    private val whole = ChartView.WholeDay

    @Test
    fun `pinching open halves the window around the point under the fingers`() {
        val view = zoomedView(whole, zoom = 2f, focus = 0.5f, pan = 0f)

        assertEquals(0.5f, view.width, 1e-5f)
        assertEquals(0.25f, view.start, 1e-5f)
    }

    @Test
    fun `the moment under the fingers stays under them`() {
        // Pinching around the left edge keeps midnight on the left edge.
        val left = zoomedView(whole, zoom = 4f, focus = 0f, pan = 0f)
        assertEquals(0f, left.start, 1e-5f)

        // Around the right edge, the end of the day stays on the right edge.
        val right = zoomedView(whole, zoom = 4f, focus = 1f, pan = 0f)
        assertEquals(1f, right.start + right.width, 1e-5f)
    }

    @Test
    fun `zoom stops at the ceiling`() {
        val view = zoomedView(whole, zoom = 1000f, focus = 0.5f, pan = 0f)

        assertEquals(1f / CHART_MAX_ZOOM, view.width, 1e-5f)
    }

    @Test
    fun `pinching shut never shows more than the day`() {
        val narrow = ChartView(start = 0.4f, width = 0.2f)
        val view = zoomedView(narrow, zoom = 0.001f, focus = 0.5f, pan = 0f)

        assertEquals(1f, view.width, 1e-5f)
        assertEquals(0f, view.start, 1e-5f)
    }

    @Test
    fun `panning cannot walk off either end of the day`() {
        val view = ChartView(start = 0f, width = 0.25f)

        val past = zoomedView(view, zoom = 1f, focus = 0.5f, pan = 5f)
        assertEquals(0f, past.start, 1e-5f)

        val beyond = zoomedView(view, zoom = 1f, focus = 0.5f, pan = -5f)
        assertEquals(1f - 0.25f, beyond.start, 1e-5f)
    }

    @Test
    fun `panning moves the window by the same share of the screen at any zoom`() {
        val wide = zoomedView(ChartView(0.4f, 0.4f), zoom = 1f, focus = 0.5f, pan = -0.5f)
        val narrow = zoomedView(ChartView(0.4f, 0.1f), zoom = 1f, focus = 0.5f, pan = -0.5f)

        assertEquals(0.4f + 0.2f, wide.start, 1e-5f)
        assertEquals(0.4f + 0.05f, narrow.start, 1e-5f)
    }

    @Test
    fun `the whole day is ruled at its quarters`() {
        assertEquals(listOf(0, 6, 12, 18, 24), gridHours(1f))
    }

    @Test
    fun `a stretched axis is ruled more finely, and always ends on midnight`() {
        val stepsSeen = listOf(1f, 0.5f, 0.3f, 0.1f).map { gridHours(it) }

        stepsSeen.forEach { hours ->
            assertEquals(0, hours.first())
            assertEquals(HOURS_PER_DAY, hours.last())
        }
        // Narrower windows are never ruled more coarsely than wider ones.
        stepsSeen.zipWithNext().forEach { (wider, narrower) ->
            assertTrue(narrower.size >= wider.size)
        }
        assertEquals(HOURS_PER_DAY + 1, gridHours(0.1f).size)
    }
}
