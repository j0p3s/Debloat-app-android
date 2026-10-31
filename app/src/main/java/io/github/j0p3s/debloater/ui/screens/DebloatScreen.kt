package io.github.j0p3s.debloater.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.data.Action
import io.github.j0p3s.debloater.data.AppInfo
import io.github.j0p3s.debloater.data.AppState
import io.github.j0p3s.debloater.data.SafetyLevel
import io.github.j0p3s.debloater.ops.OperationEngine
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.AppRow
import io.github.j0p3s.debloater.ui.components.BackTopBar
import io.github.j0p3s.debloater.ui.components.SortMenu
import io.github.j0p3s.debloater.ui.components.VendorChips
import io.github.j0p3s.debloater.data.sortedBy
import io.github.j0p3s.debloater.ui.theme.levelColor

private fun levelInfo(level: SafetyLevel) = when (level) {
    SafetyLevel.RECOMMENDED -> "Bloatware, ads, telemetry, duplicates and apps you can get back from a store. Recommended for almost everyone; check you have another app for anything you use (browser, gallery…)."
    SafetyLevel.SAFE -> "Safe to remove: the system is not affected, but these are apps you might still use (for example YouTube, Maps or Gmail)."
    SafetyLevel.EXTREME -> "Extreme debloat. These can break features such as notifications, payments, updates, the camera or calls. Read every warning, and run a test first."
    SafetyLevel.EXPERT -> "Expert mode: packages that can break important features (rated Expert by UAD-NG). Prefer Disable, and test first."
    SafetyLevel.UNSAFE -> "Expert mode: critical packages. Removing them can cause boot loops or a non-working phone. Disable rather than remove, and test first."
    SafetyLevel.UNKNOWN -> "Expert mode: system apps that are not in the debloat list. Their effect is unknown."
}

/** Whether the user may select this app for disable/remove. */
fun isSelectable(app: AppInfo, expert: Boolean) =
    !app.isProtected && (expert || !app.level.needsExpertMode || (app.level == SafetyLevel.UNKNOWN && !app.isSystem))

