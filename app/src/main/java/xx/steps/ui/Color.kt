package xx.steps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Named colors for the app theme. Window backgrounds are a distinct tone from the container
 * roles (cards, dialogs) so overlays stand out against the screen behind them.
 */

/**
 * Fill of the notice circle that replaces the ring when the app cannot count. Light enough that
 * black text sits on it at full contrast, warm enough to read as "attention", not as an error.
 */
val NoticeAmber = Color(0xFFFFCC80)

/** Text inside the notice circle: plain black on amber, the same in both themes. */
val NoticeText = Color(0xFF000000)

/**
 * The ring and the bars turn this green once a day's goal is met — one glance says "done" without
 * reading the number. Dark enough to hold its own against a white card, light enough on black.
 */
val GoalReachedGreen = Color(0xFF2E9E5B)

/**
 * How much of the accent a bar keeps when it is not the one being pointed at — today's bar in the
 * week, the picked quarter hour in a day. One step down from the full colour is enough to tell
 * them apart; anything fainter and a chart of ordinary days reads as a shadow of itself.
 */
const val QUIET_BAR_ALPHA = 0.7f

/** The same for a rule drawn over or under the bars: the goal line, the chart's baseline. */
const val CHART_LINE_ALPHA = 0.8f

/**
 * The day chart's hour rules, every six hours. Amber rather than a grey hairline: they are the only
 * thing telling one part of the day from another, and they have to carry through a wall of bars in
 * both themes without being mistaken for one.
 */
val ChartGridAmber = Color(0xFFFFB300)

/**
 * Accent choices for the ring, the bars and the buttons. Every one is a mid-tone that holds
 * contrast against both the near-white and the near-black window, and white text stays legible on
 * all of them as a button fill. Green is deliberately absent: it is the "goal met" signal, and an
 * accent that close would make a met goal indistinguishable from an ordinary day.
 */
val AccentPalette = listOf(
    Color(0xFF00897B), // teal
    Color(0xFF1E88E5), // blue
    Color(0xFF5C6BC0), // indigo
    Color(0xFF8E24AA), // purple
    Color(0xFFEF6C00), // orange
    Color(0xFFE53935), // red
)

/** Index into [AccentPalette] used until the user picks another — the teal. */
const val DEFAULT_ACCENT_INDEX = 0

fun accentAt(index: Int): Color = AccentPalette[index.coerceIn(AccentPalette.indices)]

// Light theme: a faint grey window with pure-white containers.
val WindowLight = Color(0xFFF1F2F4)
val ContainerLight = Color.White

/**
 * Tonal button fill in the dark theme: the stock tonal container all but disappears against the
 * near-black window, so dismiss buttons get a clearly lighter grey.
 */
val TonalButtonDark = Color(0xFF4A4A4A)

// Dark theme: near-black window with progressively lighter elevated containers.
val WindowDark = Color(0xFF121212)
val ContainerDarkLowest = Color(0xFF1A1A1A)
val ContainerDarkLow = Color(0xFF1F1F1F)
val ContainerDark = Color(0xFF242424)
val ContainerDarkHigh = Color(0xFF2A2A2A)
val ContainerDarkHighest = Color(0xFF303030)

/** One colour of the palette, ringed when it is the one in force. Shared by the row and the dialog. */
@Composable
fun AccentSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = color, shape = CircleShape)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}
