package xx.steps.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import xx.steps.SYNC_STATE_ID

/**
 * One calendar day's step total. [date] is the local date as ISO yyyy-MM-dd, which sorts
 * lexicographically in SQL, so the history queries need no date functions.
 *
 * [goal] is the goal in force when the row was created. Keeping it per row means changing the goal
 * later never rewrites whether past days were met.
 */
@Entity(tableName = "day_steps")
data class DaySteps(
    @PrimaryKey val date: String,
    val steps: Int,
    val goal: Int,
)

/**
 * Where the last sensor reading left the cumulative counter. Exactly one row, id [SYNC_STATE_ID].
 *
 * It lives in the database rather than in preferences so that crediting steps to a day and moving
 * the baseline happen in one transaction: a process death between the two would otherwise count
 * the same steps twice on the next reading.
 */
@Entity(tableName = "sync_state")
data class SyncStateRow(
    @PrimaryKey val id: Int = SYNC_STATE_ID,
    val lastRaw: Long,
    val lastUptimeMillis: Long,
)
