package xx.steps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xx.steps.R
import xx.steps.data.StepsRepository
import xx.steps.formatSteps
import xx.steps.settings.AppSettings
import xx.steps.settings.ThemeMode
import xx.steps.settings.currentLanguageTag
import xx.steps.settings.setLanguageTag
import xx.steps.settings.supportedLanguages
import xx.steps.steps.DemoSteps

/** Daily goal, step length, theme, accent colour, language, and the demo switch. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { StepsRepository.get(context) }

    val goal by AppSettings.goal.collectAsState()
    val stepLength by AppSettings.stepLengthCm.collectAsState()
    val themeMode by AppSettings.themeMode.collectAsState()
    val accentIndex by AppSettings.accentIndex.collectAsState()
    val demo by AppSettings.demoMode.collectAsState()

    // Both come from the platform: the shipped locales from locales_config.xml, the current one
    // from the per-app locale. Re-read whenever this screen is built, since choosing a language
    // recreates the activity anyway.
    val systemLabel = stringResource(R.string.language_system)
    val languages = remember(context, systemLabel) { supportedLanguages(context, systemLabel) }
    val languageTag = remember(context) { currentLanguageTag(context) }
    val languageLabel = languages.firstOrNull { it.tag == languageTag }?.label ?: systemLabel

    // Saveable: changing the theme or the language recreates the activity under an open dialog.
    var editing by rememberSaveable { mutableStateOf(Editing.NONE) }
    var confirmDemo by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingRow(
            label = stringResource(R.string.setting_goal),
            value = formatSteps(goal),
            onClick = { editing = Editing.GOAL },
        )
        HorizontalDivider()

        SettingRow(
            label = stringResource(R.string.setting_step_length),
            value = stepLength.toString(),
            onClick = { editing = Editing.STEP_LENGTH },
        )
        HorizontalDivider()

        SettingRow(
            label = stringResource(R.string.setting_theme),
            value = stringResource(themeMode.labelRes()),
            onClick = { editing = Editing.THEME },
        )
        HorizontalDivider()

        AccentRow(
            selected = accentIndex,
            onPick = { AppSettings.setAccentIndex(context, it) },
        )
        HorizontalDivider()

        SettingRow(
            label = stringResource(R.string.setting_language),
            value = languageLabel,
            onClick = { editing = Editing.LANGUAGE },
        )
        HorizontalDivider()

        SwitchRow(
            label = stringResource(R.string.setting_demo),
            hint = stringResource(R.string.setting_demo_hint),
            checked = demo,
            onToggle = { confirmDemo = true },
        )
    }

    when (editing) {
        Editing.GOAL -> NumberDialog(
            title = stringResource(R.string.setting_goal),
            initial = goal,
            onDismiss = { editing = Editing.NONE },
            onConfirm = { entered ->
                AppSettings.setGoal(context, entered)
                // A goal changed today applies to today; past days keep the goal they were judged by.
                scope.launch { repository.applyGoalToToday(AppSettings.goal.value) }
                editing = Editing.NONE
            },
        )

        Editing.STEP_LENGTH -> NumberDialog(
            title = stringResource(R.string.setting_step_length),
            initial = stepLength,
            onDismiss = { editing = Editing.NONE },
            onConfirm = { entered ->
                AppSettings.setStepLengthCm(context, entered)
                editing = Editing.NONE
            },
        )

        Editing.THEME -> ChoiceDialog(
            title = stringResource(R.string.setting_theme),
            options = ThemeMode.entries,
            selected = themeMode,
            label = { stringResource(it.labelRes()) },
            onDismiss = { editing = Editing.NONE },
            onPick = { picked ->
                AppSettings.setThemeMode(context, picked)
                editing = Editing.NONE
            },
        )

        Editing.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.setting_language),
            options = languages,
            selected = languages.firstOrNull { it.tag == languageTag },
            label = { it.label },
            onDismiss = { editing = Editing.NONE },
            onPick = { picked ->
                editing = Editing.NONE
                // The system persists this and recreates the activity for us.
                setLanguageTag(context, picked.tag)
            },
        )

        Editing.NONE -> Unit
    }

    if (confirmDemo) {
        ConfirmDialog(
            title = stringResource(R.string.demo_wipe_title),
            message = stringResource(R.string.demo_wipe_message),
            onDismiss = { confirmDemo = false },
            onConfirm = {
                confirmDemo = false
                scope.launch {
                    DemoSteps.toggle(context, repository, turnOn = !demo, goal = goal)
                }
            },
        )
    }
}

/** Which editor is open; only one can be at a time, so one value says it. */
private enum class Editing { NONE, GOAL, STEP_LENGTH, THEME, LANGUAGE }

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SwitchRow(label: String, hint: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

/** The accent choice is made in place: six swatches, the current one ringed. */
@Composable
private fun AccentRow(selected: Int, onPick: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(
            text = stringResource(R.string.setting_accent),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccentPalette.forEachIndexed { index, color ->
                AccentSwatch(
                    color = color,
                    selected = index == selected,
                    onClick = { onPick(index) },
                )
            }
        }
    }
}

@Composable
private fun AccentSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = color, shape = CircleShape)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

