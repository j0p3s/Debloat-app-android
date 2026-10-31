package io.github.j0p3s.debloater.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.data.Action
import io.github.j0p3s.debloater.data.AppInfo
import io.github.j0p3s.debloater.data.AppState
import io.github.j0p3s.debloater.data.sortedBy
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.AppRow
import io.github.j0p3s.debloater.ui.components.BackTopBar
import io.github.j0p3s.debloater.ui.components.SelectionBar
import io.github.j0p3s.debloater.ui.components.SortMenu
import io.github.j0p3s.debloater.ui.components.formatSize
import io.github.j0p3s.debloater.ui.theme.ErrorColor
import io.github.j0p3s.debloater.ui.theme.OkColor
import io.github.j0p3s.debloater.ui.theme.WarnColor

/** The four removal values of the Android Debloat List, with their meaning. */
enum class AdlCategory(val key: String, @param:androidx.annotation.StringRes @get:androidx.annotation.StringRes val titleRes: Int, @param:androidx.annotation.StringRes @get:androidx.annotation.StringRes val meaningRes: Int) {
    DELETE("delete", R.string.delete, R.string.adl_delete_help),
    REPLACE("replace", R.string.adl_replace, R.string.adl_replace_help),
    CAUTION("caution", R.string.adl_caution, R.string.adl_caution_help),
    UNSAFE("unsafe", R.string.unsafe, R.string.adl_unsafe_help),
}

/**
 * Checks the phone against the Android Debloat List (adl.muntashir.dev): which listed apps are
 * installed, grouped by ADL's own removal rating, with links to each app's page on the website.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdlCheckScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val expert by vm.prefs.expertMode.collectAsStateWithLifecycle()
    val selection by vm.debloatSelection.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var category by rememberSaveable { mutableStateOf<AdlCategory?>(null) }
    var showChanged by rememberSaveable { mutableStateOf(false) }
    var details by remember { mutableStateOf<AppInfo?>(null) }
    val sort by vm.prefs.sort.collectAsStateWithLifecycle()

    val listed = remember(apps) { apps.filter { it.entry?.inAdl == true } }
    val active = listed.filter { it.state == AppState.ENABLED }
    val systemApps = apps.count { it.isSystem }
    val visible = remember(listed, category, showChanged, sort) {
        listed.filter { a ->
            (category == null || a.entry?.adlRemoval == category!!.key) && (showChanged || a.state == AppState.ENABLED)
        }.sortedBy(sort)
    }
    val selectedApps = apps.filter { it.packageName in selection }

    fun open(url: String) = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.adl_check), onBack = { vm.back() }) },
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
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(R.string.adl_coverage, listed.size, apps.size),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.adl_stats, active.size, formatSize(active.sumOf { it.totalBytes }), listed.size - active.size, listed.count { it.isSystem }, systemApps),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            AdlCategory.entries.forEach { c ->
                                val ofCat = listed.filter { it.entry?.adlRemoval == c.key }
                                val on = ofCat.count { it.state == AppState.ENABLED }
                                Row {
                                    Text(
                                        stringResource(c.titleRes), Modifier.weight(0.3f), fontWeight = FontWeight.SemiBold,
                                        color = when (c) {
                                            AdlCategory.DELETE -> OkColor; AdlCategory.REPLACE -> MaterialTheme.colorScheme.primary
                                            AdlCategory.CAUTION -> WarnColor; AdlCategory.UNSAFE -> ErrorColor
                                        },
                                    )
                                    Text(stringResource(R.string.adl_active, on, ofCat.size - on), Modifier.weight(0.35f), style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(c.meaningRes), Modifier.weight(0.45f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text(
                                stringResource(R.string.adl_rating_help),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            OutlinedButton(onClick = { open("https://adl.muntashir.dev/") }) { Text(stringResource(R.string.open_adl_home)) }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { FilterChip(category == null, { category = null }, label = { Text(stringResource(R.string.all_count, visibleCount(listed, null, showChanged))) }) }
                        items(AdlCategory.entries.size) { i ->
                            val c = AdlCategory.entries[i]
                            FilterChip(category == c, { category = if (category == c) null else c }, label = { Text("${stringResource(c.titleRes)} (${visibleCount(listed, c, showChanged)})") })
                        }
                    }
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
                item { Text(stringResource(R.string.no_adl), Modifier.padding(24.dp)) }
            }
            items(visible, key = { it.packageName }) { app ->
                val selectable = isSelectable(app, expert)
                val toggle = {
                    if (selectable) vm.setDebloatSelection(if (app.packageName in selection) selection - app.packageName else selection + app.packageName)
                }
                Column {
                    AppRow(app, app.packageName in selection, selectable, onToggle = toggle, onClick = toggle, onInfo = { details = app })
                    Row(Modifier.padding(start = 64.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.adl_alternatives, stringResource(AdlCategory.entries.first { it.key == app.entry!!.adlRemoval }.titleRes), app.entry?.alternatives?.size ?: 0),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        TextButton(onClick = { open(app.entry!!.adlUrl) }) { Text(stringResource(R.string.view_adl)) }
                    }
                }
                HorizontalDivider()
            }
        }
    }

    details?.let { app -> AppDetailsDialog(app, isSelectable(app, expert), vm) { details = null } }
}

private fun visibleCount(listed: List<AppInfo>, c: AdlCategory?, showChanged: Boolean) =
    listed.count { (c == null || it.entry?.adlRemoval == c.key) && (showChanged || it.state == AppState.ENABLED) }
