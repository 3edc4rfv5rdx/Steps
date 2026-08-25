package xx.steps.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xx.steps.R
import xx.steps.formatSteps
import xx.steps.percentOfGoal

/** Thickness of both the track and the progress arc. */
private val RING_STROKE = 18.dp

/** Hairline circle drawn along the ring's inner edge, in the ring's own colour. */
private val INNER_CIRCLE_STROKE = 1.5.dp

/**
 * The percentage inside the ring. Set well above body size: after the count itself it is the number
 * the ring is read for, and at title size it was lost among the goal and the distance around it.
 */
private val PERCENT_SIZE = 28.sp

/** The whole ring sweeps clockwise from twelve o'clock. */
private const val RING_START_ANGLE = -90f

/**
 * Today's progress as a ring with the count inside it.
 *
 * Past the goal the arc stops growing and turns green: the ring is a "done or not" signal, and a
 * second lap would only be read as a lower number.
 */
@Composable
fun StepRing(
    steps: Int,
    goal: Int,
    stepLengthCm: Int,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (goal > 0) (steps.toFloat() / goal).coerceIn(0f, 1f) else 0f
    val reached = goal > 0 && steps >= goal
    val percent = percentOfGoal(steps, goal)

    // Animating the sweep makes a step landing while you watch read as motion, not as a jump.
    val sweep by animateFloatAsState(targetValue = fraction * 360f, label = "ringSweep")

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val arcColor = if (reached) GoalReachedGreen else MaterialTheme.colorScheme.primary

    Box(
        // The whole ring is the pause control: a target this size needs no aiming, and the icon
        // and word inside it say what a tap does.
        modifier = modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape).clickable(onClick = onPause),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = RING_STROKE.toPx()
            val inset = stroke / 2
            val side = size.minDimension - stroke
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            val arcSize = androidx.compose.ui.geometry.Size(side, side)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = arcColor,
                startAngle = RING_START_ANGLE,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )

            // A hairline along the ring's inner edge, in the ring's colour — the band continued
            // as a line, with nothing between the two.
            drawCircle(
                color = arcColor,
                radius = size.minDimension / 2 - stroke,
                style = Stroke(width = INNER_CIRCLE_STROKE.toPx()),
            )
        }

        RingLabel(
            steps = steps,
            goal = goal,
            percent = percent,
            reached = reached,
            arcColor = arcColor,
            stepLengthCm = stepLengthCm,
        )
    }
}

@Composable
private fun RingLabel(
    steps: Int,
    goal: Int,
    percent: Int,
    reached: Boolean,
    arcColor: Color,
    stepLengthCm: Int,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The banner goes above the count rather than in the percentage's place: the number keeps
        // climbing past the goal, and 146% is worth seeing.
        if (reached) {
            Text(
                text = stringResource(R.string.goal_reached),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                color = arcColor,
            )
        }
        Text(
            text = formatSteps(steps),
            fontSize = 56.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.goal_label) + ": " + formatSteps(goal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.percent_of_goal, percent),
            fontSize = PERCENT_SIZE,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            color = arcColor,
        )
        Text(
            text = distanceLabel(steps = steps, stepLengthCm = stepLengthCm),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        CircleAction(icon = Icons.Filled.Pause, text = stringResource(R.string.pause), tint = arcColor)
    }
}

/**
 * The icon-and-word line at the bottom of the circle, saying what tapping it does. [large] is for
 * the paused state, where this line is the only thing to act on and carries the whole circle.
 */
@Composable
fun CircleAction(icon: ImageVector, text: String, tint: Color, large: Boolean = false) {
    Row(
        modifier = Modifier.padding(top = if (large) 16.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(if (large) 34.dp else 24.dp),
        )
        Text(
            text = text,
            fontSize = if (large) 26.sp else 18.sp,
            fontWeight = if (large) FontWeight.Bold else FontWeight.SemiBold,
            color = tint,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
