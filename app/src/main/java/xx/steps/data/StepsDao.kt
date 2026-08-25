package xx.steps.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import xx.steps.SYNC_STATE_ID

/**
 * Every query the app makes. Both tables are served by one DAO: they are written together in a
 * single transaction on every sensor reading, and the whole database is a few hundred small rows.
 */
@Dao
interface StepsDao {

    /** All recorded days, newest first — the history tree and the totals reduce over this. */
    @Query("SELECT * FROM day_steps ORDER BY date DESC")
    fun observeAll(): Flow<List<DaySteps>>

    /** One day, observed live so the Today screen updates as readings land. */
    @Query("SELECT * FROM day_steps WHERE date = :date")
    fun observeDay(date: String): Flow<DaySteps?>

    /**
     * A closed range of days, oldest first. ISO dates sort lexicographically, so a string
     * comparison is a date comparison — no date functions needed in SQL.
     */
    @Query("SELECT * FROM day_steps WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<DaySteps>>

    @Query("SELECT * FROM day_steps WHERE date = :date")
    suspend fun dayRow(date: String): DaySteps?

    @Query("SELECT * FROM day_steps ORDER BY date")
    suspend fun allDays(): List<DaySteps>

    @Upsert
    suspend fun upsertDay(day: DaySteps)

    /** Retargets a day's goal; used for today only, so past days keep the goal they were judged by. */
    @Query("UPDATE day_steps SET goal = :goal WHERE date = :date")
    suspend fun updateGoal(date: String, goal: Int)

    /** The single sync-state row; callers pass [SYNC_STATE_ID], as a query takes no template. */
    @Query("SELECT * FROM sync_state WHERE id = :id")
    suspend fun syncState(id: Int): SyncStateRow?

    @Upsert
    suspend fun upsertSyncState(state: SyncStateRow)

    /** One day's intra-day breakdown, oldest slot first — what the day chart draws. */
    @Query("SELECT * FROM day_slots WHERE date = :date ORDER BY slot")
    fun observeSlots(date: String): Flow<List<DaySlot>>

    /** The same rows read once, to add today's new steps onto the slots that already hold some. */
    @Query("SELECT * FROM day_slots WHERE date = :date")
    suspend fun slotsOf(date: String): List<DaySlot>

    @Upsert
    suspend fun upsertSlots(slots: List<DaySlot>)

    /**
     * Drops a day's breakdown. An import that overwrites the day's total has none to offer, and a
     * breakdown left behind from before would no longer add up to the number above it.
     */
    @Query("DELETE FROM day_slots WHERE date = :date")
    suspend fun deleteSlotsOf(date: String)

    @Query("DELETE FROM day_steps")
    suspend fun deleteAllDays()

    @Query("DELETE FROM day_slots")
    suspend fun deleteAllSlots()

    @Query("DELETE FROM sync_state")
    suspend fun deleteSyncState()
}
