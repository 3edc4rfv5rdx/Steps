package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Test
import xx.steps.data.DaySteps
import xx.steps.ui.buildHistoryTree
import xx.steps.ui.pathToDay
import xx.steps.ui.visibleKeys
import java.time.LocalDate

/** What the history tree actually lays out for a given expansion — the basis for scrolling to a day. */
class VisibleKeysTest {

    private val today = LocalDate.of(2026, 8, 25)

    private val recorded = listOf(
        DaySteps(today.toIso(), 4_000, 8_000),
        DaySteps(today.minusDays(1).toIso(), 9_000, 8_000),
        DaySteps(LocalDate.of(2026, 7, 4).toIso(), 5_000, 8_000),
        DaySteps(LocalDate.of(2025, 12, 31).toIso(), 3_000, 8_000),
    )

    private val tree = buildHistoryTree(recorded)

    @Test
    fun `a collapsed tree shows only its years`() {
        assertEquals(listOf("y2026", "y2025"), visibleKeys(tree, expanded = emptyList()))
    }

    @Test
    fun `an open year shows its months but not their days`() {
        assertEquals(
            listOf("y2026", "y2026-m8", "y2026-m7", "y2025"),
            visibleKeys(tree, expanded = listOf("y2026")),
        )
    }

    @Test
    fun `an open month shows its days newest first`() {
        val keys = visibleKeys(tree, expanded = listOf("y2026", "y2026-m8"))

        assertEquals(
            listOf("y2026", "y2026-m8", "y2026-m8-d25", "y2026-m8-d24", "y2026-m7", "y2025"),
            keys,
        )
    }

    @Test
    fun `today is findable once its branch is open`() {
        val keys = visibleKeys(tree, expanded = pathToDay(today).dropLast(1))

        // This index is what the screen scrolls to; today must actually be in the laid-out list.
        assertEquals(2, keys.indexOf(pathToDay(today).last()))
    }

    @Test
    fun `an open month under a closed year stays hidden`() {
        // Collapsing a year must hide its months even if their keys linger in the expanded set.
        assertEquals(
            listOf("y2026", "y2025"),
            visibleKeys(tree, expanded = listOf("y2026-m8")),
        )
    }
}
