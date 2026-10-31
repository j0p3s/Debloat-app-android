package io.github.j0p3s.debloater.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.j0p3s.debloater.data.Action
import io.github.j0p3s.debloater.data.AppState
import io.github.j0p3s.debloater.data.SafetyLevel
import io.github.j0p3s.debloater.data.SpaceEstimator
import io.github.j0p3s.debloater.data.StorageInfo
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import io.github.j0p3s.debloater.ops.OperationEngine
import io.github.j0p3s.debloater.ui.PendingOperation

@Composable
fun ConfirmOperationDialog(
    pending: PendingOperation,
    keepDataDefault: Boolean,
    storage: StorageInfo?,
    onDismiss: () -> Unit,
    onConfirm: (keepData: Boolean, dryRun: Boolean, action: Action) -> Unit,
) {
    val apps = pending.apps
    val canChoose = pending.action == Action.DISABLE || pending.action == Action.REMOVE
    var action by remember { mutableStateOf(pending.action) }
    val risky = apps.filter { it.level == SafetyLevel.EXTREME || it.level == SafetyLevel.UNSAFE || it.level == SafetyLevel.UNKNOWN }
    val destructive = action == Action.DISABLE || action == Action.REMOVE
    val needsAck = destructive && !pending.dryRun && risky.isNotEmpty()
    val userApps = apps.count { !it.isSystem }
    var keepData by remember { mutableStateOf(keepDataDefault) }
    var ack by remember { mutableStateOf(false) }

    val title = if (pending.dryRun) "Test run: ${action.verb.lowercase()} ${apps.size} app(s)" else "${action.verb} ${apps.size} app(s)?"

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
                            ) { Text(if (pending.dryRun) "Test ${a.verb.lowercase()}" else a.verb) }
                        }
                    }
                }
                if (pending.dryRun) {
                    Text("Nothing on your device will be changed. Every step (Shizuku access, package checks, backup checks, free space) is run and shown, and you will see whether any errors would happen.")
                }
                if (destructive) {
                    Text(
                        "Backups are always made first: a restore point with the state of every app, plus a copy of every APK. Apps that cannot be backed up are skipped, never changed.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                when (action) {
                    Action.DISABLE -> Text("Disabled apps stay installed but cannot run. You can enable them again at any time.", style = MaterialTheme.typography.bodySmall)
                    Action.REMOVE -> Text(
                        "Apps are uninstalled for your user (pm uninstall --user). System apps can always be restored, and a factory reset also brings them back. User apps are restored from their APK backup.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> {}
                }
                Text(
                    "${apps.size - userApps} system · $userApps user · ${OperationEngine.fmtSize(apps.sumOf { it.totalBytes })} total size",
                    style = MaterialTheme.typography.labelMedium,
                )

                if (action == Action.REMOVE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(keepData, { keepData = it })
                        Text(
                            "Keep app data. Apps come back instantly with their data, but almost no space is freed. " +
                                "Turn this off to free space: app data is deleted (it cannot be backed up without root), APKs are still backed up.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // ---- storage impact
                val estimate = SpaceEstimator.estimate(apps, action, keepData)
                val backupNeed = if (destructive) apps.filter { it.state != AppState.UNINSTALLED }.sumOf { it.apkSize } else 0L
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Storage", style = MaterialTheme.typography.labelLarge)
                        storage?.let { StorageBar(it) }
                        if (destructive) {
                            Text(
                                "Estimated space freed: ${OperationEngine.fmtSize(estimate)}" +
                                    if (apps.any { !it.sizeExact } && estimate > 0) " or more (some data sizes unknown)" else "",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            when {
                                action == Action.DISABLE -> Text("Disabling does not free space; the apps and their data stay on the phone.", style = MaterialTheme.typography.bodySmall)
                                keepData -> Text("With \"keep data\" the data (and user-app APKs) stay on the phone.", style = MaterialTheme.typography.bodySmall)
                                else -> {}
                            }
                            Text("APK backups need about ${OperationEngine.fmtSize(backupNeed)}.", style = MaterialTheme.typography.bodySmall)
                            storage?.let {
                                if (backupNeed > it.freeBytes) Text("Not enough free space for the backups!", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (risky.isNotEmpty() && destructive) {
                    Text("Warnings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    risky.take(40).forEach { a ->
                        val w = a.entry?.warning ?: when (a.level) {
                            SafetyLevel.UNSAFE -> "Critical package. Removing it can break the device."
                            SafetyLevel.UNKNOWN -> "Not in the debloat list. Its effect is unknown."
                            else -> "May break features."
                        }
                        WarningText("${a.label} [${a.level.title}]: $w")
                    }
                    if (risky.size > 40) Text("…and ${risky.size - 40} more", style = MaterialTheme.typography.bodySmall)
                }
                if (needsAck) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(ack, { ack = it })
                        Text("I have read the warnings and understand that features may stop working.", style = MaterialTheme.typography.bodySmall)
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
            ) { Text(if (pending.dryRun) "Start test" else action.verb) }
        },
        dismissButton = {
            Row {
                if (!pending.dryRun && destructive) TextButton(onClick = { onConfirm(keepData, true, action) }) { Text("Test first") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
