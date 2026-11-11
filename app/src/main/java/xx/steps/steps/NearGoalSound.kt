package xx.steps.steps

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import xx.steps.data.RecordedReading
import xx.steps.settings.AppSettings

/**
 * The one sound the app makes: the phone's notification sound, once a day, when the steps come
 * within reach of the goal. A bare sound rather than a notification, so nothing is left behind in
 * the shade; it plays on the notification stream, so the phone's volume and silent mode apply.
 */
object NearGoalSound {

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Plays the sound if [recorded] crossed the mark and the switch in Settings allows it. */
    fun onReading(context: Context, recorded: RecordedReading) {
        if (!recorded.nearGoalReached || !AppSettings.nearGoalSound.value) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri) ?: return
        ringtone.audioAttributes = attributes
        // A sound is a nicety: a phone that refuses to play it must not take the count down with it.
        runCatching { ringtone.play() }
    }
}
