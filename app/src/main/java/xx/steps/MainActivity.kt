package xx.steps

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.about.About
import dev.about.AboutConfig
import dev.updater.Updater
import dev.updater.UpdaterConfig
import xx.steps.BuildConfig
import xx.steps.data.StepsRepository
import xx.steps.settings.AppSettings
import xx.steps.settings.batteryExemptionIntent
import xx.steps.settings.isIgnoringBatteryOptimizations
import xx.steps.steps.DemoSteps
import xx.steps.steps.PermissionAsk
import xx.steps.steps.StepAccessState
import xx.steps.steps.hasStepPermission
import xx.steps.steps.nextPermissionAsk
import xx.steps.work.StepsService
import xx.steps.ui.ConfirmDialog
import xx.steps.ui.HistoryCommands
import xx.steps.ui.HistoryScreen
import xx.steps.ui.ScreenWork
import xx.steps.ui.StatusBanner
import xx.steps.ui.NavLabelStyle
import xx.steps.ui.StepsTheme
import xx.steps.ui.WindowDark
import xx.steps.ui.WindowLight
import xx.steps.ui.isDarkTheme
import xx.steps.ui.SettingsScreen
import xx.steps.ui.TodayScreen

/**
 * One bar across the top of every screen: the current tab's name, plus whatever that tab can do —
 * only History has actions, and they reach it through [HistoryCommands].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(tab: Tab, demo: Boolean, onDemo: () -> Unit, onAbout: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(tab.labelRes)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
        actions = {
            // The demo is offered on an emulator only — on a real phone it could do nothing but
            // wipe the history by accident. Lit while it runs, dimmed while it does not, so the
            // one button both starts it and says whether it is on.
            if (tab == Tab.TODAY && DemoSteps.isEmulator) {
                IconButton(onClick = onDemo) {
                    Icon(
                        imageVector = Icons.Filled.Science,
                        contentDescription = stringResource(
                            if (demo) R.string.demo_stop else R.string.demo_start,
                        ),
                        tint = Color.White.copy(alpha = if (demo) 1f else DIMMED_ACTION_ALPHA),
                    )
                }
            }
            if (tab == Tab.SETTINGS) {
                IconButton(onClick = onAbout) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = stringResource(R.string.about_title),
                    )
                }
            }
            if (tab == Tab.HISTORY) {
                IconButton(onClick = { HistoryCommands.send(HistoryCommands.Command.OPEN_TODAY) }) {
                    Icon(
                        imageVector = Icons.Filled.Today,
                        contentDescription = stringResource(R.string.action_today),
                    )
                }
                IconButton(onClick = { HistoryCommands.send(HistoryCommands.Command.COLLAPSE_ALL) }) {
                    Icon(
                        imageVector = Icons.Filled.UnfoldLess,
                        contentDescription = stringResource(R.string.action_collapse),
                    )
                }
            }
        },
    )
}

/** How far down a top-bar action is turned when it stands for something that is off. */
private const val DIMMED_ACTION_ALPHA = 0.45f

/** The three tabs, in bottom-bar order. */
private enum class Tab(val labelRes: Int) {
    TODAY(R.string.tab_today),
    HISTORY(R.string.tab_history),
    SETTINGS(R.string.tab_settings),
}

/**
 * Today wears the pedestrian cut from the crossing sign — the same figure as the launcher icon, so
 * the tab and the app read as one thing. The other two keep Material glyphs.
 */
@Composable
private fun TabIcon(tab: Tab) {
    when (tab) {
        Tab.TODAY -> Icon(
            painter = painterResource(R.drawable.ic_walker),
            contentDescription = null,
        )
        Tab.HISTORY -> Icon(Icons.Filled.CalendarMonth, contentDescription = null)
        Tab.SETTINGS -> Icon(Icons.Filled.Settings, contentDescription = null)
    }
}

