package xx.steps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** How an operation ended, which is all the banner needs to know to colour itself. */
enum class BannerKind {
    /** It worked. Green. */
    SUCCESS,

    /** It worked, but not entirely — skipped lines, nothing to do. Amber. */
    WARNING,

    /** It did not work. Red. */
    ERROR,
}

/** A finished operation waiting to be shown. */
data class BannerMessage(val kind: BannerKind, val text: String)

/** Banner fill for a failure — dark enough to carry white text in either theme. */
private val BannerRed = Color(0xFFC62828)

/** How long a banner stays before it takes itself away. */
private const val BANNER_MILLIS = 4_000L

/**
 * One banner for every outcome in the app: green when it worked, amber when it worked partly, red
 * when it did not. Tapping it dismisses it early; otherwise it goes on its own.
 */
@Composable
fun StatusBanner(message: BannerMessage, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(message) {
        delay(BANNER_MILLIS)
        onDismiss()
    }

    val background = when (message.kind) {
        BannerKind.SUCCESS -> GoalReachedGreen
        BannerKind.WARNING -> NoticeAmber
        BannerKind.ERROR -> BannerRed
    }
    // Amber is a light fill and takes black; the other two are dark and take white.
    val content = if (message.kind == BannerKind.WARNING) NoticeText else Color.White
    val icon: ImageVector = when (message.kind) {
        BannerKind.SUCCESS -> Icons.Filled.CheckCircle
        BannerKind.WARNING -> Icons.Filled.Warning
        BannerKind.ERROR -> Icons.Filled.Error
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color = background, shape = RoundedCornerShape(10.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content)
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = content,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
