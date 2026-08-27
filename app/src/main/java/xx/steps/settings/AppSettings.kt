package xx.steps.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xx.steps.DEFAULT_GOAL
import xx.steps.SHOW_JOURNAL_SETTING
import xx.steps.DEFAULT_STEP_LENGTH_CM
import xx.steps.PREFS_NAME
import xx.steps.clampGoal
import xx.steps.clampStepLength
import xx.steps.ui.AccentPalette
import xx.steps.ui.DEFAULT_ACCENT_INDEX

/**
 * Process-wide app settings, backed by SharedPreferences and exposed as [StateFlow] for Compose.
 * Theme, accent and language join the goal here later.
 *
 * The sensor sync state deliberately lives in the database instead: it has to be written in the
 * same transaction as the steps it accounts for.
 */
object AppSettings {
    private const val KEY_GOAL = "daily_goal"
    private const val KEY_PAUSED = "counting_paused"
    private const val KEY_DEMO = "demo_mode"
    private const val KEY_STEP_LENGTH = "step_length_cm"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_ACCENT = "accent_index"
    private const val KEY_JOURNAL = "journal_enabled"

    private val _goal = MutableStateFlow(DEFAULT_GOAL)

    /** Daily step goal; new day rows are stamped with whatever this holds at the time. */
    val goal: StateFlow<Int> = _goal.asStateFlow()

    private val _paused = MutableStateFlow(false)

    /**
     * While paused, readings still move the baseline but credit nothing — the point of the pause
     * is to throw away what the counter picks up on a bus. It persists, because a pause the user
     * set has to survive the app being swapped out of memory.
     */
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _stepLengthCm = MutableStateFlow(DEFAULT_STEP_LENGTH_CM)

    /** Step length in centimetres, the only input the distance readout has. */
    val stepLengthCm: StateFlow<Int> = _stepLengthCm.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)

    /** Light, dark, or whatever the phone is set to. */
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentIndex = MutableStateFlow(DEFAULT_ACCENT_INDEX)

    /** Index into the accent palette; the ring, the bars and the buttons all take their color from it. */
    val accentIndex: StateFlow<Int> = _accentIndex.asStateFlow()

    private val _demoMode = MutableStateFlow(false)

    /**
     * Feeds the app a simulated counter instead of the hardware one, so the interface can be seen
     * on an emulator or a phone without a step sensor. Off unless the user turns it on.
     */
    val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    private val _journalEnabled = MutableStateFlow(false)

    /**
     * Whether the counting journal is written. Off by default, and its switch is hidden — see
     * [SHOW_JOURNAL_SETTING]. It is a diagnostic for a phone that is miscounting, not something an
     * app that counts correctly should be writing a line per reading for; turning it on is a
     * deliberate act, and it stays on until turned off again.
     */
    val journalEnabled: StateFlow<Boolean> = _journalEnabled.asStateFlow()

    /** Load persisted settings into memory. Call once at startup before the UI reads them. */
    fun load(context: Context) {
        val prefs = prefs(context)
        val stored = prefs.getInt(KEY_GOAL, DEFAULT_GOAL)
        _goal.value = clampGoal(stored)
        _paused.value = prefs.getBoolean(KEY_PAUSED, false)
        _demoMode.value = prefs.getBoolean(KEY_DEMO, false)
        _stepLengthCm.value = clampStepLength(prefs.getInt(KEY_STEP_LENGTH, DEFAULT_STEP_LENGTH_CM))
        _themeMode.value = prefs.getString(KEY_THEME, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        _accentIndex.value = prefs.getInt(KEY_ACCENT, DEFAULT_ACCENT_INDEX)
            .coerceIn(AccentPalette.indices)
        _journalEnabled.value = prefs.getBoolean(KEY_JOURNAL, false)
        if (stored != _goal.value) {
            prefs.edit { putInt(KEY_GOAL, _goal.value) }
        }
    }

    fun setGoal(context: Context, steps: Int) {
        val clamped = clampGoal(steps)
        _goal.value = clamped
        prefs(context).edit { putInt(KEY_GOAL, clamped) }
    }

    fun setPaused(context: Context, paused: Boolean) {
        _paused.value = paused
        prefs(context).edit { putBoolean(KEY_PAUSED, paused) }
    }

    fun setStepLengthCm(context: Context, cm: Int) {
        val clamped = clampStepLength(cm)
        _stepLengthCm.value = clamped
        prefs(context).edit { putInt(KEY_STEP_LENGTH, clamped) }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        prefs(context).edit { putString(KEY_THEME, mode.name) }
    }

    /** Prefer StepLog.setEnabled, which notes the change in the journal on the way past. */
    fun setJournalEnabled(context: Context, enabled: Boolean) {
        _journalEnabled.value = enabled
        prefs(context).edit { putBoolean(KEY_JOURNAL, enabled) }
    }

    fun setAccentIndex(context: Context, index: Int) {
        val clamped = index.coerceIn(AccentPalette.indices)
        _accentIndex.value = clamped
        prefs(context).edit { putInt(KEY_ACCENT, clamped) }
    }

    fun setDemoMode(context: Context, enabled: Boolean) {
        _demoMode.value = enabled
        prefs(context).edit { putBoolean(KEY_DEMO, enabled) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
