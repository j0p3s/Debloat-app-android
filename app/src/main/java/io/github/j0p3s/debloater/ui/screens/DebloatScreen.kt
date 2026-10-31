package io.github.j0p3s.debloater.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.data.Action
import io.github.j0p3s.debloater.data.AppInfo
import io.github.j0p3s.debloater.data.AppState
import io.github.j0p3s.debloater.data.SafetyLevel
import io.github.j0p3s.debloater.data.sortedBy
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.AppRow
import io.github.j0p3s.debloater.ui.components.BackTopBar
import io.github.j0p3s.debloater.ui.components.SelectionBar
import io.github.j0p3s.debloater.ui.components.SortMenu
import io.github.j0p3s.debloater.ui.components.VendorChips
import io.github.j0p3s.debloater.ui.components.formatSize
import io.github.j0p3s.debloater.ui.theme.levelColor

@Composable
private fun levelInfo(level: SafetyLevel) = when (level) {
    SafetyLevel.RECOMMENDED -> stringResource(R.string.tier_recommended_info)
    SafetyLevel.SAFE -> stringResource(R.string.tier_safe_info)
    SafetyLevel.EXTREME -> stringResource(R.string.tier_extreme_info)
    SafetyLevel.EXPERT -> stringResource(R.string.tier_expert_info)
    SafetyLevel.UNSAFE -> stringResource(R.string.tier_unsafe_info)
    SafetyLevel.UNKNOWN -> stringResource(R.string.tier_unknown_info)
}

