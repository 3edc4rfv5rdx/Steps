package xx.steps.ui

import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * The two buttons every dialog in the app ends with. Both carry a fill — nothing in this app is a
 * bare text button — and the difference between them is weight, not presence: the action that
 * commits is in the accent colour, the one that backs out is tonal.
 */

@Composable
fun DialogConfirmButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick) { Text(text) }
}

@Composable
fun DialogDismissButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) { Text(text) }
}
