package io.github.j0p3s.debloater.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.data.CacheSort
import io.github.j0p3s.debloater.data.CacheScope
import io.github.j0p3s.debloater.data.CachePolicy
import io.github.j0p3s.debloater.data.CacheClearMethod
import io.github.j0p3s.debloater.cache.SettingsCacheService
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
    val settingsAvailable by SettingsCacheService.available.collectAsStateWithLifecycle()
    var showAccessDisclosure by rememberSaveable { mutableStateOf(false) }
    val operation by vm.operation.collectAsStateWithLifecycle()
    val free = !cache.busy && !vm.leftovers.busy && operation?.finished != false
    var categoryName by rememberSaveable(system) { mutableStateOf(if(system) CacheScope.SYSTEM.name else CacheScope.USER.name) }
    val category = CacheScope.valueOf(categoryName)
    val categoryGroups = CachePolicy.inScope(cache.groups, category)
    val visible = cache.visible(category)
    val chosen = CachePolicy.selectedInScope(cache.groups, cache.selected, category)
    val clipboard = LocalClipboardManager.current

    Scaffold(topBar = { BackTopBar(stringResource(R.string.cache_scanner), onBack = { vm.back() }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(when(category) {
                    CacheScope.USER -> R.string.cache_user_help
                    CacheScope.SYSTEM -> R.string.cache_system_help
                    CacheScope.BOTH -> R.string.cache_both_help
                }))
                Text(stringResource(R.string.cache_scope), style = MaterialTheme.typography.bodySmall)
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.cache_scanner), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.cache_app_type), style = MaterialTheme.typography.labelMedium)
                        var categoryMenu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { categoryMenu = true }, enabled = free, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(category.titleRes), modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                                CacheScope.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(option.titleRes)) },
                                        onClick = { categoryName = option.name; categoryMenu = false },
                                    )
                                }
                            }
                        }
                        if(cache.partial) Text(stringResource(R.string.cache_partial))
                        cache.scannedAt?.let { time ->
                            Text(stringResource(R.string.cache_total, formatSize(categoryGroups.sumOf { it.bytes ?: 0 })))
                            if(category == CacheScope.BOTH) Text(stringResource(R.string.cache_totals,
                                formatSize(cache.groups.filter { !it.isSystem }.sumOf { it.bytes ?: 0 }),
                                formatSize(cache.groups.filter { it.isSystem }.sumOf { it.bytes ?: 0 })))
                            categoryGroups.mapNotNull { g -> cache.residueFor(g)?.let { minOf(it.bytes, g.bytes ?: 0) } }.sum().takeIf { it > 0 }?.let {
                                Text(stringResource(R.string.cache_residue_total, formatSize(it)), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(stringResource(R.string.cache_timestamp, DateFormat.getDateTimeInstance().format(Date(time))), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.cache_unknown_count, categoryGroups.count { it.bytes == null }), style = MaterialTheme.typography.bodySmall)
                        } ?: Text(stringResource(R.string.cache_not_scanned))
                        Button(onClick = { cache.scan(category) }, enabled = free) { Text(stringResource(R.string.cache_scan_selected)) }
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
                Text(stringResource(cache.phase))
                LinearProgressIndicator(progress = { if(cache.progress.second > 0) cache.progress.first.toFloat() / cache.progress.second else 0f }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.cache_progress, cache.progress.first, cache.progress.second))
                Text(cache.currentApp, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = cache::cancel, enabled = !cache.stopRequested) { Text(stringResource(R.string.cancel)) }
            }
            cache.notice?.let { message -> item { Text(stringResource(message.resource, *message.args.toTypedArray())) } }
            item {
                Text(stringResource(R.string.cache_clear_method), style = MaterialTheme.typography.titleMedium)
                var methodMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { methodMenu = true }, enabled = free, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(cache.method.titleRes), Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = methodMenu, onDismissRequest = { methodMenu = false }) {
                        CacheClearMethod.entries.forEach { method -> DropdownMenuItem(
                            text = { Text(stringResource(method.titleRes)) },
                            onClick = { cache.method = method; methodMenu = false },
                        ) }
                    }
                }
                if(cache.method == CacheClearMethod.SETTINGS) {
                    Text(stringResource(R.string.cache_settings_help))
                    if(!settingsAvailable) {
                        Text(stringResource(R.string.cache_settings_enable_help))
                        OutlinedButton(onClick = { showAccessDisclosure = true }, enabled = free) {
                            Text(stringResource(R.string.cache_settings_enable))
                        }
                    }
                } else {
                    if(status != ShizukuStatus.READY) Text(stringResource(R.string.cache_needs_shizuku))
                    if(cache.supported == false) Text(stringResource(R.string.cache_unsupported))
                }
            }
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
                    TextButton(onClick = { cache.selectShown(category) }, enabled = free) { Text(stringResource(R.string.cache_select_shown)) }
                    TextButton(onClick = { cache.deselect(category) }, enabled = free) { Text(stringResource(R.string.cache_deselect)) }
                }
                Text(stringResource(R.string.cache_selection, chosen.size, formatSize(chosen.sumOf { it.bytes ?: 0 })))
                Text(stringResource(R.string.cache_hidden_selection), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { cache.requestClear(category) }, enabled = free && chosen.isNotEmpty() &&
                    (if(cache.method == CacheClearMethod.SETTINGS) settingsAvailable else status == ShizukuStatus.READY && cache.supported != false)) { Text(stringResource(R.string.cache_clear)) }
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
                            cache.residueFor(group)?.let { residue ->
                                Text(stringResource(R.string.cache_residue, formatSize(residue.bytes)),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                residue.sharedFolderBytes?.let { Text(stringResource(R.string.cache_residue_folder, formatSize(it)), style = MaterialTheme.typography.bodySmall) }
                            }
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
                    if(cache.pendingMethod == CacheClearMethod.SETTINGS) Text(stringResource(R.string.cache_settings_warning))
                    else Text(stringResource(R.string.cache_confirm_help, formatSize(targets.sumOf { it.bytes ?: 0 })))
                    targets.forEach { Text("• ${it.label}\n${it.packages.joinToString(", ")}") }
                }
            },
            confirmButton = { Button(onClick = cache::confirmClear, enabled = free &&
                (if(cache.pendingMethod == CacheClearMethod.SETTINGS) settingsAvailable else status == ShizukuStatus.READY)) { Text(stringResource(R.string.cache_clear)) } },
            dismissButton = { TextButton(onClick = { cache.pending = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    cache.fallback?.let { offer ->
        // Apps whose cache a confirmed clear could not remove start unticked, like "Select shown".
        var chosen by remember(offer) { mutableStateOf((offer.failed + offer.untried).filter { cache.residueFor(it) == null }.map { it.key }.toSet()) }
        AlertDialog(
            onDismissRequest = { cache.fallback = null },
            title = { Text(stringResource(R.string.cache_fallback_title, (offer.failed + offer.untried).size)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.cache_fallback_help))
                    @Composable fun section(title: Int, items: List<io.github.j0p3s.debloater.data.CacheGroup>) {
                        if(items.isEmpty()) return
                        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                        items.forEach { group ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(checked = group.key in chosen, onCheckedChange = { on -> chosen = if(on) chosen + group.key else chosen - group.key })
                                Column(Modifier.weight(1f)) {
                                    Text(group.label)
                                    Text(listOfNotNull(group.packages.joinToString(", "), group.bytes?.let { formatSize(it) }).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall)
                                    cache.residueFor(group)?.let { residue ->
                                        Text(stringResource(R.string.cache_residue, formatSize(residue.bytes)),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                            }
                        }
                    }
                    section(R.string.cache_fallback_failed, offer.failed)
                    section(R.string.cache_fallback_untried, offer.untried)
                    if(!settingsAvailable) {
                        Text(stringResource(R.string.cache_settings_enable_help), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { showAccessDisclosure = true }) { Text(stringResource(R.string.cache_settings_enable)) }
                    }
                }
            },
            confirmButton = { Button(onClick = { cache.useSettingsFor(chosen) }, enabled = chosen.isNotEmpty() && settingsAvailable && free) {
                Text(stringResource(R.string.cache_fallback_use_settings)) } },
            dismissButton = { TextButton(onClick = { cache.fallback = null }) { Text(stringResource(R.string.cache_fallback_not_now)) } },
        )
    }
    if(showAccessDisclosure) AlertDialog(
        onDismissRequest = { showAccessDisclosure = false },
        title = { Text(stringResource(R.string.cache_settings_enable)) },
        text = { Text(stringResource(R.string.cache_settings_disclosure), Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = {
            showAccessDisclosure = false
            runCatching { context.startActivity(SettingsCacheService.settingsIntent()) }
        }) { Text(stringResource(R.string.open_settings)) } },
        dismissButton = { TextButton(onClick = { showAccessDisclosure = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
