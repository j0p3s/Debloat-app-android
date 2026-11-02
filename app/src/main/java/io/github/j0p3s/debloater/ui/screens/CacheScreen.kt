package io.github.j0p3s.debloater.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.data.CacheSort
import io.github.j0p3s.debloater.shizuku.ShizukuStatus
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.BackTopBar
import io.github.j0p3s.debloater.ui.components.formatSize
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CacheScreen(vm: MainViewModel, system: Boolean) {
    val cache = vm.cache
    val context = LocalContext.current
    val status by vm.shizukuStatus.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val free = !cache.busy && operation?.finished != false
    val visible = cache.visible(system)
    val chosen = cache.groups.filter { it.isSystem == system && it.key in cache.selected }
    val clipboard = LocalClipboardManager.current

    Scaffold(topBar = { BackTopBar(stringResource(if(system) R.string.cache_system else R.string.cache_user), onBack = { vm.back() }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(if(system) R.string.cache_system_help else R.string.cache_user_help))
                Text(stringResource(R.string.cache_scope), style = MaterialTheme.typography.bodySmall)
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.cache_scanner), style = MaterialTheme.typography.titleMedium)
                        cache.scannedAt?.let { time ->
                            Text(stringResource(R.string.cache_total, formatSize(cache.groups.sumOf { it.bytes ?: 0 })))
                            Text(stringResource(R.string.cache_totals,
                                formatSize(cache.groups.filter { !it.isSystem }.sumOf { it.bytes ?: 0 }),
                                formatSize(cache.groups.filter { it.isSystem }.sumOf { it.bytes ?: 0 })))
                            Text(stringResource(R.string.cache_timestamp, DateFormat.getDateTimeInstance().format(Date(time))), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.cache_unknown_count, cache.groups.count { it.bytes == null }), style = MaterialTheme.typography.bodySmall)
                        } ?: Text(stringResource(R.string.cache_not_scanned))
                        Button(onClick = cache::scan, enabled = free) { Text(stringResource(R.string.cache_scan)) }
                        if(!vm.repo.hasUsageAccess()) {
                            Text(stringResource(R.string.cache_usage_help), style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if(status == ShizukuStatus.READY) TextButton(onClick = vm::grantUsageAccessWithShizuku, enabled = free) { Text(stringResource(R.string.grant_shizuku)) }
                                TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }, enabled = free) { Text(stringResource(R.string.open_settings)) }
                            }
                        }
                    }
                }
            }
            if(cache.busy) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.cache_progress, cache.progress.first, cache.progress.second))
            }
            cache.notice?.let { message -> item { Text(stringResource(message.resource, *message.args.toTypedArray())) } }
            if(status != ShizukuStatus.READY) item { Text(stringResource(R.string.cache_needs_shizuku)) }
            if(cache.supported == false) item { Text(stringResource(R.string.cache_unsupported)) }
            item {
                OutlinedTextField(value = cache.query, onValueChange = { cache.query = it }, label = { Text(stringResource(R.string.cache_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                var menu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text(stringResource(cache.sort.titleRes)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        CacheSort.entries.forEach { order -> DropdownMenuItem(text = { Text(stringResource(order.titleRes)) }, onClick = { cache.sort = order; menu = false }) }
                    }
                }
                Row {
                    Checkbox(checked = cache.nonzeroOnly, onCheckedChange = { cache.nonzeroOnly = it })
                    Text(stringResource(R.string.cache_nonzero), modifier = Modifier.padding(top = 12.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { cache.selectShown(system) }, enabled = free) { Text(stringResource(R.string.cache_select_shown)) }
                    TextButton(onClick = { cache.deselect(system) }, enabled = free) { Text(stringResource(R.string.cache_deselect)) }
                }
                Text(stringResource(R.string.cache_selection, chosen.size, formatSize(chosen.sumOf { it.bytes ?: 0 })))
                Text(stringResource(R.string.cache_hidden_selection), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { cache.requestClear(system) }, enabled = free && chosen.isNotEmpty() && status == ShizukuStatus.READY && cache.supported != false) { Text(stringResource(R.string.cache_clear)) }
            }
            if(visible.isEmpty() && cache.scannedAt != null) item { Text(stringResource(R.string.cache_empty)) }
            items(visible, key = { it.key }) { group ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp)) {
                        Checkbox(checked = group.key in cache.selected, onCheckedChange = { cache.toggle(group.key) }, enabled = free)
                        Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(group.label, style = MaterialTheme.typography.titleSmall)
                            Text(group.packages.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                            Text(group.bytes?.let { formatSize(it) } ?: stringResource(R.string.cache_unknown))
                            if(group.packages.size > 1) Text(stringResource(R.string.cache_shared), style = MaterialTheme.typography.bodySmall)
                            group.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            if(cache.log.isNotEmpty()) item {
                Text(stringResource(R.string.cache_log), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { clipboard.setText(AnnotatedString(cache.log.joinToString("\n\n"))) }) { Text(stringResource(R.string.cache_copy_log)) }
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    cache.log.asReversed().forEach { Text(it, style = MaterialTheme.typography.bodySmall); HorizontalDivider() }
                }
            }
        }
    }
    cache.pending?.let { targets ->
        AlertDialog(
            onDismissRequest = { cache.pending = null },
            title = { Text(stringResource(R.string.cache_confirm_title, targets.sumOf { it.packages.size })) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.cache_confirm_help, formatSize(targets.sumOf { it.bytes ?: 0 })))
                    targets.forEach { Text("• ${it.label}\n${it.packages.joinToString(", ")}") }
                }
            },
            confirmButton = { Button(onClick = cache::confirmClear, enabled = free && status == ShizukuStatus.READY) { Text(stringResource(R.string.cache_clear)) } },
            dismissButton = { TextButton(onClick = { cache.pending = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
