package xx.steps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Big and bold: this text replaces the step count, so it carries the count's visual weight. */
private val NOTICE_TEXT_SIZE = 26.sp
private val NOTICE_LINE_HEIGHT = 32.sp

/**
 * Stands in for the progress ring whenever there is nothing to show: no sensor, no permission,
 * counting paused. It occupies the ring's exact footprint, so the screen does not jump when the
 * state changes, and states its reason in black on amber — legible in sunlight and in both themes.
 */
@Composable
fun NoticeCircle(
    text: String,
    modifier: Modifier = Modifier,
    action: CircleActionSpec? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(color = NoticeAmber, shape = CircleShape)
            .let { if (action == null) it else it.clickable(onClick = action.onClick) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                fontSize = NOTICE_TEXT_SIZE,
                lineHeight = NOTICE_LINE_HEIGHT,
                fontWeight = FontWeight.Bold,
                color = NoticeText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            action?.let { CircleAction(icon = it.icon, text = it.text, tint = NoticeText, large = true) }
        }
    }
}

/** What tapping the circle does, drawn as an icon and a word under its message. */
data class CircleActionSpec(
    val icon: ImageVector,
    val text: String,
    val onClick: () -> Unit,
)
