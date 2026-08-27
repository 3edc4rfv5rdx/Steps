package xx.steps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xx.steps.settings.ThemeMode
import xx.steps.ui.isDarkTheme

/**
 * Which theme the app renders. The window itself asks the same question as the composition does —
 * its background before the first frame, and the colour the system bar icons are drawn for — so
 * the rule is one function rather than one per side of the window.
 */
class ThemeRuleTest {

    @Test
    fun `following the system means following the system`() {
        assertTrue(isDarkTheme(ThemeMode.SYSTEM, systemInDark = true))
        assertFalse(isDarkTheme(ThemeMode.SYSTEM, systemInDark = false))
    }

    @Test
    fun `a chosen theme overrides the system either way`() {
        // The case the window used to get wrong twice over: dark app, light phone.
        assertTrue(isDarkTheme(ThemeMode.DARK, systemInDark = false))
        assertFalse(isDarkTheme(ThemeMode.LIGHT, systemInDark = true))
    }
}
