package xx.steps.ui

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Top-bar-to-History commands. The buttons live in the activity's top bar while the tree's
 * expansion state lives in the History screen, so taps are forwarded through this process-wide
 * flow rather than lifting the whole tree state up.
 */
object HistoryCommands {
    /**
     * OPEN_TODAY lays the tree out by month and opens today's month; SHOW_WEEKS lays it out by
     * week with this year's weeks closed; COLLAPSE_ALL closes everything in whichever layout.
     */
    enum class Command { OPEN_TODAY, SHOW_WEEKS, COLLAPSE_ALL }

    val commands = MutableSharedFlow<Command>(extraBufferCapacity = 1)

    fun send(command: Command) {
        commands.tryEmit(command)
    }
}
