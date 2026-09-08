package xx.steps.ui

import xx.steps.BuildConfig

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import xx.steps.R

/**
 * Number editor used for the goal and the step length. The unit is carried by the row's label
 * ("Step length, cm"), so the field itself holds a bare number. The caller clamps what comes back,
 * so a typo cannot store nonsense; empty or unparseable input simply keeps the old value.
 */
@Composable
fun NumberDialog(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { entered -> text = entered.filter { it.isDigit() }.take(6) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            DialogConfirmButton(stringResource(R.string.save)) {
                text.toIntOrNull()?.let(onConfirm) ?: onDismiss()
            }
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
        },
    )
}

/**
 * Single-choice list: the theme and the language both pick one labelled option. It scrolls, because
 * the language list is built from whatever locales the build ships and can outgrow the dialog.
 */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, onClick = { onPick(option) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = { onPick(option) })
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
        },
    )
}

/** Yes/no confirmation for something that cannot be undone — wiping the database, for one. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            DialogConfirmButton(stringResource(R.string.confirm), onConfirm)
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
        },
    )
}

/** App name, version and build number — reached from the Info button in the top bar. */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val info = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${stringResource(R.string.about_version)} ${info.versionName}",
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // The build number is the version's last component, so what is
                // worth a line of its own is the day it was built.
                Text(
                    text = "${stringResource(R.string.about_build_date)} ${BuildConfig.BUILD_DATE}",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        confirmButton = {
            DialogConfirmButton(stringResource(R.string.action_ok), onDismiss)
        },
    )
}


/**
 * The four ways data leaves or enters the app, in one place: CSV is readable and merges, a ZIP is
 * an exact snapshot that replaces.
 */
@Composable
fun BackupDialog(
    onDismiss: () -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onExportZip: () -> Unit,
    onImportZip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_backup)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupAction(stringResource(R.string.setting_export_csv), onExportCsv)
                BackupAction(stringResource(R.string.setting_import_csv), onImportCsv)
                BackupAction(stringResource(R.string.backup_export_zip), onExportZip)
                BackupAction(stringResource(R.string.backup_import_zip), onImportZip)
            }
        },
        confirmButton = {
            DialogDismissButton(stringResource(R.string.close), onDismiss)
        },
    )
}

/** One full-width action inside the backup dialog; filled, like every button in this app. */
@Composable
fun BackupAction(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The accent palette: six swatches, the one in force ringed. */
@Composable
fun AccentDialog(selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_accent)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                AccentPalette.forEachIndexed { index, color ->
                    AccentSwatch(
                        color = color,
                        selected = index == selected,
                        onClick = { onPick(index) },
                    )
                }
            }
        },
        confirmButton = {
            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
        },
    )
}
