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
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.AppRow
import io.github.j0p3s.debloater.ui.components.BackTopBar
import io.github.j0p3s.debloater.ui.theme.levelColor

private fun levelInfo(level: SafetyLevel) = when (level) {
    SafetyLevel.RECOMMENDED -> "Bloatware, ads, telemetry and duplicate apps. Recommended for almost everyone. The system keeps working normally."
    SafetyLevel.SAFE -> "Safe to remove: the system is not affected, but these are apps you might still use (for example YouTube, Maps or Gmail)."
    SafetyLevel.EXTREME -> "Extreme debloat. These can break features such as notifications, payments, updates, the camera or calls. Read every warning, and run a test first."
    SafetyLevel.UNSAFE -> "Expert mode: critical packages. Removing them can cause boot loops or a non-working phone. Disable rather than remove, and test first."
    SafetyLevel.UNKNOWN -> "Expert mode: system apps that are not in the debloat list. Their effect is unknown."
}

/** Whether the user may select this app for disable/remove. */
fun isSelectable(app: AppInfo, expert: Boolean) =
    !app.isProtected && (app.level != SafetyLevel.UNSAFE || expert) && (app.level != SafetyLevel.UNKNOWN || expert || !app.isSystem)

@Composable
fun DebloatScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val expert by vm.prefs.expertMode.collectAsStateWithLifecycle()
    val selection by vm.debloatSelection.collectAsStateWithLifecycle()

    val tabs = remember(expert) {
        buildList {
            add(SafetyLevel.RECOMMENDED); add(SafetyLevel.SAFE); add(SafetyLevel.EXTREME)
            if (expert) { add(SafetyLevel.UNSAFE); add(SafetyLevel.UNKNOWN) }
        }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (tab >= tabs.size) tab = 0
    val level = tabs[tab]
    var query by rememberSaveable { mutableStateOf("") }
    var showChanged by rememberSaveable { mutableStateOf(false) }

    val visible = remember(apps, level, query, showChanged) {
        apps.filter { a ->
            a.level == level &&
                (level != SafetyLevel.UNKNOWN || a.isSystem) &&
                (showChanged || a.state == AppState.ENABLED) &&
                (query.isBlank() || a.label.contains(query, true) || a.packageName.contains(query, true) ||
                    a.entry?.vendor?.contains(query, true) == true)
        }
    }
    val selectedApps = apps.filter { it.packageName in selection }
    val dark = isSystemInDarkTheme()

    Scaffold(
        topBar = { BackTopBar("Debloat", onBack = { vm.back() }) },
        bottomBar = {
            if (selection.isNotEmpty()) {
                BottomAppBar {
                    Text("${selection.size} selected", Modifier.padding(start = 12.dp).weight(1f))
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
                    val count = apps.count { it.level == l && it.state == AppState.ENABLED && (l != SafetyLevel.UNKNOWN || it.isSystem) }
                    Tab(selected = i == tab, onClick = { tab = i }, text = { Text("${l.title} ($count)", color = levelColor(l, dark)) })
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = levelColor(level, dark).copy(alpha = 0.12f),
                            ),
                        ) {
                            Text(levelInfo(level), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            placeholder = { Text("Search name, package or vendor") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(showChanged, { showChanged = !showChanged }, label = { Text("Show disabled/removed") })
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
                            "No ${level.title.lowercase()} packages ${if (showChanged) "" else "left "}on this device.",
                            Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(visible, key = { it.packageName }) { app ->
                    val selectable = isSelectable(app, expert)
                    val toggle = {
                        if (selectable) vm.setDebloatSelection(if (app.packageName in selection) selection - app.packageName else selection + app.packageName)
                    }
                    AppRow(app, app.packageName in selection, selectable, onToggle = toggle, onClick = toggle)
                    HorizontalDivider()
                }
            }
        }
    }
}
