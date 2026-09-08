package xx.steps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/**
 * Two dialog buttons pushed to opposite corners. Use it in the AlertDialog confirmButton slot with
 * no dismissButton, or Material clusters both against the right edge.
 */
@Composable
fun DialogButtonRow(start: @Composable () -> Unit, end: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        start()
        end()
    }
}
