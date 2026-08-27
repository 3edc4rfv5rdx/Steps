package xx.steps.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The long jobs a screen starts — export, import, restore, switching the demo — and the one thing
 * they have to say afterwards.
 *
 * Both live in the process rather than in the screen. A screen is a branch of a `when` on the
 * selected tab, so switching tabs takes its composition with it, and with it the scope the job was
 * running in: an operation the user confirmed would be cancelled halfway with nothing said about
 * it. Here the job runs to the end and its message waits for whoever looks next. This is the only
 * place such work is started from — a composition is never allowed to own it.
 *
 * Nothing is persisted. A message need only survive a tab switch, not a process death — a restore
 * whose process is gone has a database to speak for it.
 */
object ScreenWork {

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
        scope.launch {
            try {
                job()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                report(failure)
            }
        }
    }

    /**
     * Runs [job] and keeps what it answers, unless one is already running — in which case nothing
     * starts, [busyText] is left in an amber banner and false comes back, so a caller can also keep
     * a dialog open rather than appear to have done it.
     *
     * The flag is cleared in a `finally`: a job that throws must not leave the screen refusing
     * every operation after it for the life of the process.
     *
     * A job that throws answers [failureText] in a red banner. Both texts are parameters, and
     * required ones, because the job cannot say anything once it has thrown or has never started,
     * and a caller that forgot either used to leave the operation silent: the picked file that no
     * longer resolves, the lapsed grant, the restore confirmed while an export is still writing.
     *
     * A job that answers null has nothing to say — the demo switch, whose whole result is the
     * screen behind the dialog — and leaves whatever banner was already there alone.
     */
    fun run(failureText: String, busyText: String, job: suspend () -> BannerMessage?): Boolean {
        if (!_running.compareAndSet(expect = false, update = true)) {
            _message.value = BannerMessage(BannerKind.WARNING, busyText)
            return false
        }
        scope.launch {
            try {
                job()?.let { _message.value = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                report(failure)
                _message.value = BannerMessage(BannerKind.ERROR, failureText)
            } finally {
                _running.value = false
            }
        }
        return true
    }

    /**
     * Where a failed job goes on its way to a banner. `System.err` rather than `android.util.Log`:
     * this object is deliberately free of Android, which is what lets its whole contract — the
     * refusal, the message, the failure — be pinned by plain JVM tests. Android routes `System.err`
     * to logcat anyway.
     */
    private fun report(failure: Throwable) {
        failure.printStackTrace()
    }
}