// One description of this app's release, for the silent check at start-up and
// the About dialog's button alike.
val UPDATER_CONFIG = UpdaterConfig(appKey = "steps", repo = "Steps")

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Looks for a newer build in this app's own GitHub release and asks
        // before it downloads anything. Silent when there is nothing newer or
        // GitHub cannot be reached.
        Updater.checkOnStart(this, UPDATER_CONFIG)
        applyWindowTheme()
        StepAccessState.refresh(this)

        // The permission is asked for from the Today screen, next to the sentence explaining what
        // it is for — a dialog thrown at a screen the user has not seen yet only gets dismissed.
        //
        // The readings themselves are not started here: they belong to the process, not to this
        // screen — see StepCounting for why the sensor has to stay registered while the phone is
        // in a pocket.

        setContent {
            val themeMode by AppSettings.themeMode.collectAsState()
            val accentIndex by AppSettings.accentIndex.collectAsState()

            // The window is outside the composition and does not follow it: a theme changed in
            // Settings has to be carried back out to the bars by hand.
            LaunchedEffect(themeMode) { applyWindowTheme() }

            StepsTheme(themeMode = themeMode, accentIndex = accentIndex) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    /**
     * The platform side of the window: the background it shows before Compose has drawn anything,
     * and the colour the system bar icons are drawn for.
     *
     * Both used to be decided by the system's night setting alone — `Theme.Steps` named the light
     * platform theme outright, and `enableEdgeToEdge()` with no arguments styles the bars from the
     * resource configuration. With the app set to Dark on a phone in light mode that is the wrong
     * answer twice: a white window ahead of the first frame, and status-bar icons drawn for a light
     * background over a near-black one. `values-night/themes.xml` settles the platform theme for
     * the case where the app follows the system; this settles the other two cases.
     */
    private fun applyWindowTheme() {
        val systemInDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = isDarkTheme(AppSettings.themeMode.value, systemInDark)
        window.setBackgroundDrawable((if (dark) WindowDark else WindowLight).toArgb().toDrawable())
        // Android 15 draws every app edge to edge whether it asks or not; saying so explicitly
        // means the same layout on 13 and 14, where the system bars would otherwise take their own
        // space and the two would differ. The styles are named rather than left to auto(), which
        // reads the system setting the app is allowed to override.
        val style = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onStart() {
        super.onStart()
        // The permission can be revoked from system settings while the app sits in the background.
        StepAccessState.refresh(this)
        // And granted there too — or in the dialog the Today screen puts up, which stops this
        // activity and starts it again. Every way back into the app passes through here, so this
        // is where the service is caught up with a permission that was not there at onCreate.
        // Starting a running one is a no-op the system absorbs.
        StepsService.start(this)
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    // The About dialog is the platform's, drawn by the shared module, so it needs
    // the activity rather than a context.
    val activity = LocalActivity.current
    // The demo switch outlives this composition, so neither the activity's resources nor the
    // activity itself goes into it — the same reason SettingsScreen's jobs take both from the
    // application.
    val appContext = context.applicationContext
    val resources = appContext.resources
    val repository = remember(context) { StepsRepository.get(context) }
    val demo by AppSettings.demoMode.collectAsState()
    val goal by AppSettings.goal.collectAsState()

    // The questions that follow the user allowing activity data live here rather than on the button
    // that starts them: that button disappears the moment the permission is granted, and a launcher
    // disposed mid-chain never delivers the answer that would carry the chain on. This composable
    // stands for as long as the activity does.
    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    // The count lives in the service's notification, which Android 13 will not show without this.
    fun canPostNotifications(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun askNotifications() {
        if (!canPostNotifications()) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Nothing to do with the exemption's own answer — it is read again wherever it is shown. What
    // matters is that the user is back in the app, which is when the next question can be put.
    val battery = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { askNotifications() }

    /**
     * Everything that follows the user allowing counting, in order: the service, then whichever
     * question is still open. Remembered rather than referenced, so the screen it is handed to is
     * not rebuilt on every recomposition by a lambda that only looks new.
     */
    val onCountingAllowed: () -> Unit = remember(context, battery, notifications) {
        {
            // Counting only survives a locked screen while the service is up, and this is the
            // moment the app first becomes able to count. onStart catches it too, on the phones
            // where the permission dialog stops this activity; on the ones where it does not,
            // this is the only catch there is.
            StepsService.start(context)
            when (nextPermissionAsk(isIgnoringBatteryOptimizations(context), canPostNotifications())) {
                // A phone with no such screen is left alone rather than crashed on an intent it
                // cannot resolve — and it still gets asked the question that comes after.
                PermissionAsk.BATTERY_EXEMPTION ->
                    runCatching { battery.launch(batteryExemptionIntent(context)) }
                        .onFailure { askNotifications() }

                PermissionAsk.NOTIFICATIONS -> askNotifications()
                PermissionAsk.NOTHING -> Unit
            }
        }
    }

    // The app opening with counting already allowed: no dialog is in flight, so the one question
    // that may still be open can be put straight away.
    LaunchedEffect(Unit) {
        if (hasStepPermission(context)) askNotifications()
    }

    // The one banner in the app, and it belongs here rather than to a screen: the jobs are owned by
    // the process, and the tab the user is on when one of them is refused or fails is not
    // necessarily the tab it was started from.
    val banner by ScreenWork.message.collectAsState()

    var current by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var confirmDemo by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                tab = current,
                demo = demo,
                onDemo = { confirmDemo = true },
                // The shared About dialog: it reads the name and the version off
                // the package and the GitHub address out of the updater config,
                // so only the build date is handed over.
                onAbout = {
                    activity?.let {
                        About.show(
                            it,
                            AboutConfig(
                                updater = UPDATER_CONFIG,
                                buildDate = BuildConfig.BUILD_DATE,
                            ),
                        )
                    }
                },
            )
        },
        bottomBar = {
            // On a phone with on-screen back/home buttons the bar sits under them unless it is
            // given that inset; with gesture navigation the same inset is a thin strip.
            NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == current,
                        onClick = { current = tab },
                        icon = { TabIcon(tab) },
                        label = { Text(stringResource(tab.labelRes), style = NavLabelStyle) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (current) {
                Tab.TODAY -> TodayScreen(onCountingAllowed = onCountingAllowed)
                Tab.HISTORY -> HistoryScreen()
                Tab.SETTINGS -> SettingsScreen()
            }
            banner?.let { message ->
                StatusBanner(
                    message = message,
                    onDismiss = { ScreenWork.clear() },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }


    // Either direction wipes the database, so either direction asks first.
    if (confirmDemo) {
        ConfirmDialog(
            title = stringResource(R.string.demo_wipe_title),
            message = stringResource(R.string.demo_wipe_message),
            onDismiss = { confirmDemo = false },
            onConfirm = {
                confirmDemo = false
                // Not this composition's scope: the switch empties the database and refills it,
                // and an activity recreated partway must not be able to stop that. It takes its
                // turn in ScreenWork like the import and the restore do — the two write the same
                // table, and whichever finished second used to decide what was in it.
                ScreenWork.run(
                    failureText = resources.getString(R.string.demo_failed),
                    busyText = resources.getString(R.string.work_busy),
                ) {
                    DemoSteps.toggle(appContext, repository, turnOn = !demo, goal = goal)
                    null
                }
            },
        )
    }
}
