package xx.steps.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import xx.steps.SLOTS_PER_DAY
import xx.steps.SLOT_MINUTES
import xx.steps.SYNC_STATE_ID
import xx.steps.clampGoal
import xx.steps.isIsoDate

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

/** What a restore could use out of an untrusted database, and how many days it could not. */
data class UsableRows(
    val days: List<DaySteps>,
    val slots: List<DaySlot>,
    val daysDropped: Int,
)

/**
 * Reduces the rows of a database this app did not necessarily write to the ones it can read back.
 *
 * Everything downstream takes [DaySteps.date] for an ISO date it can parse — the history tree parses
 * every one of them — so a row that is not one of ours is dropped here rather than stored and
 * crashed on afterwards, on every visit to that screen, with no way back through the app. A
 * negative step count goes the same way; a goal from outside the editor's bounds is only clamped,
 * since the steps of that day are still true and only the line they are judged against is not.
 *
 * A slot survives only with the day it belongs to: a breakdown with no total above it adds up to
 * nothing, and an index outside the day is not a quarter hour of it.
 */
fun usableRows(days: List<DaySteps>, slots: List<DaySlot>): UsableRows {
    val kept = days.mapNotNull { day ->
        if (isIsoDate(day.date) && day.steps >= 0) day.copy(goal = clampGoal(day.goal)) else null
    }
    val dates = kept.mapTo(HashSet()) { it.date }

    return UsableRows(
        days = kept,
        slots = slots.filter { it.date in dates && it.slot in 0 until SLOTS_PER_DAY && it.steps >= 0 },
        daysDropped = days.size - kept.size,
    )
}
