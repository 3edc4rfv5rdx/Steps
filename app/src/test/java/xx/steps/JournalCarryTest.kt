package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a journal batch that could not be written hands to the next attempt. */
class JournalCarryTest {

    @Test
    fun `a batch under the ceiling keeps every line`() {
        // The oldest line held is the one nearest to whatever went wrong; a failed write that drops
        // it every time leaves the journal describing everything but its own trouble.
        assertEquals("first\nsecond\n", carriedAfterFailure("first\nsecond\n", maxChars = 1_024))
    }

    @Test
    fun `a batch over the ceiling keeps whole lines from the end`() {
        val batch = (1..100).joinToString("\n", postfix = "\n") { "line $it" }

        val carried = carriedAfterFailure(batch, maxChars = 40)

        assertTrue(carried.length <= 40)
        assertTrue(batch.endsWith(carried))
        // Whole lines only: what is kept starts where a line starts.
        assertTrue(carried.startsWith("line "))
        assertTrue(batch.contains("\n" + carried))
    }

    @Test
    fun `a cut landing on a line break keeps the whole tail`() {
        // "a\n" is cut away exactly, so "bb\ncc\n" is already whole lines and none of it is dropped.
        assertEquals("bb\ncc\n", carriedAfterFailure("a\nbb\ncc\n", maxChars = 6))
    }

    @Test
    fun `a single line longer than the ceiling is dropped rather than halved`() {
        assertEquals("", carriedAfterFailure("x".repeat(100) + "\n", maxChars = 10))
    }
}
