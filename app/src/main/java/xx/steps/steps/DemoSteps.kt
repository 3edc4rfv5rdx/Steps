package xx.steps.steps

import android.content.Context
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xx.steps.MAX_GOAL
import xx.steps.MINUTES_PER_HOUR
import xx.steps.MIN_GOAL
import xx.steps.SLOTS_PER_DAY
import xx.steps.SLOT_MINUTES
import xx.steps.clampGoal
import xx.steps.data.DaySlot
import xx.steps.data.DaySteps
import xx.steps.data.StepsRepository
import xx.steps.settings.AppSettings
import xx.steps.toIso
import java.time.LocalDate
import kotlin.random.Random

/**
 * A stand-in for the hardware counter, so the whole interface can be looked at on an emulator or
 * on any phone without a step sensor. It is switched on by hand and persists like any setting —
 * nothing here ever runs unless the user asks for it.
 */
object DemoSteps {

    /**
     * True on an emulator. The demo exists to look at the interface where there is no step
     * counter, which is exactly an emulator; on a real phone it would only be a way to wipe the
     * history by accident, so it is not offered there at all.
     *
     * The checks are the usual fingerprint tells — Google's images report generic builds and the
     * goldfish/ranchu virtual hardware.
     */
    val isEmulator: Boolean by lazy {
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("vbox") ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.PRODUCT.startsWith("sdk") ||
            Build.HARDWARE == "goldfish" ||
            Build.HARDWARE == "ranchu"
    }

    /** Where the fake counter starts, mimicking a phone that has been up for a while. */
    private const val DEMO_BASELINE = 20_000L

    /** How often the demo walker takes a few more steps. */
    private const val DEMO_TICK_MS = 1_500L

    private const val DEMO_MIN_STEPS = 3
    private const val DEMO_MAX_STEPS = 14

    /** Days of history the demo seeds, enough to fill the week bars and a couple of months. */
    const val DEMO_HISTORY_DAYS = 70

    /**
     * How a seeded day is placed against the goal it is judged by: a lazy one lands between a
     * quarter of the goal and just under it, an ordinary one between the goal and twice it.
     *
     * Shares of the goal rather than fixed step counts, because the goal is the user's and runs
     * from [MIN_GOAL] to [MAX_GOAL]. Fixed bounds left one range or the other empty at the ends of
     * that scale, and an empty range is not a dull demo — it is an exception, thrown after the
     * database has already been wiped.
     */
    private const val SEED_LAZY_FLOOR_DIVISOR = 4
    private const val SEED_BEST_MULTIPLE = 2

    /**
     * Shape of a made-up day, one weight per hour: asleep until six, out to work, a walk at lunch,
     * the way home, and an evening that tails off. The seeded days need a breakdown of their own —
     * without one the day chart would be empty on the very machine the demo exists to be looked at.
     */
    private val DEMO_HOUR_WEIGHTS = intArrayOf(
        0, 0, 0, 0, 0, 1,
        4, 10, 15, 8, 6, 7,
        10, 7, 5, 6, 9, 13,
        14, 10, 6, 4, 2, 1,
    )

    /** How far a single quarter hour strays from its hour's weight, as a multiplier range. */
    private const val DEMO_JITTER_MIN = 2
    private const val DEMO_JITTER_MAX = 10

    /**
     * A walking phone: the counter climbs by a handful of steps every second and a half, exactly
     * like the real one, so everything downstream — folding, capping, the ring — is the real path.
     */
    fun readings(): Flow<Long> = flow {
        var raw = DEMO_BASELINE
        emit(raw)
        while (true) {
            delay(DEMO_TICK_MS)
            raw += Random.nextInt(DEMO_MIN_STEPS, DEMO_MAX_STEPS)
            emit(raw)
        }
    }

    /**
     * One seeded day's step count. Total by construction: [clampGoal] first, so the goal is inside
     * the bounds the editor accepts, and both ranges are then non-empty for every value in them —
     * a quarter of [MIN_GOAL] is 125, and twice any goal is above it. Nothing here can throw, which
     * matters because [toggle] has already wiped the database by the time this runs.
     *
     * [random] is a parameter so the rule can be driven from a test without a phone.
     */
    internal fun seededSteps(goal: Int, lazy: Boolean, random: Random = Random): Int {
        val target = clampGoal(goal)
        return if (lazy) {
            random.nextInt(target / SEED_LAZY_FLOOR_DIVISOR, target)
        } else {
            random.nextInt(target, target * SEED_BEST_MULTIPLE)
        }
    }

    /**
     * Spreads a made-up day's [steps] over its quarter hours along [DEMO_HOUR_WEIGHTS], jittered so
     * no two demo days look alike. The shares add up to [steps] exactly — the same distribution
     * the real breakdown is written with.
     */
    internal fun demoSlots(steps: Int): List<SlotShare> {
        val weights = IntArray(SLOTS_PER_DAY) { slot ->
            val hourly = DEMO_HOUR_WEIGHTS[slot * SLOT_MINUTES / MINUTES_PER_HOUR]
            if (hourly == 0) 0 else hourly * Random.nextInt(DEMO_JITTER_MIN, DEMO_JITTER_MAX)
        }
        return distributeOverSlots(steps, weights)
    }

    /**
     * Turns the demo on or off. Either direction empties the database of what was in it: a demo run
     * and real history must never share one, and on the way out its made-up days have to go. This
     * is the only place that sequence lives — both screens that offer the demo call it.
     *
     * The whole history is built first and written in one transaction, so the switch either happens
     * or does not. Wiping and then filling seventy days one at a time meant a cancellation partway
     * — the activity recreated by a language change, the screen gone — left the real history gone
     * and the demo half-built.
     */
    suspend fun toggle(context: Context, repository: StepsRepository, turnOn: Boolean, goal: Int) {
        if (turnOn) {
            val history = demoHistory(goal)
            repository.restoreAll(days = history.days, slots = history.slots)
        } else {
            repository.clearAll()
        }
        AppSettings.setDemoMode(context, turnOn)
        StepAccessState.refresh(context)
    }

    /** A whole demo history, built before anything is written. */
    internal data class DemoHistory(val days: List<DaySteps>, val slots: List<DaySlot>)

    /**
     * The past [days] as plausible days with their breakdown, today left alone for the live demo
     * counter to fill. Every fourth day is a lazy one, so the history has visibly missed goals in
     * it.
     *
     * Pure but for the random draw: it builds a value rather than writing rows, which is what lets
     * the switch be one transaction and lets a JVM test check it without a database.
     */
    internal fun demoHistory(
        goal: Int,
        days: Int = DEMO_HISTORY_DAYS,
        today: LocalDate = LocalDate.now(),
    ): DemoHistory {
        // Clamped once, here, so the goal the days are stamped with is the same one their step
        // counts were drawn against. Two readings of it would let a day be judged by a goal it was
        // never placed against.
        val target = clampGoal(goal)
        val dayRows = ArrayList<DaySteps>(days)
        val slotRows = ArrayList<DaySlot>()

        for (back in 1..days) {
            val iso = today.minusDays(back.toLong()).toIso()
            val steps = seededSteps(target, lazy = back % 4 == 0)
            dayRows += DaySteps(date = iso, steps = steps, goal = target)
            demoSlots(steps).forEach { slotRows += DaySlot(date = iso, slot = it.slot, steps = it.steps) }
        }

        return DemoHistory(days = dayRows, slots = slotRows)
    }
}
