package xx.steps.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import xx.steps.SYNC_STATE_ID
import xx.steps.steps.SyncState
import xx.steps.steps.foldReading
import xx.steps.toIso
import xx.steps.uptimeMillis
import java.time.LocalDate

/** Reads and writes the step history. The counting rule itself is in `steps/StepSync.kt`. */
class StepsRepository(private val database: AppDatabase) {

    private val dao = database.stepsDao()

    fun observeAll(): Flow<List<DaySteps>> = dao.observeAll()

    fun observeDay(date: LocalDate): Flow<DaySteps?> = dao.observeDay(date.toIso())

    /** Every recorded day, read once — for the export and for merging an import against. */
    suspend fun allDays(): List<DaySteps> = dao.allDays()

    /**
     * The [days] days starting at [first], oldest first. Days with no steps have no row, so the
     * result is sparse — callers fill the gaps with zeroes rather than expecting one entry per day.
     */
    fun observeDaysFrom(first: LocalDate, days: Int): Flow<List<DaySteps>> =
        dao.observeRange(first.toIso(), first.plusDays(days - 1L).toIso())

    /**
     * Folds one cumulative counter reading into [today]'s row and returns the steps credited.
     *
     * The day and the new baseline are written in one transaction: were the baseline to move
     * without the steps landing (or the other way round), the next reading would count them twice
     * or drop them. A day's row is created only once it has steps, and is stamped with [goal] then.
     *
     * [uptimeMillis] must come from the same clock as the stored baseline — see `uptimeMillis()`;
     * the parameter exists so tests can drive reboots and time windows.
     *
     * With [credit] false the baseline still moves but nothing is recorded: that is what counting
     * paused means. The hardware keeps counting through a bus ride whatever the app does, so the
     * only way to discard those steps is to keep consuming them and throw them away — stopping the
     * readings would just hand them over in one lump when the pause ends.
     */
    suspend fun recordReading(
        rawCount: Long,
        goal: Int,
        today: LocalDate = LocalDate.now(),
        uptimeMillis: Long = uptimeMillis(),
        credit: Boolean = true,
    ): Int = database.withTransaction {
        val previous = dao.syncState(SYNC_STATE_ID)?.let { SyncState(it.lastRaw, it.lastUptimeMillis) }
        val outcome = foldReading(previous, rawCount, uptimeMillis)

        if (credit && outcome.addedSteps > 0) {
            val iso = today.toIso()
            val existing = dao.dayRow(iso)
            dao.upsertDay(
                existing?.copy(steps = existing.steps + outcome.addedSteps)
                    ?: DaySteps(date = iso, steps = outcome.addedSteps, goal = goal),
            )
        }

        dao.upsertSyncState(
            SyncStateRow(
                id = SYNC_STATE_ID,
                lastRaw = outcome.newState.lastRaw,
                lastUptimeMillis = outcome.newState.lastUptimeMillis,
            ),
        )
        outcome.addedSteps
    }

    /**
     * Retargets today's row after a goal change. Past days keep the goal they were judged by, and a
     * day with no row yet will simply be stamped with the new goal when it gets one.
     */
    suspend fun applyGoalToToday(goal: Int, today: LocalDate = LocalDate.now()) {
        dao.updateGoal(today.toIso(), goal)
    }

    /**
     * Writes a day outright, replacing whatever was there. Used by the demo seeding and, later, by
     * the CSV import — never by counting, which only ever adds to a day.
     */
    suspend fun setDay(date: LocalDate, steps: Int, goal: Int) {
        dao.upsertDay(DaySteps(date = date.toIso(), steps = steps, goal = goal))
    }

    /** Writes a batch of days in one transaction, as an import does. */
    suspend fun setDays(days: List<DaySteps>) = database.withTransaction {
        days.forEach { dao.upsertDay(it) }
    }

    /** Wipes every recorded day and the counter baseline — the demo's way out, and a reset. */
    suspend fun clearAll() = database.withTransaction {
        dao.deleteAllDays()
        dao.deleteSyncState()
    }

    companion object {
        fun get(context: Context): StepsRepository = StepsRepository(AppDatabase.get(context))
    }
}
