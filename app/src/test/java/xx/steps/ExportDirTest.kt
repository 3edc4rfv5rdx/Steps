package xx.steps

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the app's files land. The folder had three definitions — two of them the same expression
 * written twice, the third hand-spelled with a trailing slash — and moving it would have moved two.
 * This string is what the export and the backup have always used, so it is pinned here: a refactor
 * of the constant must not quietly relocate a year of backups.
 */
class ExportDirTest {

    @Test
    fun `the export folder is the one the app has always written to`() {
        assertEquals("Documents/Steps", EXPORT_DIR)
    }
}
