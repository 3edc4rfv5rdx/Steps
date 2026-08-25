package xx.steps.ui

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Top-bar-to-History commands. The buttons live in the activity's top bar while the tree's
 * expansion state lives in the History screen, so taps are forwarded through this process-wide
 * flow rather than lifting the whole tree state up.
 */
object HistoryCommands {
    enum class Command { OPEN_TODAY, COLLAPSE_ALL }

    val commands = MutableSharedFlow<Command>(extraBufferCapacity = 1)

    fun send(command: Command) {
        commands.tryEmit(command)
    }
}
