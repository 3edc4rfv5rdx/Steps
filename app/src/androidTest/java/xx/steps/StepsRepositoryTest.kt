package xx.steps

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xx.steps.data.AppDatabase
import xx.steps.data.StepsRepository
import java.time.LocalDate

/** The repository's side of counting: accumulating a day, pinning its goal, moving the baseline. */
@RunWith(AndroidJUnit4::class)
class StepsRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: StepsRepository

    private val today = LocalDate.of(2026, 8, 25)
    private val yesterday = today.minusDays(1)

    /** Two hours of uptime; readings step forward from here in the worker's own 15-minute stride. */
    private var uptime = 2 * 60 * 60 * 1000L

    /**
     * Advances by one sync interval. Deltas in these tests stay within what a person can walk in
     * that time — the counting rule caps anything faster than four steps a second, and a test that
     * ignored that would be asserting on clipped numbers.
     */
    private fun nextUptime(): Long {
        uptime += 15 * 60 * 1000L
        return uptime
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = StepsRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun firstReadingWritesNoDayAtAll() = runBlocking {
        repository.recordReading(rawCount = 5_000, goal = 8_000, today = today, uptimeMillis = nextUptime())

        // The baseline is set, but the counter's pre-existing total is not anybody's step count.
        assertNull(repository.observeDay(today).first())
    }

    @Test
    fun successiveReadingsAccumulateIntoOneDay() = runBlocking {
        repository.recordReading(5_000, goal = 8_000, today = today, uptimeMillis = nextUptime())
        repository.recordReading(5_300, goal = 8_000, today = today, uptimeMillis = nextUptime())
        repository.recordReading(5_450, goal = 8_000, today = today, uptimeMillis = nextUptime())

        val day = repository.observeDay(today).first()
        assertEquals(450, day?.steps)
        assertEquals(8_000, day?.goal)
    }

    @Test
    fun eachDayKeepsTheGoalItWasCreatedWith() = runBlocking {
        repository.recordReading(1_000, goal = 8_000, today = yesterday, uptimeMillis = nextUptime())
        repository.recordReading(2_500, goal = 8_000, today = yesterday, uptimeMillis = nextUptime())
        repository.recordReading(3_200, goal = 12_000, today = today, uptimeMillis = nextUptime())

        // Raising the goal today must not retroactively make yesterday a harder day.
        assertEquals(8_000, repository.observeDay(yesterday).first()?.goal)
        assertEquals(12_000, repository.observeDay(today).first()?.goal)
    }

    @Test
    fun changingTheGoalRetargetsTodayOnly() = runBlocking {
        repository.recordReading(1_000, goal = 8_000, today = yesterday, uptimeMillis = nextUptime())
        repository.recordReading(2_400, goal = 8_000, today = yesterday, uptimeMillis = nextUptime())
        repository.recordReading(2_900, goal = 8_000, today = today, uptimeMillis = nextUptime())

        repository.applyGoalToToday(goal = 10_000, today = today)

        assertEquals(10_000, repository.observeDay(today).first()?.goal)
        assertEquals(8_000, repository.observeDay(yesterday).first()?.goal)
    }

    @Test
    fun stepsAfterMidnightGoToTheNewDay() = runBlocking {
        repository.recordReading(1_000, goal = 8_000, today = yesterday, uptimeMillis = nextUptime())
        repository.recordReading(2_500, goal = 8_000, today = yesterday, uptimeMillis = nextUptime())
        repository.recordReading(2_700, goal = 8_000, today = today, uptimeMillis = nextUptime())

        assertEquals(1_500, repository.observeDay(yesterday).first()?.steps)
        assertEquals(200, repository.observeDay(today).first()?.steps)
    }

    @Test
    fun aRebootDoesNotResetTheDayAlreadyRecorded() = runBlocking {
        repository.recordReading(1_000, goal = 8_000, today = today, uptimeMillis = nextUptime())
        repository.recordReading(2_500, goal = 8_000, today = today, uptimeMillis = nextUptime())

        // Phone restarts: uptime falls back to a minute after boot and the counter starts over.
        repository.recordReading(120, goal = 8_000, today = today, uptimeMillis = 60_000)

        assertEquals(1_620, repository.observeDay(today).first()?.steps)
    }

    @Test
    fun repeatingTheSameReadingAddsNothing() = runBlocking {
        repository.recordReading(1_000, goal = 8_000, today = today, uptimeMillis = nextUptime())
        repository.recordReading(1_400, goal = 8_000, today = today, uptimeMillis = nextUptime())
        // The worker and the open screen can read the very same counter value.
        repository.recordReading(1_400, goal = 8_000, today = today, uptimeMillis = nextUptime())

        assertEquals(400, repository.observeDay(today).first()?.steps)
    }

    @Test
    fun pausedReadingsAreConsumedButNotCounted() = runBlocking {
        repository.recordReading(1_000, goal = 8_000, today = today, uptimeMillis = nextUptime())
        repository.recordReading(2_000, goal = 8_000, today = today, uptimeMillis = nextUptime())

        // On the bus: the counter keeps climbing, the app keeps reading, nothing is credited.
        repository.recordReading(2_600, goal = 8_000, today = today, uptimeMillis = nextUptime(), credit = false)
        repository.recordReading(3_100, goal = 8_000, today = today, uptimeMillis = nextUptime(), credit = false)

        // Off the bus: walking counts again, and the ride's 1 100 steps never arrive.
        repository.recordReading(3_400, goal = 8_000, today = today, uptimeMillis = nextUptime())

        assertEquals(1_300, repository.observeDay(today).first()?.steps)
    }

    @Test
    fun aPauseWithNoReadingsStillDiscardsTheRide() = runBlocking {
        repository.recordReading(1_000, goal = 8_000, today = today, uptimeMillis = nextUptime())
        repository.recordReading(2_000, goal = 8_000, today = today, uptimeMillis = nextUptime())

        // The screen was off for the whole ride, so the first reading after it is the one that
        // carries those steps — and it is the one that must not count them.
        repository.recordReading(5_000, goal = 8_000, today = today, uptimeMillis = nextUptime(), credit = false)
        repository.recordReading(5_200, goal = 8_000, today = today, uptimeMillis = nextUptime())

        assertEquals(1_200, repository.observeDay(today).first()?.steps)
    }

    @Test
    fun clearingWipesDaysAndTheBaseline() = runBlocking {
        repository.recordReading(1_000, goal = 8_000, today = today, uptimeMillis = nextUptime())
        repository.recordReading(2_000, goal = 8_000, today = today, uptimeMillis = nextUptime())

        repository.clearAll()

        assertNull(repository.observeDay(today).first())
        // With the baseline gone the next reading is a first reading again: it credits nothing.
        repository.recordReading(9_000, goal = 8_000, today = today, uptimeMillis = nextUptime())
        assertNull(repository.observeDay(today).first())
    }

    @Test
    fun historyIsReturnedNewestFirst() = runBlocking {
        repository.recordReading(1_000, goal = 8_000, today = yesterday, uptimeMillis = nextUptime())
        repository.recordReading(2_000, goal = 8_000, today = yesterday, uptimeMillis = nextUptime())
        repository.recordReading(2_400, goal = 8_000, today = today, uptimeMillis = nextUptime())

        val days = repository.observeAll().first()
        assertEquals(listOf(today.toIso(), yesterday.toIso()), days.map { it.date })
    }
}
