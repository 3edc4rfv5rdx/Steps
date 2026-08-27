package xx.steps.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import xx.steps.settings.ThemeMode

// Window background is a distinct tone from the container roles, so dialogs and cards stand out
// against the screen behind them instead of blending in.
private val LightColors = lightColorScheme(
    background = WindowLight,
    surface = WindowLight,
    surfaceContainerLowest = ContainerLight,
    surfaceContainerLow = ContainerLight,
    surfaceContainer = ContainerLight,
    surfaceContainerHigh = ContainerLight,
    surfaceContainerHighest = ContainerLight,
)

private val DarkColors = darkColorScheme(
    background = WindowDark,
    surface = WindowDark,
    surfaceContainerLowest = ContainerDarkLowest,
    surfaceContainerLow = ContainerDarkLow,
    surfaceContainer = ContainerDark,
    surfaceContainerHigh = ContainerDarkHigh,
    surfaceContainerHighest = ContainerDarkHighest,
    // Tonal buttons (Cancel, and the demo action) must read as buttons against the dark window.
    secondaryContainer = TonalButtonDark,
    onSecondaryContainer = Color.White,
)

/**
 * True when the app renders dark for the given theme choice and system setting.
 *
 * Free of Compose so that the window itself can ask the same question: the platform side of the
 * window — its background before the first frame, and the colour the system bar icons are drawn
 * for — is decided in `MainActivity.onCreate`, where there is no composition to read the setting
 * from, and two answers to "is this app dark" is how a dark start comes to flash white.
 */
fun isDarkTheme(themeMode: ThemeMode, systemInDark: Boolean): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> systemInDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** The same rule, with the system setting taken from the composition. */
@Composable
fun isDarkTheme(themeMode: ThemeMode): Boolean = isDarkTheme(themeMode, isSystemInDarkTheme())

/**
 * Applies the light or dark scheme according to the user's choice, with their accent as the
 * primary color. Every accent in the palette is dark enough for white text, so `onPrimary` is
 * white in both themes rather than tracking the scheme.
 */
@Composable
fun StepsTheme(themeMode: ThemeMode, accentIndex: Int, content: @Composable () -> Unit) {
    val dark = isDarkTheme(themeMode)
    val accent = accentAt(accentIndex)
    val scheme = (if (dark) DarkColors else LightColors).copy(
        primary = accent,
        onPrimary = Color.White,
    )

    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content,
    )
}
