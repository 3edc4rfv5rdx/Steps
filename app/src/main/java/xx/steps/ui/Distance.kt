package xx.steps.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import xx.steps.R
import xx.steps.distanceMeters
import xx.steps.formatDistanceValue
import xx.steps.isKilometres

/**
 * Distance walked, with its unit — "4,4 km" or "850 m". One place builds this string, so the ring,
 * the totals card and anything later all read the same way. [decimals] is 0 where whole kilometres
 * are enough, as in the history table.
 */
@Composable
fun distanceLabel(steps: Int, stepLengthCm: Int, decimals: Int = 1): String {
    val meters = distanceMeters(steps, stepLengthCm)
    val unit = stringResource(if (isKilometres(meters)) R.string.unit_km else R.string.unit_m)
    return "${formatDistanceValue(meters, decimals)} $unit"
}
