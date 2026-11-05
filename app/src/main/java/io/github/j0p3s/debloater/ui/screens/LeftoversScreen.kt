package io.github.j0p3s.debloater.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.shizuku.ShizukuStatus
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.BackTopBar
import io.github.j0p3s.debloater.ui.components.formatSize

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeftoversScreen(vm: MainViewModel) {
    val tool = vm.leftovers
    val status by vm.shizukuStatus.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val free = !tool.busy && !vm.cache.busy && operation?.finished != false && status == ShizukuStatus.READY
    val clipboard = LocalClipboardManager.current
    Scaffold(topBar = { BackTopBar(stringResource(R.string.leftovers_title), onBack = { vm.back() }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.leftovers_help))
                Text(stringResource(R.string.leftovers_scope), style = MaterialTheme.typography.bodySmall)
                Button(onClick = tool::scan, enabled = free) { Text(stringResource(R.string.leftovers_scan)) }
                if(status != ShizukuStatus.READY) Text(stringResource(R.string.leftovers_needs_shizuku))
            }
            if(tool.busy) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.cache_progress, tool.progress.first, tool.progress.second))
                Text(stringResource(R.string.leftovers_stop_help))
                OutlinedButton(onClick = tool::stop, enabled = !tool.stopping) { Text(stringResource(R.string.cancel)) }
            }
            tool.notice?.let { notice -> item { Text(stringResource(notice.resource, *notice.args.toTypedArray())) } }
            if(tool.warnings.isNotEmpty()) item {
                Text(stringResource(R.string.leftovers_partial), color = MaterialTheme.colorScheme.error)
                tool.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                OutlinedTextField(tool.query, { tool.query = it }, label = { Text(stringResource(R.string.cache_search)) }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { tool.largestFirst = !tool.largestFirst }) {
                    Text(stringResource(if(tool.largestFirst) R.string.cache_largest else R.string.sort_name_asc))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = tool::selectShown, enabled = free) { Text(stringResource(R.string.cache_select_shown)) }
                    TextButton(onClick = tool::deselect, enabled = free) { Text(stringResource(R.string.cache_deselect)) }
                }
                Text(stringResource(R.string.cache_selection, tool.selected.size,
                    formatSize(tool.candidates.filter { it.key in tool.selected }.sumOf { it.bytes })))
                Button(onClick = { tool.request("quarantine") }, enabled = free && tool.selected.isNotEmpty()) {
                    Text(stringResource(R.string.leftovers_move))
                }
            }
            items(tool.visible(), key = { it.key }) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp)) {
                        Checkbox(row.key in tool.selected, { tool.toggle(row.key) }, enabled = free)
                        Column(Modifier.weight(1f)) {
                            Text(row.key.substringAfter('/'), style = MaterialTheme.typography.titleSmall)
                            Text(row.path, style = MaterialTheme.typography.bodySmall)
                            Text(formatSize(row.bytes))
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.leftovers_recovery), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.leftovers_recovery_help))
                TextButton(onClick = tool::refreshRecovery, enabled = free) { Text(stringResource(R.string.leftovers_load_recovery)) }
            }
            items(tool.recovery, key = { it.id }) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(row.key, style = MaterialTheme.typography.titleSmall)
                        Text(row.path, style = MaterialTheme.typography.bodySmall)
                        Text(formatSize(row.bytes))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { tool.request("restore", row) }, enabled = free) { Text(stringResource(R.string.leftovers_restore)) }
                            TextButton(onClick = { tool.request("purge", row) }, enabled = free) { Text(stringResource(R.string.leftovers_delete)) }
                        }
                    }
                }
            }
            if(tool.log.isNotEmpty()) item {
                Text(stringResource(R.string.cache_log), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { clipboard.setText(AnnotatedString(tool.log.joinToString("\n\n"))) }) { Text(stringResource(R.string.cache_copy_log)) }
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    tool.log.asReversed().forEach { Text(it, style = MaterialTheme.typography.bodySmall); HorizontalDivider() }
                }
            }
        }
    }
    tool.pending?.let { request ->
        val action = when(request.action) { "restore" -> R.string.leftovers_restore; "purge" -> R.string.leftovers_delete; else -> R.string.leftovers_move }
        AlertDialog(
            onDismissRequest = { tool.pending = null },
            title = { Text(stringResource(action)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(when(request.action) { "restore" -> R.string.leftovers_restore_warning; "purge" -> R.string.leftovers_delete_warning; else -> R.string.leftovers_move_warning }))
                    request.rows.forEach { Text("${it.path}\n${formatSize(it.bytes)}") }
                }
            },
            confirmButton = { Button(onClick = tool::confirm, enabled = free) { Text(stringResource(action)) } },
            dismissButton = { TextButton(onClick = { tool.pending = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
