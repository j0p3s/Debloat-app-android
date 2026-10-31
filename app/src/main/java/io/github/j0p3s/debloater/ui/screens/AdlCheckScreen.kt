package io.github.j0p3s.debloater.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.data.Action
import io.github.j0p3s.debloater.data.AppInfo
import io.github.j0p3s.debloater.data.AppState
import io.github.j0p3s.debloater.ops.OperationEngine
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.AppRow
import io.github.j0p3s.debloater.ui.components.BackTopBar
import io.github.j0p3s.debloater.ui.theme.ErrorColor
import io.github.j0p3s.debloater.ui.theme.OkColor
import io.github.j0p3s.debloater.ui.theme.WarnColor

/** The four removal values of the Android Debloat List, with their meaning. */
enum class AdlCategory(val key: String, val title: String, val meaning: String) {
    DELETE("delete", "Delete", "Safe to just get rid of"),
    REPLACE("replace", "Replace", "Remove after installing a replacement"),
    CAUTION("caution", "Caution", "Might not be safe to remove"),
    UNSAFE("unsafe", "Unsafe", "Unsafe to remove"),
}

/**
 * Checks the phone against the Android Debloat List (adl.muntashir.dev): which listed apps are
 * installed, grouped by ADL's own removal rating, with links to each app's page on the website.
 */
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

    val listed = remember(apps) { apps.filter { it.entry?.inAdl == true } }
    val active = listed.filter { it.state == AppState.ENABLED }
    val systemApps = apps.count { it.isSystem }
    val visible = remember(listed, category, showChanged) {
        listed.filter { a ->
            (category == null || a.entry?.adlRemoval == category!!.key) && (showChanged || a.state == AppState.ENABLED)
        }
    }
    val selectedApps = apps.filter { it.packageName in selection }

    fun open(url: String) = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Scaffold(
        topBar = { BackTopBar("Android Debloat List check", onBack = { vm.back() }) },
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
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "${listed.size} of the ${apps.size} packages on this phone are in the Android Debloat List",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${active.size} are still active (${OperationEngine.fmtSize(active.sumOf { it.totalBytes })}), " +
                                    "${listed.size - active.size} already disabled or removed. " +
                                    "${listed.count { it.isSystem }} of your $systemApps system apps are covered.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            AdlCategory.entries.forEach { c ->
                                val ofCat = listed.filter { it.entry?.adlRemoval == c.key }
                                val on = ofCat.count { it.state == AppState.ENABLED }
                                Row {
                                    Text(
                                        c.title, Modifier.weight(0.3f), fontWeight = FontWeight.SemiBold,
                                        color = when (c) {
                                            AdlCategory.DELETE -> OkColor; AdlCategory.REPLACE -> MaterialTheme.colorScheme.primary
                                            AdlCategory.CAUTION -> WarnColor; AdlCategory.UNSAFE -> ErrorColor
                                        },
                                    )
                                    Text("$on active · ${ofCat.size - on} done", Modifier.weight(0.35f), style = MaterialTheme.typography.bodySmall)
                                    Text(c.meaning, Modifier.weight(0.45f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text(
                                "Ratings in the app's lists combine ADL with other lists; the most cautious one is used. " +
                                    "ADL's own rating is shown here.",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            OutlinedButton(onClick = { open("https://adl.muntashir.dev/") }) { Text("Open adl.muntashir.dev") }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { FilterChip(category == null, { category = null }, label = { Text("All (${visibleCount(listed, null, showChanged)})") }) }
                        items(AdlCategory.entries.size) { i ->
                            val c = AdlCategory.entries[i]
                            FilterChip(category == c, { category = if (category == c) null else c }, label = { Text("${c.title} (${visibleCount(listed, c, showChanged)})") })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(showChanged, { showChanged = !showChanged }, label = { Text("Show disabled/removed") })
                        Box(Modifier.weight(1f))
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
                item { Text("No matching ADL apps on this phone.", Modifier.padding(24.dp)) }
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
                            "ADL: ${app.entry?.adlRemoval}" + (app.entry?.alternatives?.takeIf { it.isNotEmpty() }?.let { " · ${it.size} open-source alternative(s)" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        TextButton(onClick = { open(app.entry!!.adlUrl) }) { Text("View on ADL") }
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
