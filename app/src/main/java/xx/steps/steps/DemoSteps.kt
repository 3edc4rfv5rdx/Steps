package xx.steps.steps

import android.content.Context
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xx.steps.data.StepsRepository
import xx.steps.settings.AppSettings
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

    /** Range the seeded days fall in — some short, some well past a normal goal. */
    private const val SEED_MIN_STEPS = 1_200
    private const val SEED_MAX_STEPS = 15_500

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
     * Turns the demo on or off. Either direction wipes the database first: a demo run and real
     * history must never share one, and on the way out its made-up days have to go. This is the
     * only place that sequence lives — both screens that offer the demo call it.
     */
    suspend fun toggle(context: Context, repository: StepsRepository, turnOn: Boolean, goal: Int) {
        repository.clearAll()
        if (turnOn) seedHistory(repository, goal)
        AppSettings.setDemoMode(context, turnOn)
        StepAccessState.refresh(context)
    }

    /**
     * Fills the past [days] with plausible days, leaving today alone for the live demo counter to
     * fill. Every fourth day is a lazy one, so the history has visibly missed goals in it.
     */
    suspend fun seedHistory(repository: StepsRepository, goal: Int, days: Int = DEMO_HISTORY_DAYS) {
        val today = LocalDate.now()
        for (back in 1..days) {
            val date = today.minusDays(back.toLong())
            val steps = if (back % 4 == 0) {
                Random.nextInt(SEED_MIN_STEPS, goal)
            } else {
                Random.nextInt(goal, SEED_MAX_STEPS)
            }
            repository.setDay(date = date, steps = steps, goal = goal)
        }
    }
}
