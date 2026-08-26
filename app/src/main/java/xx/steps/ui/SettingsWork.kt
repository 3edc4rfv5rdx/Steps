package xx.steps.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The long jobs the Settings screen starts — export, import, restore — and the one thing they have
 * to say afterwards.
 *
 * Both live in the process rather than in the screen. The Settings screen is one branch of a `when`
 * on the selected tab, so switching to Today takes its composition with it, and with it the scope
 * an import was running in: an operation the user confirmed would be cancelled halfway with nothing
 * said about it. Here the job runs to the end and its message waits for whoever looks next.
 *
 * Nothing is persisted. A message need only survive a tab switch, not a process death — a restore
 * whose process is gone has a database to speak for it.
 */
object SettingsWork {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _running = MutableStateFlow(false)

    /** Whether a job is in flight. One at a time: a second import over the first is nobody's intent. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _message = MutableStateFlow<BannerMessage?>(null)

    /** What the last finished job had to say, until a screen shows it and clears it. */
    val message: StateFlow<BannerMessage?> = _message.asStateFlow()

    /** An outcome with no job behind it — a row that could not open the screen it leads to. */
    fun show(message: BannerMessage) {
        _message.value = message
    }

    fun clear() {
        _message.value = null
    }

    /**
     * A job with nothing to say afterwards, run in the same scope for the same reason: retargeting
     * today's goal is a single write, but a tab switch a moment after the dialog closed would
     * cancel it just the same. It takes no turn in [running] — it is not an operation the user
     * waits on, and two of them in a row settle on the same answer.
     */
    fun launch(job: suspend () -> Unit) {
        scope.launch { job() }
    }

    /**
     * Runs [job] and keeps what it answers, unless one is already running — in which case nothing
     * starts and false comes back, so a caller can say so rather than appear to have done it.
     *
     * The flag is cleared in a `finally`: a job that throws must not leave the screen refusing
     * every operation after it for the life of the process.
     */
    fun run(job: suspend () -> BannerMessage): Boolean {
        if (!_running.compareAndSet(expect = false, update = true)) return false
        scope.launch {
            try {
                _message.value = job()
            } finally {
                _running.value = false
            }
        }
        return true
    }
}
