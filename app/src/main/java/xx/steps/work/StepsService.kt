package xx.steps.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import xx.steps.DATE_TICK_MS
import xx.steps.MainActivity
import xx.steps.R
import xx.steps.data.StepsRepository
import xx.steps.distanceLabel
import xx.steps.formatSteps
import xx.steps.logSteps
import xx.steps.settings.AppSettings
import xx.steps.steps.hasStepPermission
import java.time.LocalDate

/**
 * Keeps the app in the foreground so that its sensor registration keeps delivering.
 *
 * This is not an optimisation. Android stops delivering sensor events to an app whose UID has gone
 * idle, and it does so without unregistering anything: the registration stays in the sensor
 * service, marked `disabled`, and the steps walked meanwhile are never handed over. A foreground
 * service is what keeps the UID active, and there is no other way to read the counter from a
 * pocket — the step counter has no wake-up variant on this hardware, and the detector falls under
 * the same rule.
 *
 * The service does not read the sensor itself. [xx.steps.steps.StepCounting] holds the one
 * registration there is; this only keeps the process in a state where that registration works, and
 * puts today's total where the user can see it without unlocking anything.
 */
class StepsService : Service() {

    /**
     * The main thread, named rather than left to the default. Everything this scope does is format
     * one line and hand it to the notification manager, and the only other toucher of that line is
     * a broadcast receiver, which the framework calls on the main thread: one thread for both is
     * what makes [line] a plain field and not a question about visibility between threads.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * The readout as it stands, so a dismissed notification can be put back showing it. Filled in
     * onCreate rather than here: building it needs resources, which a field initialiser runs too
     * early to have.
     *
     * Confined to the main thread — written in onCreate and by the collector above, read by the
     * dismissal receiver. Move that collector off the main thread and this needs publishing safely.
     */
    private var line: String = ""

    /**
     * Android 14 lets the user swipe away a foreground service's notification whatever ONGOING and
     * NO_CLEAR say. Only the card goes; the service keeps running and the steps keep being counted
     * — but the count is then invisible, and this notification is also the one honest sign that the
     * app is holding a sensor open. So it goes back up.
     */
    private val dismissed = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            logSteps("service: notification dismissed, posting it again")
            notificationManager()?.notify(NOTIFICATION_ID, notification(line))
        }
    }

    override fun onCreate() {
        super.onCreate()
        logSteps("service: starting")
        line = readout(steps = 0, stepLengthCm = AppSettings.stepLengthCm.value)
        createChannel()
        // Posted before anything else can go wrong: a foreground service that has not called
        // startForeground in time is killed by the system with an ANR-shaped crash. The type is
        // named explicitly because Android 14 refuses a foreground service that does not declare
        // one; "health" is what a step count is, and it is what the manifest declares.
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(line), foregroundType())
        ContextCompat.registerReceiver(
            this,
            dismissed,
            IntentFilter(ACTION_DISMISSED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        watchToday()
    }

    /**
     * Keeps the notification showing today's total. The day is re-read on a tick rather than fixed
     * at start, so a service running through midnight follows the date over instead of going on
     * showing yesterday.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun watchToday() {
        val repository = StepsRepository.get(applicationContext)
        val days = flow {
            while (true) {
                emit(LocalDate.now())
                delay(DATE_TICK_MS)
            }
        }.distinctUntilChanged()

        scope.launch {
            days.flatMapLatest { day -> repository.observeDay(day) }
                .combine(AppSettings.stepLengthCm) { day, stepLength ->
                    readout(steps = day?.steps ?: 0, stepLengthCm = stepLength)
                }
                .distinctUntilChanged()
                .collect { current ->
                    line = current
                    notificationManager()?.notify(NOTIFICATION_ID, notification(current))
                }
        }
    }

    /** Restarted by the system after a kill: there is nothing in the intent worth keeping. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        logSteps("service: stopping")
        unregisterReceiver(dismissed)
        scope.cancel()
        super.onDestroy()
    }

    /**
     * The whole notification in one line: what the app is called, then today's steps and the
     * distance they come to. The two numbers are joined the way the totals card joins them, so the
     * notification and the screen never word the same pair differently.
     */
    private fun readout(steps: Int, stepLengthCm: Int): String =
        getString(R.string.app_name) + " · " + formatSteps(steps) +
            " / " + distanceLabel(this, steps, stepLengthCm)

    /**
     * The service's type, which Android 14 and later require to be stated at startForeground and to
     * match the manifest. "health" is what a step count is, but it only exists from API 34; on 33
     * the nearest type that does exist is dataSync, and the manifest declares both.
     */
    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }

    /**
     * [line] carries the whole readout, steps and distance together. There is no second line and no
     * style attached to the builder: a notification with nothing below the title has nothing to
     * expand into, so it stays the one row it is meant to be.
     */
    private fun notification(line: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_walker)
            .setContentTitle(line)
            .setContentIntent(open)
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    this,
                    0,
                    Intent(ACTION_DISMISSED).setPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            // Ongoing and silent: it is a readout, not an event. Without setShowWhen(false) it
            // would also carry the time it was posted, which means nothing here.
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            // Low: it must be visible, since that visibility is what keeps counting alive, but it
            // has nothing to announce.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
        }
        notificationManager()?.createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager? = getSystemService()

    companion object {
        private const val CHANNEL_ID = "counting"

        /** Broadcast to ourselves when the notification is swiped away; nothing else may send it. */
        private const val ACTION_DISMISSED = "xx.steps.NOTIFICATION_DISMISSED"
        private const val NOTIFICATION_ID = 1

        /**
         * Starts the service if it is not already running; starting a running one is a no-op the
         * system absorbs, so callers need not track it.
         */
        fun start(context: Context) {
            // A health-typed foreground service without the activity permission is a SecurityException
            // at startForeground, so the one check lives here rather than at each call site.
            if (!hasStepPermission(context)) {
                logSteps("service: not started, no permission")
                return
            }
            // Starting a foreground service from the background is allowed here because the app is
            // exempt from battery optimisation; revoke that exemption and the same call throws.
            // Losing the service is bad, but taking the worker down with it is worse — it is the
            // worker that gets another chance at starting it.
            runCatching {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, StepsService::class.java),
                )
            }.onFailure { logSteps("service: could not start — $it") }
        }
    }
}
