package io.github.j0p3s.debloater.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.data.Action
import io.github.j0p3s.debloater.data.AppState
import io.github.j0p3s.debloater.data.SafetyLevel
import io.github.j0p3s.debloater.data.SpaceEstimator
import io.github.j0p3s.debloater.data.StorageInfo
import io.github.j0p3s.debloater.ui.PendingOperation

@Composable
fun ConfirmOperationDialog(
    pending: PendingOperation,
    keepDataDefault: Boolean,
    storage: StorageInfo?,
    /** All installed apps, to warn when a selected package is needed by one that stays. */
    allApps: List<io.github.j0p3s.debloater.data.AppInfo> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (keepData: Boolean, dryRun: Boolean, action: Action) -> Unit,
) {
    val apps = pending.apps
    val canChoose = pending.action == Action.DISABLE || pending.action == Action.REMOVE
    var action by rememberSaveable(pending) { mutableStateOf(pending.action) }
    val risky = apps.filter { it.level.isRisky }
    val destructive = action == Action.DISABLE || action == Action.REMOVE
    val needsAck = destructive && !pending.dryRun && risky.isNotEmpty()
    val userApps = apps.count { !it.isSystem }
    var keepData by rememberSaveable(pending) { mutableStateOf(keepDataDefault) }
    var ack by rememberSaveable(pending) { mutableStateOf(false) }

    val title = stringResource(R.string.operation_question, if (pending.dryRun) stringResource(R.string.test) else stringResource(action.verbRes), apps.size)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canChoose) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(Action.DISABLE, Action.REMOVE).forEachIndexed { i, a ->
                            SegmentedButton(
                                selected = action == a,
                                onClick = { action = a },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text(stringResource(a.verbRes)) }
                        }
                    }
                }
                if (pending.dryRun) {
                    Text(stringResource(R.string.test_confirmation))
                }
                if (destructive) {
                    Text(
                        stringResource(R.string.backup_confirmation),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                when (action) {
                    Action.DISABLE -> Text(stringResource(R.string.disable_help), style = MaterialTheme.typography.bodySmall)
                    Action.REMOVE -> Text(
                        stringResource(R.string.remove_confirmation),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> {}
                }
                Text(
                    stringResource(R.string.confirm_summary, apps.size - userApps, userApps, formatSize(apps.sumOf { it.totalBytes })),
                    style = MaterialTheme.typography.labelMedium,
                )

                if (action == Action.REMOVE) {
                    Text(stringResource(R.string.app_data), style = MaterialTheme.typography.labelLarge)
                    DataChoice(
                        selected = keepData,
                        title = stringResource(R.string.keep_data),
                        subtitle = stringResource(R.string.keep_help),
                        onClick = { keepData = true },
                    )
                    DataChoice(
                        selected = !keepData,
                        title = stringResource(R.string.delete_data),
                        subtitle = stringResource(R.string.delete_data_help, formatSize(SpaceEstimator.estimate(apps, Action.REMOVE, keepData = false))),
                        danger = true,
                        onClick = { keepData = false },
                    )
                }

                // ---- storage impact
                val estimate = SpaceEstimator.estimate(apps, action, keepData)
                val backupNeed = if (destructive) apps.filter { it.state != AppState.UNINSTALLED }.sumOf { it.apkSize } else 0L
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.storage), style = MaterialTheme.typography.labelLarge)
                        storage?.let { StorageBar(it) }
                        if (destructive) {
                            Text(
                                stringResource(R.string.estimated_freed, formatSize(estimate)),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            when {
                                action == Action.DISABLE -> Text(stringResource(R.string.no_disable_space), style = MaterialTheme.typography.bodySmall)
                                keepData -> Text(stringResource(R.string.keep_no_space), style = MaterialTheme.typography.bodySmall)
                                else -> {}
                            }
                            Text(stringResource(R.string.backup_need, formatSize(backupNeed)), style = MaterialTheme.typography.bodySmall)
                            storage?.let {
                                if (backupNeed > it.freeBytes) Text(stringResource(R.string.no_backup_space), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (risky.isNotEmpty() && destructive) {
                    Text(stringResource(R.string.warnings), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    risky.forEach { a ->
                        val w = a.entry?.warning ?: when (a.level) {
                            SafetyLevel.UNSAFE -> stringResource(R.string.critical_warning)
                            SafetyLevel.EXPERT -> stringResource(R.string.expert_warning)
                            SafetyLevel.UNKNOWN -> stringResource(R.string.unknown_warning)
                            else -> stringResource(R.string.risk_warning)
                        }
                        WarningText(stringResource(R.string.app_warning, a.label, stringResource(a.level.titleRes), w))
                    }
                }
                if (destructive) {
                    val selected = apps.map { it.packageName }.toSet()
                    val byPkg = allApps.associateBy { it.packageName }
                    val broken = apps.mapNotNull { a ->
                        val users = a.entry?.neededBy.orEmpty().mapNotNull { byPkg[it] }
                            .filter { it.state == AppState.ENABLED && it.packageName !in selected }
                        if (users.isEmpty()) null else stringResource(R.string.needed_by_apps, a.label, users.joinToString { it.label })
                    }
                    if (broken.isNotEmpty()) {
                        Text(stringResource(R.string.dependencies), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        broken.forEach { WarningText(it) }
                    }
                    val stubborn = apps.filter { it.entry?.oftenCantUninstall == true }
                    if (action == Action.REMOVE && stubborn.isNotEmpty()) {
                        Text(
                            stringResource(R.string.stubborn_apps, stubborn.size, stubborn.take(5).joinToString { it.label }),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (needsAck) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(ack, { ack = it })
                        Text(stringResource(R.string.ack_warnings), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(keepData, pending.dryRun, action) },
                enabled = !needsAck || ack,
                colors = if (!pending.dryRun && action == Action.REMOVE) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.buttonColors(),
            ) {
                Text(
                    when {
                        pending.dryRun -> stringResource(R.string.start_test)
                        action == Action.REMOVE && !keepData -> stringResource(R.string.remove_delete)
                        else -> stringResource(action.verbRes)
                    },
                )
            }
        },
        dismissButton = {
            Row {
                if (!pending.dryRun && destructive) TextButton(onClick = { onConfirm(keepData, true, action) }) { Text(stringResource(R.string.test_first)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** One option of the keep/delete app data choice. */
@Composable
private fun DataChoice(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit, danger: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (danger && selected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
