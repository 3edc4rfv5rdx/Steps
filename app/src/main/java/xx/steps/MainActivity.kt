package xx.steps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import xx.steps.data.StepsRepository
import xx.steps.settings.AppSettings
import xx.steps.steps.DemoSteps
import xx.steps.steps.StepAccess
import xx.steps.steps.StepAccessState
import xx.steps.steps.StepSensor
import xx.steps.ui.AboutDialog
import xx.steps.ui.HistoryCommands
import xx.steps.ui.HistoryScreen
import xx.steps.ui.NavLabelStyle
import xx.steps.ui.StepsTheme
import xx.steps.ui.SettingsScreen
import xx.steps.ui.TodayScreen

/**
 * One bar across the top of every screen: the current tab's name, plus whatever that tab can do —
 * only History has actions, and they reach it through [HistoryCommands].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(tab: Tab, onAbout: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(tab.labelRes)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
        actions = {
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

/** The three tabs, in bottom-bar order. */
private enum class Tab(val labelRes: Int, val icon: ImageVector) {
    TODAY(R.string.tab_today, Icons.AutoMirrored.Filled.DirectionsWalk),
    HISTORY(R.string.tab_history, Icons.Filled.CalendarMonth),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StepAccessState.refresh(this)

        // The permission is asked for from the Today screen, next to the sentence explaining what
        // it is for — a dialog thrown at a screen the user has not seen yet only gets dismissed.

        // While a screen is on, read the counter directly: the count then grows as the user walks
        // instead of jumping every quarter hour when the worker runs. Collection is tied to the
        // STARTED state, so nothing is registered with the sensor in the background, and it
        // restarts by itself the moment the permission is granted.
        val repository = StepsRepository.get(applicationContext)
        val sensor = StepSensor(applicationContext)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(StepAccessState.access, AppSettings.demoMode, ::Pair)
                    .flatMapLatest { (access, demo) ->
                        when {
                            demo -> DemoSteps.readings()
                            access == StepAccess.READY -> sensor.readings()
                            else -> emptyFlow()
                        }
                    }
                    .collect { raw ->
                        // Readings are consumed even while paused: that is what makes the steps of
                        // a bus ride disappear instead of arriving in one lump when it ends.
                        repository.recordReading(
                            rawCount = raw,
                            goal = AppSettings.goal.value,
                            credit = !AppSettings.paused.value,
                        )
                    }
            }
        }

        setContent {
            val themeMode by AppSettings.themeMode.collectAsState()
            val accentIndex by AppSettings.accentIndex.collectAsState()

            StepsTheme(themeMode = themeMode, accentIndex = accentIndex) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The permission can be revoked from system settings while the app sits in the background.
        StepAccessState.refresh(this)
    }
}

@Composable
private fun MainScreen() {
    var current by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { AppTopBar(tab = current, onAbout = { showAbout = true }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == current,
                        onClick = { current = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes), style = NavLabelStyle) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (current) {
                Tab.TODAY -> TodayScreen()
                Tab.HISTORY -> HistoryScreen()
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}
