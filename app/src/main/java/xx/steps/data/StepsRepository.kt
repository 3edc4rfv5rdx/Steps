package xx.steps.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import xx.steps.MILLIS_PER_MINUTE
import xx.steps.SECONDS_PER_MINUTE
import xx.steps.SYNC_STATE_ID
import xx.steps.steps.SlotShare
import xx.steps.steps.SyncState
import xx.steps.steps.foldReading
import xx.steps.steps.spreadOverSlots
import xx.steps.toIso
import xx.steps.uptimeMillis
import java.time.LocalDate
import java.time.LocalTime

/** Reads and writes the step history. The counting rule itself is in `steps/StepSync.kt`. */
class StepsRepository(private val database: AppDatabase) {

    private val dao = database.stepsDao()

    fun observeAll(): Flow<List<DaySteps>> = dao.observeAll()

    fun observeDay(date: LocalDate): Flow<DaySteps?> = dao.observeDay(date.toIso())

    /** One day's breakdown by slot, sparse: a quarter hour with no steps has no row. */
    fun observeSlots(date: LocalDate): Flow<List<DaySlot>> = dao.observeSlots(date.toIso())

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
     * the parameter exists so tests can drive reboots and time windows. [now] is the wall-clock
     * time of the reading, which is what the intra-day breakdown is measured back from.
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
        now: LocalTime = LocalTime.now(),
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

            // The same steps once more, this time spread over the stretch of the day they had to
            // happen in. Only the reading's own clock time is known, so the window is measured back
            // from it — see spreadOverSlots for why an even spread is all the sensor supports.
            val toMinute = now.toSecondOfDay() / SECONDS_PER_MINUTE
            val fromMinute = toMinute - (outcome.windowMillis / MILLIS_PER_MINUTE).toInt()
            addSlots(iso, spreadOverSlots(outcome.addedSteps, fromMinute, toMinute))
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
     * Adds [shares] onto whatever the day's slots already hold. Called inside the reading's
     * transaction, so a day's breakdown and its total can never land apart.
     */
    private suspend fun addSlots(iso: String, shares: List<SlotShare>) {
        if (shares.isEmpty()) return
        val existing = dao.slotsOf(iso).associate { it.slot to it.steps }
        dao.upsertSlots(
            shares.map { share ->
                DaySlot(date = iso, slot = share.slot, steps = (existing[share.slot] ?: 0) + share.steps)
            },
        )
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
    suspend fun setDay(
        date: LocalDate,
        steps: Int,
        goal: Int,
        slots: List<SlotShare> = emptyList(),
    ) = database.withTransaction {
        val iso = date.toIso()
        dao.upsertDay(DaySteps(date = iso, steps = steps, goal = goal))
        dao.deleteSlotsOf(iso)
        if (slots.isNotEmpty()) {
            dao.upsertSlots(slots.map { DaySlot(date = iso, slot = it.slot, steps = it.steps) })
        }
    }

    /**
     * Writes a batch of days in one transaction, as an import does. A day's breakdown goes with it:
     * a file carries day totals only, and slots left over from before would no longer add up to the
     * number they sit under.
     */
    suspend fun setDays(days: List<DaySteps>) = database.withTransaction {
        days.forEach {
            dao.upsertDay(it)
            dao.deleteSlotsOf(it.date)
        }
    }

    /** Wipes every recorded day and the counter baseline — the demo's way out, and a reset. */
    suspend fun clearAll() = database.withTransaction {
        dao.deleteAllDays()
        dao.deleteAllSlots()
        dao.deleteSyncState()
    }

    companion object {
        fun get(context: Context): StepsRepository = StepsRepository(AppDatabase.get(context))
    }
}
