package xx.steps.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * The two buttons every dialog in the app ends with. Both carry a fill — nothing in this app is a
 * bare text button — and the difference between them is weight, not presence: the action that
 * commits is in the accent colour, the one that backs out is tonal.
 */

/**
 * Tighter than the stock padding: at this app's 18dp minimum text size, two default-padded
 * buttons do not fit across a dialog and Material stacks them onto separate lines.
 */
private val DIALOG_BUTTON_PADDING = PaddingValues(horizontal = 14.dp, vertical = 8.dp)

@Composable
fun DialogConfirmButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, contentPadding = DIALOG_BUTTON_PADDING) {
        Text(text, maxLines = 1)
    }
}

@Composable
fun DialogDismissButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = DIALOG_BUTTON_PADDING) {
        Text(text, maxLines = 1)
    }
}
