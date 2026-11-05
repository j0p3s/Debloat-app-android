package io.github.j0p3s.debloater.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.backup.BackupSession
import io.github.j0p3s.debloater.backup.EntryStatus
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.BackTopBar
import io.github.j0p3s.debloater.ui.components.Pill
import io.github.j0p3s.debloater.ui.components.StorageBar
import io.github.j0p3s.debloater.ui.components.formatSize
import io.github.j0p3s.debloater.ui.theme.ErrorColor
import io.github.j0p3s.debloater.ui.theme.OkColor
import io.github.j0p3s.debloater.ui.theme.WarnColor
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupsScreen(vm: MainViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var logs by remember { mutableStateOf<List<File>>(emptyList()) }
    var openLog by remember { mutableStateOf<File?>(null) }
    var confirmDelete by remember { mutableStateOf<BackupSession?>(null) }
    var totalSize by remember { mutableStateOf(0L) }
    val storage by vm.storage.collectAsStateWithLifecycle()

    LaunchedEffect(sessions) {
        withContext(Dispatchers.IO) {
            logs = vm.backups.listLogs()
            totalSize = sessions.sumOf { vm.backups.sessionSize(it.id) }
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(vm::exportBackups)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBackups)
    }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.backups_restore), onBack = { vm.back() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.auto_backups), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.backup_intro),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(stringResource(R.string.backup_stats, sessions.size, formatSize(totalSize), formatSize(sessions.sumOf { it.freedBytes })), style = MaterialTheme.typography.labelMedium)
                        storage?.let { StorageBar(it) }
                        Text(vm.backups.root.absolutePath, style = MaterialTheme.typography.labelSmall)
                        Text(
                            stringResource(R.string.backup_export_help),
                            style = MaterialTheme.typography.bodySmall, color = WarnColor,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { exporter.launch("debloater-backups-${System.currentTimeMillis() / 1000}.zip") }) { Text(stringResource(R.string.export_zip)) }
                            OutlinedButton(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text(stringResource(R.string.import_zip)) }
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.undo_all), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.undo_help),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.restoreBaseline(dryRun = true) }) { Text(stringResource(R.string.test)) }
                            Button(onClick = { vm.restoreBaseline(dryRun = false) }) { Text(stringResource(R.string.restore_all)) }
                        }
                    }
                }
            }
            if (sessions.isEmpty()) {
                item { Text(stringResource(R.string.no_backups), Modifier.padding(8.dp)) }
            }
            items(sessions, key = { it.id }) { s -> SessionCard(s, vm, onDelete = { confirmDelete = s }) }
            if (logs.isNotEmpty()) {
                item { Text(stringResource(R.string.logs), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(logs.take(50), key = { it.absolutePath }) { f ->
                    Text(
                        "${f.nameWithoutExtension}  ·  ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(f.lastModified()))}",
                        Modifier.fillMaxWidth().clickable { openLog = f }.padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    confirmDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_backup)) },
            text = { Text(stringResource(R.string.delete_backup_help, s.id)) },
            confirmButton = { TextButton(onClick = { vm.deleteSession(s.id); confirmDelete = null }) { Text(stringResource(R.string.delete), color = ErrorColor) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    openLog?.let { f -> LogDialog(f) { openLog = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCard(s: BackupSession, vm: MainViewModel, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember(s.id) { mutableStateOf(s.entries.filter { it.status == EntryStatus.DONE }.map { it.packageName }.toSet()) }
    val done = s.entries.count { it.status == EntryStatus.DONE }
    val failed = s.entries.count { it.status == EntryStatus.FAILED || it.status == EntryStatus.RESTORE_FAILED }
    val restored = s.entries.count { it.status == EntryStatus.RESTORED }
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.session_title, stringResource(s.action.verbRes), s.entries.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.createdAt)) +
                            " · ${s.device.manufacturer} ${s.device.model}, API ${s.device.sdk}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Pill(stringResource(R.string.changed_count, done), OkColor)
                        if (s.freedBytes > 0) Pill(stringResource(R.string.freed_size, formatSize(s.freedBytes)), OkColor)
                        if (s.backupBytes > 0) Pill(stringResource(R.string.backup_size, formatSize(s.backupBytes)), MaterialTheme.colorScheme.outline)
                        if (restored > 0) Pill(stringResource(R.string.restored_count, restored), MaterialTheme.colorScheme.primary)
                        if (failed > 0) Pill(stringResource(R.string.failed_count, failed), ErrorColor)
                        if (!s.finished) Pill(stringResource(R.string.interrupted), WarnColor)
                    }
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (expanded) {
                s.entries.forEach { e ->
                    val restorable = e.status == EntryStatus.DONE || e.status == EntryStatus.RESTORE_FAILED || e.status == EntryStatus.RESTORED
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = e.packageName in selected,
                            enabled = restorable,
                            onCheckedChange = { selected = if (it) selected + e.packageName else selected - e.packageName },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(e.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.backup_entry, e.packageName, stringResource(e.stateBefore.titleRes), stringResource(e.status.titleRes), formatSize(e.sizeBytes), formatSize(e.freedBytes), formatSize(e.apkBytes)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (e.status == EntryStatus.FAILED || e.status == EntryStatus.SKIPPED) {
                                Text(e.message.take(200), style = MaterialTheme.typography.bodySmall, color = ErrorColor)
                            }
                        }
                    }
                }
                val chosen = s.entries.filter { it.packageName in selected }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    OutlinedButton(onClick = { vm.restoreSession(s, chosen, dryRun = true) }, enabled = chosen.isNotEmpty()) { Text(stringResource(R.string.test_restore)) }
                    Button(onClick = { vm.restoreSession(s, chosen, dryRun = false) }, enabled = chosen.isNotEmpty()) { Text(stringResource(R.string.restore_count, chosen.size)) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = ErrorColor) }
                }
            }
        }
    }
}

@Composable
private fun LogDialog(file: File, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(file) { text = withContext(Dispatchers.IO) { runCatching { file.readText() }.getOrDefault("") } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.nameWithoutExtension) },
        text = {
            Text(
                text, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            TextButton(onClick = {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), ctx.getString(R.string.share_log)))
            }) { Text(stringResource(R.string.share)) }
        },
    )
}