@Composable
fun DebloatScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val expert by vm.prefs.expertMode.collectAsStateWithLifecycle()
    val selection by vm.debloatSelection.collectAsStateWithLifecycle()

    val tabs = remember(expert) {
        buildList {
            add(SafetyLevel.RECOMMENDED); add(SafetyLevel.SAFE); add(SafetyLevel.EXTREME)
            if (expert) { add(SafetyLevel.EXPERT); add(SafetyLevel.UNSAFE); add(SafetyLevel.UNKNOWN) }
        }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (tab >= tabs.size) tab = 0
    val level = tabs[tab]
    var query by rememberSaveable { mutableStateOf("") }
    var showChanged by rememberSaveable { mutableStateOf(false) }
    var vendor by rememberSaveable { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<AppInfo?>(null) }
    val sort by vm.prefs.sort.collectAsStateWithLifecycle()
    val deviceVendor = vm.repo.deviceVendor?.name

    // Apps that can appear in any of the visible tabs, used for vendor counts and quick selection.
    val inTabs = remember(apps, tabs) {
        apps.filter { a -> a.level in tabs && (a.level != SafetyLevel.UNKNOWN || a.isSystem) }
    }
    val vendorCounts = remember(inTabs, showChanged) {
        inTabs.filter { showChanged || it.state == AppState.ENABLED }.groupingBy { it.vendor }.eachCount()
    }

    val visible = remember(apps, level, query, showChanged, vendor, sort) {
        apps.filter { a ->
            a.level == level &&
                (vendor == null || a.vendor == vendor) &&
                (level != SafetyLevel.UNKNOWN || a.isSystem) &&
                (showChanged || a.state == AppState.ENABLED) &&
                (query.isBlank() || a.label.contains(query, true) || a.packageName.contains(query, true) ||
                    a.entry?.vendor?.contains(query, true) == true)
        }.sortedBy(sort)
    }
    val selectedApps = apps.filter { it.packageName in selection }
    val dark = isSystemInDarkTheme()

    Scaffold(
        topBar = { BackTopBar("Debloat", onBack = { vm.back() }) },
        bottomBar = {
            if (selection.isNotEmpty()) {
                BottomAppBar {
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("${selection.size} selected", style = MaterialTheme.typography.labelLarge)
                        Text(OperationEngine.fmtSize(selectedApps.sumOf { it.totalBytes }), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { vm.request(selectedApps, Action.REMOVE, dryRun = true) }) { Text("Test") }
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
        Column(Modifier.padding(pad).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, l ->
                    val count = apps.count {
                        it.level == l && it.state == AppState.ENABLED && (l != SafetyLevel.UNKNOWN || it.isSystem) &&
                            (vendor == null || it.vendor == vendor)
                    }
                    Tab(selected = i == tab, onClick = { tab = i }, text = { Text("${l.title} ($count)", color = levelColor(l, dark)) })
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Vendor" + (deviceVendor?.let { " · this phone: $it" } ?: ""),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        VendorChips(vendorCounts, vendor, deviceVendor) { vendor = it }
                        vendor?.let { v ->
                            VendorQuickSelect(
                                vendor = v,
                                isThisPhone = v == deviceVendor,
                                apps = inTabs.filter { it.vendor == v && it.state == AppState.ENABLED && isSelectable(it, expert) },
                                selection = selection,
                                onSelect = { vm.setDebloatSelection(it) },
                            )
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = levelColor(level, dark).copy(alpha = 0.12f),
                            ),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(levelInfo(level), style = MaterialTheme.typography.bodyMedium)
                                val active = apps.filter { it.level == level && it.state == AppState.ENABLED && (level != SafetyLevel.UNKNOWN || it.isSystem) }
                                Text(
                                    "${active.size} active apps use ${OperationEngine.fmtSize(active.sumOf { it.totalBytes })} " +
                                        "(${OperationEngine.fmtSize(active.sumOf { it.appBytes })} app + ${OperationEngine.fmtSize(active.sumOf { it.dataBytes })} data)",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            placeholder = { Text("Search name, package or vendor") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(showChanged, { showChanged = !showChanged }, label = { Text("Show disabled/removed") })
                            SortMenu(sort, vm.prefs::setSort)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${visible.size} apps · ${OperationEngine.fmtSize(visible.sumOf { it.totalBytes })}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                vm.setDebloatSelection(selection + visible.filter { isSelectable(it, expert) && it.state == AppState.ENABLED }.map { it.packageName })
                            }) { Text("Select all") }
                            TextButton(onClick = { vm.setDebloatSelection(selection - visible.map { it.packageName }.toSet()) }) { Text("Clear") }
                        }
                    }
                }
                if (loading && apps.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else if (visible.isEmpty()) {
                    item {
                        Text(
                            "No ${level.title.lowercase()} packages ${vendor?.let { "from $it " } ?: ""}${if (showChanged) "" else "left "}on this device.",
                            Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(visible, key = { it.packageName }) { app ->
                    val selectable = isSelectable(app, expert)
                    val toggle = {
                        if (selectable) vm.setDebloatSelection(if (app.packageName in selection) selection - app.packageName else selection + app.packageName)
                    }
                    AppRow(app, app.packageName in selection, selectable, onToggle = toggle, onClick = toggle, onInfo = { details = app })
                    HorizontalDivider()
                }
            }
        }
    }
    details?.let { app -> AppDetailsDialog(app, isSelectable(app, expert), vm) { details = null } }
}

/**
 * Bulk selection for one vendor across all tiers: Recommended only, Recommended + Safe, or
 * everything including Extreme (and Unsafe/Unlisted when expert mode is on).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun VendorQuickSelect(
    vendor: String,
    isThisPhone: Boolean,
    apps: List<AppInfo>,
    selection: Set<String>,
    onSelect: (Set<String>) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val rec = apps.filter { it.level == SafetyLevel.RECOMMENDED }
    val safe = apps.filter { it.level == SafetyLevel.SAFE }
    val vendorPkgs = apps.map { it.packageName }.toSet()
    val selectedHere = selection.count { it in vendorPkgs }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Debloat $vendor apps", style = MaterialTheme.typography.titleSmall)
            Text(
                (if (isThisPhone) "Made by the maker of this phone. " else "") +
                    "${apps.size} active app(s), ${OperationEngine.fmtSize(apps.sumOf { it.totalBytes })}. $selectedHere selected.",
                style = MaterialTheme.typography.bodySmall,
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                OutlinedButton(onClick = { onSelect(selection - vendorPkgs + rec.map { it.packageName }) }, enabled = rec.isNotEmpty()) {
                    Text("Recommended (${rec.size})", color = levelColor(SafetyLevel.RECOMMENDED, dark))
                }
                OutlinedButton(onClick = { onSelect(selection - vendorPkgs + (rec + safe).map { it.packageName }) }, enabled = rec.size + safe.size > 0) {
                    Text("+ Safe (${rec.size + safe.size})", color = levelColor(SafetyLevel.SAFE, dark))
                }
                OutlinedButton(onClick = { onSelect(selection + vendorPkgs) }, enabled = apps.isNotEmpty()) {
                    Text("Everything (${apps.size})", color = levelColor(SafetyLevel.EXTREME, dark))
                }
                TextButton(onClick = { onSelect(selection - vendorPkgs) }, enabled = selectedHere > 0) { Text("Clear") }
            }
            if (apps.any { it.level.isRisky }) {
                Text(
                    "\"Everything\" includes ${apps.filter { it.level.isRisky }.map { it.level.title }.distinct().joinToString("/")} packages: read the warnings and run a test first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = levelColor(SafetyLevel.EXTREME, dark),
                )
            }
        }
    }
}
