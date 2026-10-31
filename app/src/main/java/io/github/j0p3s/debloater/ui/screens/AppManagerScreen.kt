package io.github.j0p3s.debloater.ui.screens

import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.data.Action
import io.github.j0p3s.debloater.data.AppInfo
import io.github.j0p3s.debloater.data.AppState
import io.github.j0p3s.debloater.data.SpaceEstimator
import io.github.j0p3s.debloater.ops.OperationEngine
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.AppIcon
import io.github.j0p3s.debloater.ui.components.AppRow
import io.github.j0p3s.debloater.ui.components.BackTopBar
import io.github.j0p3s.debloater.ui.components.SortMenu
import io.github.j0p3s.debloater.ui.components.VendorChips
import io.github.j0p3s.debloater.data.sortedBy
import io.github.j0p3s.debloater.ui.components.LevelPill
import io.github.j0p3s.debloater.ui.components.StatePill
import io.github.j0p3s.debloater.ui.components.WarningText

private enum class TypeFilter(val title: String) { USER("User apps"), SYSTEM("System apps"), ALL("All") }
private enum class StateFilter(val title: String) { ANY("Any state"), ENABLED("Enabled"), DISABLED("Disabled"), REMOVED("Removed") }

@Composable
fun AppManagerScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val expert by vm.prefs.expertMode.collectAsStateWithLifecycle()
    val selection by vm.managerSelection.collectAsStateWithLifecycle()

    var type by rememberSaveable { mutableStateOf(TypeFilter.USER) }
    var stateFilter by rememberSaveable { mutableStateOf(StateFilter.ANY) }
    val sort by vm.prefs.sort.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var details by remember { mutableStateOf<AppInfo?>(null) }

    var vendor by rememberSaveable { mutableStateOf<String?>(null) }
    val ofType = remember(apps, type) {
        apps.filter { a -> when (type) { TypeFilter.USER -> !a.isSystem; TypeFilter.SYSTEM -> a.isSystem; TypeFilter.ALL -> true } }
    }
    val vendorCounts = remember(ofType) { ofType.groupingBy { it.vendor }.eachCount() }
    val visible = remember(ofType, stateFilter, sort, query, vendor) {
        ofType.filter { a ->
            (vendor == null || a.vendor == vendor) &&
                when (stateFilter) {
                    StateFilter.ANY -> true
                    StateFilter.ENABLED -> a.state == AppState.ENABLED
                    StateFilter.DISABLED -> a.state == AppState.DISABLED
                    StateFilter.REMOVED -> a.state == AppState.UNINSTALLED
                } &&
                (query.isBlank() || a.label.contains(query, true) || a.packageName.contains(query, true))
        }.sortedBy(sort)
    }
    val selectedApps = apps.filter { it.packageName in selection }

    Scaffold(
        topBar = { BackTopBar("App manager", onBack = { vm.back() }) },
        bottomBar = {
            if (selection.isNotEmpty()) {
                BottomAppBar {
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("${selection.size}", style = MaterialTheme.typography.labelLarge)
                        Text(OperationEngine.fmtSize(selectedApps.sumOf { it.totalBytes }), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { vm.request(selectedApps, Action.REMOVE, dryRun = true) }) { Text("Test") }
                    if (selectedApps.any { it.state != AppState.ENABLED }) {
                        TextButton(onClick = { vm.request(selectedApps.filter { it.state != AppState.ENABLED }, Action.RESTORE, dryRun = false) }) { Text("Restore") }
                    }
                    TextButton(onClick = { vm.request(selectedApps, Action.DISABLE, dryRun = false) }) { Text("Disable") }
                    Button(
                        onClick = { vm.request(selectedApps, Action.REMOVE, dryRun = false) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Remove") }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TypeFilter.entries.forEach { FilterChip(type == it, { type = it }, label = { Text(it.title) }) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StateFilter.entries.forEach { FilterChip(stateFilter == it, { stateFilter = it }, label = { Text(it.title) }) }
                    }
                    VendorChips(vendorCounts, vendor, vm.repo.deviceVendor?.name) { vendor = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${visible.size} apps · ${OperationEngine.fmtSize(visible.sumOf { it.totalBytes })}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        SortMenu(sort, vm.prefs::setSort)
                        TextButton(onClick = { vm.setManagerSelection(selection + visible.filter { isSelectable(it, expert) }.map { it.packageName }) }) { Text("Select all") }
                        TextButton(onClick = { vm.setManagerSelection(emptySet()) }) { Text("Clear") }
                    }
                }
            }
            if (loading && apps.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
            items(visible, key = { it.packageName }) { app ->
                val selectable = isSelectable(app, expert)
                AppRow(
                    app, app.packageName in selection, selectable,
                    onToggle = { vm.setManagerSelection(if (app.packageName in selection) selection - app.packageName else selection + app.packageName) },
                    onClick = { details = app },
                    showDescription = false,
                )
                HorizontalDivider()
            }
        }
    }

    details?.let { app -> AppDetailsDialog(app, isSelectable(app, expert), vm) { details = null } }
}

@Composable
fun AppDetailsDialog(app: AppInfo, canModify: Boolean, vm: MainViewModel, onDismiss: () -> Unit) {
    val installed = vm.apps.collectAsStateWithLifecycle().value.associateBy { it.packageName }
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppIcon(app.applicationInfo, Modifier.size(48.dp)) },
        title = { Text(app.label) },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${app.packageName} · ${app.vendor}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { LevelPill(app.level); StatePill(app.state) }
                Text("Version ${app.versionName} (${app.versionCode})", style = MaterialTheme.typography.bodySmall)
                Text("${if (app.isSystem) "System" else "User"} app · ${app.apkPaths.size} APK file(s)", style = MaterialTheme.typography.bodySmall)
                SizeTable(app)
                app.entry?.let { e ->
                    if (e.description.isNotBlank()) Text(e.description, style = MaterialTheme.typography.bodySmall)
                    e.warning?.let { w -> WarningText(w) }
                    if (e.oftenCantUninstall) {
                        WarningText("Often cannot be uninstalled on this ROM. Use Disable if Remove fails.")
                    }
                    if (e.neededBy.isNotEmpty()) {
                        WarningText("Needed by: " + e.neededBy.joinToString { p -> installed[p]?.let { "${it.label} (installed)" } ?: p })
                    }
                    if (e.dependencies.isNotEmpty()) {
                        Text("Depends on: " + e.dependencies.joinToString { p -> installed[p]?.label ?: p }, style = MaterialTheme.typography.bodySmall)
                    }
                    e.details?.takeIf { it != e.description }?.let { d ->
                        Text("Details", style = MaterialTheme.typography.labelLarge)
                        Text(d, style = MaterialTheme.typography.bodySmall)
                    }
                    if (e.alternatives.isNotEmpty()) {
                        Text("Open-source alternatives (Android Debloat List)", style = MaterialTheme.typography.labelLarge)
                        e.alternatives.forEach { alt ->
                            val url = alt.repo ?: "https://f-droid.org/packages/${alt.packageName}/"
                            Text(
                                "• ${alt.label}" + (if (alt.sources.isNotEmpty()) " (${alt.sources})" else "") +
                                    (alt.reason?.let { " – $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { openUrl(ctx, url) },
                            )
                        }
                    }
                    if (e.webLinks.isNotEmpty()) {
                        Text("Links", style = MaterialTheme.typography.labelLarge)
                        e.webLinks.take(6).forEach { url ->
                            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { openUrl(ctx, url) })
                        }
                    }
                    Text(
                        "Rated by: ${e.sources.joinToString()}" + (e.adlRemoval?.let { " · ADL: $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                    )
                    if (e.inAdl) {
                        TextButton(onClick = { openUrl(ctx, e.adlUrl) }) { Text("Open on adl.muntashir.dev") }
                    }
                }
                app.protectedReason?.let { Text("Protected: $it", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
                if (!canModify && !app.isProtected) {
                    Text("Locked. Turn on expert mode in Settings to modify this package.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    if (canModify && app.state == AppState.ENABLED) {
                        OutlinedButton(onClick = { onDismiss(); vm.request(listOf(app), Action.REMOVE, true) }) { Text("Test") }
                        OutlinedButton(onClick = { onDismiss(); vm.request(listOf(app), Action.DISABLE, false) }) { Text("Disable") }
                        Button(
                            onClick = { onDismiss(); vm.request(listOf(app), Action.REMOVE, false) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) { Text(if (app.isSystem) "Remove" else "Uninstall") }
                    }
                    if (app.state == AppState.DISABLED && canModify) {
                        OutlinedButton(onClick = { onDismiss(); vm.request(listOf(app), Action.REMOVE, false) }) { Text("Remove") }
                    }
                    if (app.state != AppState.ENABLED) {
                        Button(onClick = { onDismiss(); vm.request(listOf(app), Action.RESTORE, false) }) {
                            Text(if (app.state == AppState.DISABLED) "Enable" else "Restore")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${app.packageName}".toUri()))
                }
            }) { Text("System app info") }
        },
    )
}

@Composable
private fun SizeTable(app: AppInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        @Composable
        fun line(label: String, value: String, bold: Boolean = false) = Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        }
        line("APK", OperationEngine.fmtSize(app.apkSize))
        if (app.sizeExact) {
            line("App (APK, code, libraries)", OperationEngine.fmtSize(app.appBytes))
            line("Data", OperationEngine.fmtSize(app.dataBytes - app.cacheBytes))
            line("Cache", OperationEngine.fmtSize(app.cacheBytes))
            line("Total", OperationEngine.fmtSize(app.totalBytes), bold = true)
        } else {
            Text("Data size unknown (grant Usage access on the home screen).", style = MaterialTheme.typography.bodySmall)
        }
        if (app.state != AppState.UNINSTALLED) {
            val freed = SpaceEstimator.estimate(app, Action.REMOVE, keepData = false)
            line("Removing without keeping data frees about", OperationEngine.fmtSize(freed), bold = true)
            if (app.isSystem) {
                Text("The APK of a system app stays on the read-only system partition.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun openUrl(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
