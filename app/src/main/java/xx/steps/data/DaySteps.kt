package xx.steps.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import xx.steps.SLOT_MINUTES
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

/**
 * One [SLOT_MINUTES] slice of a day and the steps that fell in it — the intra-day breakdown behind
 * the day chart. [date] is the same ISO date as in `day_steps`, [slot] the index of the slice,
 * 0 for midnight through `SLOTS_PER_DAY - 1`.
 *
 * A slice with no steps has no row. The rows of one day always add up to that day's total: they
 * are written in the same transaction, out of the same credited number.
 */
@Entity(tableName = "day_slots", primaryKeys = ["date", "slot"])
data class DaySlot(
    val date: String,
    val slot: Int,
    val steps: Int,
)
