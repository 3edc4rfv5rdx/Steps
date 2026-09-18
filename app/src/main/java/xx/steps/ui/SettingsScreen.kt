package xx.steps.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xx.steps.R
import xx.steps.data.RestoreFailure
import xx.steps.data.StepsRepository
import xx.steps.data.exportCsv
import dev.backups.Backups
import dev.updater.Updater
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
    // The jobs outlive this screen, so nothing of the activity goes into one: a Resources held past
    // the activity that owned it is a leak with a stale locale, and the Context it came from is the
    // activity itself. Both are taken from the application instead — the picker's Uri grant belongs
    // to the process, so reading it through that Context is what the pickers already assume.
    val appContext = context.applicationContext
    val resources = appContext.resources
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
    var showBackup by rememberSaveable { mutableStateOf(false) }
    // The daily-copy flag lives in ../backups and in its own preferences file, so it is read here
    // rather than through AppSettings; nothing outside this switch ever changes it.
    var dailyBackup by remember { mutableStateOf(Backups.isEnabled(context)) }
    // Likewise the start-up update check: ../updater keeps that flag, in its own file.
    var updateCheck by remember { mutableStateOf(Updater.isEnabled(context)) }
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

    /**
     * Whether the demo is in the way, saying so if it is. Both jobs below write real days, and a
     * demo run has the database to itself — refusing is the only answer that keeps the two apart
     * without throwing away the one the user did not name.
     */
    fun demoBlocks(): Boolean {
        if (!AppSettings.demoMode.value) return false
        ScreenWork.show(BannerMessage(BannerKind.WARNING, resources.getString(R.string.demo_blocks_history)))
        return true
    }

    fun importCsvNow(uri: Uri) {
        if (demoBlocks()) return
        ScreenWork.run(
            failureText = resources.getString(R.string.import_failed),
            busyText = resources.getString(R.string.work_busy),
        ) {
            val result = importCsv(appContext, uri, repository, AppSettings.goal.value)
            val summary = resources.getString(
                R.string.import_done_message,
                result.read,
                result.written,
                result.skipped,
            )
            // Skipped lines mean the file was not entirely understood; a dropped breakdown means
            // something was destroyed to make room for it. Either one worked, but not cleanly.
            val lost = result.breakdownsDropped
            BannerMessage(
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

    // The dialog stays open when the job is refused, so a tap that did not take does not look like
    // one that did.
    fun exportCsvNow() {
        val started = ScreenWork.run(
            failureText = resources.getString(R.string.export_failed),
            busyText = resources.getString(R.string.work_busy),
        ) {
            val result = exportCsv(appContext, repository.allDays())
            if (result == null) {
                BannerMessage(BannerKind.ERROR, resources.getString(R.string.export_failed))
            } else {
                BannerMessage(
                    BannerKind.SUCCESS,
                    resources.getString(R.string.export_done_message, result.days, result.fileName),
                )
            }
        }
        if (started) showBackup = false
    }

    fun exportZipNow() {
        val started = ScreenWork.run(
            failureText = resources.getString(R.string.backup_failed),
            busyText = resources.getString(R.string.work_busy),
        ) {
            val result = exportZip(appContext)
            if (result == null) {
                BannerMessage(BannerKind.ERROR, resources.getString(R.string.backup_failed))
            } else {
                BannerMessage(
                    BannerKind.SUCCESS,
                    resources.getString(R.string.backup_done_message, result.fileName),
                )
            }
        }
        if (started) showBackup = false
    }

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
                    ScreenWork.show(
                        BannerMessage(BannerKind.ERROR, resources.getString(R.string.battery_no_screen)),
                    )
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
        SwitchRow(
            label = stringResource(R.string.setting_backup_auto),
            checked = dailyBackup,
            onChange = {
                dailyBackup = it
                Backups.setEnabled(context, it)
            },
        )
        SwitchRow(
            label = stringResource(R.string.setting_update_check),
            checked = updateCheck,
            onChange = {
                updateCheck = it
                Updater.setEnabled(context, it)
            },
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
                ScreenWork.launch { repository.applyGoalToToday(AppSettings.goal.value) }
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
                if (demoBlocks()) return@ConfirmDialog
                ScreenWork.run(
                    failureText = resources.getString(R.string.restore_failed),
                    busyText = resources.getString(R.string.work_busy),
                ) {
                    val result = importZip(appContext, uri, repository)
                    val done = resources.getString(R.string.restore_done_message)
                    // A day the archive held but this app could not read back is the one thing a
                    // restore destroys without being asked to, so it is said out loud.
                    when {
                        result.failure != null ->
                            BannerMessage(BannerKind.ERROR, resources.getString(result.failure.messageRes()))

                        result.daysDropped > 0 -> BannerMessage(
                            BannerKind.WARNING,
                            done + "\n" + resources.getString(R.string.restore_dropped) +
                                ": " + result.daysDropped,
                        )

                        else -> BannerMessage(BannerKind.SUCCESS, done)
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

/**
 * A row whose setting is plainly on or off, so it carries the switch itself rather than a word
 * describing one. The whole row is not clickable: the switch is the control, and a row that also
 * toggles makes an accidental brush of the label change a setting.
 */
@Composable
private fun SwitchRow(
    label: String,
    hint: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
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
        Switch(checked = checked, onCheckedChange = onChange)
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

