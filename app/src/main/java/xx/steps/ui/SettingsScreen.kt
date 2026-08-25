package xx.steps.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xx.steps.R
import xx.steps.data.AppDatabase
import xx.steps.data.RestoreFailure
import xx.steps.data.StepsRepository
import xx.steps.data.exportCsv
import xx.steps.data.exportZip
import xx.steps.data.importCsv
import xx.steps.data.importZip
import xx.steps.formatSteps
import xx.steps.settings.AppSettings
import xx.steps.settings.batteryExemptionIntent
import xx.steps.settings.isIgnoringBatteryOptimizations
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import xx.steps.settings.ThemeMode
import xx.steps.settings.currentLanguageTag
import xx.steps.settings.setLanguageTag
import xx.steps.settings.supportedLanguages

/** Daily goal, step length, theme, accent colour, language, and the demo switch. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val repository = remember(context) { StepsRepository.get(context) }

    val goal by AppSettings.goal.collectAsState()
    val stepLength by AppSettings.stepLengthCm.collectAsState()
    val themeMode by AppSettings.themeMode.collectAsState()
    val accentIndex by AppSettings.accentIndex.collectAsState()

    // Both come from the platform: the shipped locales from locales_config.xml, the current one
    // from the per-app locale. Re-read whenever this screen is built, since choosing a language
    // recreates the activity anyway.
    val systemLabel = stringResource(R.string.language_system)
    val languages = remember(context, systemLabel) { supportedLanguages(context, systemLabel) }
    val languageTag = remember(context) { currentLanguageTag(context) }
    val languageLabel = languages.firstOrNull { it.tag == languageTag }?.label ?: systemLabel

    // Saveable: changing the theme or the language recreates the activity under an open dialog.
    var editing by rememberSaveable { mutableStateOf(Editing.NONE) }
    var banner by remember { mutableStateOf<BannerMessage?>(null) }
    var showBackup by rememberSaveable { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    // Read once and again on the way back from the system screen, which is the only place it can
    // change while this screen is up.
    var unrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    val batterySettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        unrestricted = isIgnoringBatteryOptimizations(context)
    }

    // The system picker hands back a readable Uri; no storage permission is involved either way.
    // Like a restore, the file is chosen first and confirmed after: a merge cannot be undone either.
    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        showBackup = false
        pendingImport = uri
    }

    fun importCsvNow(uri: Uri) {
        scope.launch {
            val result = importCsv(context, uri, repository, AppSettings.goal.value)
            val summary = resources.getString(
                R.string.import_done_message,
                result.read,
                result.written,
                result.skipped,
            )
            // Skipped lines mean the file was not entirely understood; a dropped breakdown means
            // something was destroyed to make room for it. Either one worked, but not cleanly.
            val lost = result.breakdownsDropped
            banner = BannerMessage(
                kind = if (result.skipped > 0 || lost > 0) BannerKind.WARNING else BannerKind.SUCCESS,
                text = if (lost == 0) {
                    summary
                } else {
                    summary + "\n" + resources.getString(R.string.import_breakdown_dropped) + ": " + lost
                },
            )
        }
    }

    // A restore replaces everything, so the file is chosen first and confirmed after.
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        showBackup = false
        pendingRestore = uri
    }

    fun exportCsvNow() {
        showBackup = false
        scope.launch {
            val result = runCatching { exportCsv(context, repository.allDays()) }.getOrNull()
            banner = if (result == null) {
                BannerMessage(BannerKind.ERROR, resources.getString(R.string.export_failed))
            } else {
                BannerMessage(
                    BannerKind.SUCCESS,
                    resources.getString(R.string.export_done_message, result.days, result.fileName),
                )
            }
        }
    }

    fun exportZipNow() {
        showBackup = false
        scope.launch {
            val result = runCatching { exportZip(context, AppDatabase.get(context)) }.getOrNull()
            banner = if (result == null) {
                BannerMessage(BannerKind.ERROR, resources.getString(R.string.backup_failed))
            } else {
                BannerMessage(
                    BannerKind.SUCCESS,
                    resources.getString(R.string.backup_done_message, result.fileName),
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            label = stringResource(R.string.setting_battery),
            value = stringResource(
                if (unrestricted) R.string.battery_unrestricted else R.string.battery_restricted,
            ),
            hint = stringResource(R.string.setting_battery_hint),
            // A phone with neither screen has nothing to offer here — rare, but a row that does
            // nothing and says nothing is worse than one that admits it.
            onClick = {
                val opened = runCatching { batterySettings.launch(batteryExemptionIntent(context)) }
                if (opened.isFailure) {
                    banner = BannerMessage(BannerKind.ERROR, resources.getString(R.string.battery_no_screen))
                }
            },
        )
        HorizontalDivider()

        SettingRow(
            label = stringResource(R.string.setting_theme),
            value = stringResource(themeMode.labelRes()),
            onClick = { editing = Editing.THEME },
        )
        HorizontalDivider()

        AccentRow(selected = accentIndex, onClick = { editing = Editing.ACCENT })
        HorizontalDivider()

        SettingRow(
            label = stringResource(R.string.setting_language),
            value = languageLabel,
            onClick = { editing = Editing.LANGUAGE },
        )
        HorizontalDivider()

        ActionRow(
            label = stringResource(R.string.setting_backup),
            onClick = { showBackup = true },
        )
    }

        banner?.let { message ->
            StatusBanner(
                message = message,
                onDismiss = { banner = null },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
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

        Editing.ACCENT -> AccentDialog(
            selected = accentIndex,
            onPick = { picked ->
                AppSettings.setAccentIndex(context, picked)
                editing = Editing.NONE
            },
            onDismiss = { editing = Editing.NONE },
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

    if (showBackup) {
        BackupDialog(
            onDismiss = { showBackup = false },
            onExportCsv = ::exportCsvNow,
            onImportCsv = { pickCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
            onExportZip = ::exportZipNow,
            onImportZip = { pickZip.launch(arrayOf("application/zip", "application/octet-stream")) },
        )
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.import_confirm_title),
            message = stringResource(R.string.import_confirm_message),
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                importCsvNow(uri)
            },
        )
    }

    pendingRestore?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            message = stringResource(R.string.restore_confirm_message),
            onDismiss = { pendingRestore = null },
            onConfirm = {
                pendingRestore = null
                scope.launch {
                    val failure = importZip(context, uri, repository)
                    banner = if (failure == null) {
                        BannerMessage(BannerKind.SUCCESS, resources.getString(R.string.restore_done_message))
                    } else {
                        BannerMessage(BannerKind.ERROR, resources.getString(failure.messageRes()))
                    }
                }
            },
        )
    }
}


private fun RestoreFailure.messageRes(): Int = when (this) {
    RestoreFailure.NOT_AN_ARCHIVE -> R.string.restore_failed_archive
    RestoreFailure.NO_DATABASE_INSIDE -> R.string.restore_failed_content
    RestoreFailure.TOO_LARGE -> R.string.restore_failed_size
    RestoreFailure.NOT_A_DATABASE -> R.string.restore_failed_broken
}

/** A settings row that opens something: the chevron says the row leads somewhere. */
@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Which editor is open; only one can be at a time, so one value says it. */
private enum class Editing { NONE, GOAL, STEP_LENGTH, THEME, ACCENT, LANGUAGE }

/** [hint] is for a row whose label cannot say the whole thing on its own; most rows need none. */
@Composable
private fun SettingRow(label: String, value: String, hint: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            hint?.let {
                Text(
                    text = it,
                    style = RowHintStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The row shows which colour is in force; the palette itself lives in a dialog. */
@Composable
private fun AccentRow(selected: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.setting_accent),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        AccentSwatch(color = accentAt(selected), selected = false, onClick = onClick)
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

