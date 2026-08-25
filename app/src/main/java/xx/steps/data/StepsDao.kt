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

    @Query("DELETE FROM day_steps")
    suspend fun deleteAllDays()

    @Query("DELETE FROM sync_state")
    suspend fun deleteSyncState()
}