/** Whether the user may select this app for disable/remove. */
fun isSelectable(app: AppInfo, expert: Boolean) =
    !app.isProtected && (expert || !app.level.needsExpertMode || (app.level == SafetyLevel.UNKNOWN && !app.isSystem))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebloatScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val expert by vm.prefs.expertMode.collectAsStateWithLifecycle()
    val selection by vm.debloatSelection.collectAsStateWithLifecycle()

    val tiers = remember(expert) {
        buildList {
            add(SafetyLevel.RECOMMENDED); add(SafetyLevel.SAFE); add(SafetyLevel.EXTREME)
            if (expert) { add(SafetyLevel.EXPERT); add(SafetyLevel.UNSAFE); add(SafetyLevel.UNKNOWN) }
        }
    }
    val allTiers = tiers.toSet()
    // The tiers shown in the list. Tabs set it to all tiers or one tier; the vendor buttons set it to
    // exactly what they select (e.g. Recommended + Safe). Saved as names so it survives rotation.
    var scopeNames by rememberSaveable { mutableStateOf(SafetyLevel.RECOMMENDED.name) }
    val scope: Set<SafetyLevel> = scopeNames.split(',').mapNotNull { n -> SafetyLevel.entries.firstOrNull { it.name == n } }
        .filter { it in allTiers }.toSet().ifEmpty { allTiers }
    fun setScope(levels: Set<SafetyLevel>) { scopeNames = levels.joinToString(",") { it.name } }
    val isAll = scope == allTiers
    val single: SafetyLevel? = scope.singleOrNull()
    // Tab 0 is All; tabs 1.. are the tiers. A combined scope highlights All.
    val selectedTab = if (single != null && !isAll) tiers.indexOf(single) + 1 else 0
    var query by rememberSaveable { mutableStateOf("") }
    var showChanged by rememberSaveable { mutableStateOf(false) }
    var vendor by rememberSaveable { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<AppInfo?>(null) }
    val sort by vm.prefs.sort.collectAsStateWithLifecycle()
    val deviceVendor = vm.repo.deviceVendor?.name

    // Apps that can appear in any of the visible tabs, used for vendor counts and quick selection.
    val inTabs = remember(apps, tiers) {
        apps.filter { a -> a.level in tiers && (a.level != SafetyLevel.UNKNOWN || a.isSystem) }
    }
    val vendorCounts = remember(inTabs, showChanged) {
        inTabs.filter { showChanged || it.state == AppState.ENABLED }.groupingBy { it.vendor }.eachCount()
    }

    val visible = remember(apps, scope, query, showChanged, vendor, sort) {
        apps.filter { a ->
            a.level in scope &&
                (vendor == null || a.vendor == vendor) &&
                (a.level != SafetyLevel.UNKNOWN || a.isSystem) &&
                (showChanged || a.state == AppState.ENABLED) &&
                (query.isBlank() || a.label.contains(query, true) || a.packageName.contains(query, true) ||
                    a.entry?.vendor?.contains(query, true) == true)
        }.sortedBy(sort)
    }
    val selectedApps = apps.filter { it.packageName in selection }
    val dark = isSystemInDarkTheme()
    val levelTitles = SafetyLevel.entries.associateWith { stringResource(it.titleRes) }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.debloat), onBack = { vm.back() }) },
        bottomBar = {
            if (selection.isNotEmpty()) {
                SelectionBar(
                    count = selection.size,
                    size = formatSize(selectedApps.sumOf { it.totalBytes }),
                    onTest = { vm.request(selectedApps, Action.DISABLE, dryRun = true) },
                    onDisable = { vm.request(selectedApps, Action.DISABLE, dryRun = false) },
                    onRemove = { vm.request(selectedApps, Action.REMOVE, dryRun = false) },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
                fun count(levels: Set<SafetyLevel>) = apps.count {
                    it.level in levels && it.state == AppState.ENABLED && (it.level != SafetyLevel.UNKNOWN || it.isSystem) &&
                        (vendor == null || it.vendor == vendor)
                }
                Tab(selected = selectedTab == 0, onClick = { setScope(allTiers) }, text = { Text(stringResource(R.string.all_count, count(allTiers))) })
                tiers.forEachIndexed { i, l ->
                    Tab(
                        selected = selectedTab == i + 1,
                        onClick = { setScope(setOf(l)) },
                        text = { Text("${stringResource(l.titleRes)} (${count(setOf(l))})", color = levelColor(l, dark)) },
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.vendor_filter, deviceVendor ?: "—"),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        VendorChips(vendorCounts, vendor, deviceVendor) { vendor = it }
                        vendor?.let { v ->
                            VendorQuickSelect(
                                vendor = v,
                                apps = inTabs.filter { it.vendor == v && it.state == AppState.ENABLED && isSelectable(it, expert) },
                                selection = selection,
                                onSelect = { newSelection, levels ->
                                    vm.setDebloatSelection(newSelection)
                                    // Show exactly the apps the button is about.
                                    setScope(levels ?: allTiers)
                                },
                            )
                        }
                        if (!isAll && single == null) {
                            InputChip(
                                selected = true,
                                onClick = { setScope(allTiers) },
                                label = { Text(stringResource(R.string.showing_tiers, tiers.filter { it in scope }.joinToString(" + ") { levelTitles.getValue(it) })) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.show_all_tiers), modifier = Modifier.size(18.dp)) },
                            )
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = (single?.let { levelColor(it, dark) } ?: MaterialTheme.colorScheme.secondary).copy(alpha = 0.12f),
                            ),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    single?.let { levelInfo(it) }
                                        ?: stringResource(R.string.all_tiers_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                // Respects the vendor filter, like the list below.
                                val active = apps.filter {
                                    it.level in scope && it.state == AppState.ENABLED && (it.level != SafetyLevel.UNKNOWN || it.isSystem) &&
                                        (vendor == null || it.vendor == vendor)
                                }
                                Text(
                                    stringResource(R.string.active_apps_size, active.size, formatSize(active.sumOf { it.totalBytes }), formatSize(active.sumOf { it.appBytes }), formatSize(active.sumOf { it.dataBytes })),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            placeholder = { Text(stringResource(R.string.search_packages)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(showChanged, { showChanged = !showChanged }, label = { Text(stringResource(R.string.show_changed)) })
                            SortMenu(sort, vm.prefs::setSort)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.apps_size, visible.size, formatSize(visible.sumOf { it.totalBytes })), style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = {
                                vm.setDebloatSelection(selection + visible.filter { isSelectable(it, expert) && it.state == AppState.ENABLED }.map { it.packageName })
                            }) { Text(stringResource(R.string.select_all)) }
                            TextButton(onClick = { vm.setDebloatSelection(selection - visible.map { it.packageName }.toSet()) }) { Text(stringResource(R.string.clear)) }
                        }
                    }
                }
                if (loading && apps.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else if (visible.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.no_apps),
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
    apps: List<AppInfo>,
    selection: Set<String>,
    /** New selection, and the tiers the list should show (null = all tiers). */
    onSelect: (Set<String>, Set<SafetyLevel>?) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val rec = apps.filter { it.level == SafetyLevel.RECOMMENDED }
    val safe = apps.filter { it.level == SafetyLevel.SAFE }
    val vendorPkgs = apps.map { it.packageName }.toSet()
    val selectedHere = selection.count { it in vendorPkgs }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.vendor_title, vendor), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.vendor_selection, apps.size, formatSize(apps.sumOf { it.totalBytes }), selectedHere),
                style = MaterialTheme.typography.bodySmall,
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                OutlinedButton(onClick = { onSelect(selection - vendorPkgs + rec.map { it.packageName }, setOf(SafetyLevel.RECOMMENDED)) }, enabled = rec.isNotEmpty()) {
                    Text(stringResource(R.string.select_recommended, rec.size), color = levelColor(SafetyLevel.RECOMMENDED, dark))
                }
                OutlinedButton(onClick = { onSelect(selection - vendorPkgs + (rec + safe).map { it.packageName }, setOf(SafetyLevel.RECOMMENDED, SafetyLevel.SAFE)) }, enabled = rec.size + safe.size > 0) {
                    Text(stringResource(R.string.select_safe, rec.size + safe.size), color = levelColor(SafetyLevel.SAFE, dark))
                }
                OutlinedButton(onClick = { onSelect(selection + vendorPkgs, null) }, enabled = apps.isNotEmpty()) {
                    Text(stringResource(R.string.select_everything, apps.size), color = levelColor(SafetyLevel.EXTREME, dark))
                }
                TextButton(onClick = { onSelect(selection - vendorPkgs, null) }, enabled = selectedHere > 0) { Text(stringResource(R.string.clear)) }
            }
            if (apps.any { it.level.isRisky }) {
                Text(
                    stringResource(R.string.everything_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = levelColor(SafetyLevel.EXTREME, dark),
                )
            }
        }
    }
}
