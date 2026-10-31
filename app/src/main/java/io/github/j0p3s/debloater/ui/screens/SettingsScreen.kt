package io.github.j0p3s.debloater.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.BuildConfig
import io.github.j0p3s.debloater.data.DebloatDatabase
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.BackTopBar
import io.github.j0p3s.debloater.ui.theme.ErrorColor
import io.github.j0p3s.debloater.ui.theme.OkColor
import io.github.j0p3s.debloater.ui.ListUpdateState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val expert by vm.prefs.expertMode.collectAsStateWithLifecycle()
    val keepData by vm.prefs.keepData.collectAsStateWithLifecycle()
    var askExpert by remember { mutableStateOf(false) }
    var askUpdate by remember { mutableStateOf(false) }
    val listUpdate by vm.listUpdate.collectAsStateWithLifecycle()
    val autoUpdate by vm.prefs.autoUpdateLists.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    // Counting is done off the main thread: after an update the list has to be read again.
    val stats by produceState<String?>(null, apps, listUpdate) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val db = vm.database
                "${db.size} packages: ${db.countFrom(DebloatDatabase.SOURCE_OWN)} rated by Debloater, " +
                    "${db.countFrom(DebloatDatabase.SOURCE_UAD)} by UAD-NG, " +
                    "${db.countFrom(DebloatDatabase.SOURCE_ADL)} by the Android Debloat List, " +
                    "${db.countFrom(DebloatDatabase.SOURCE_SAR)} corrected by System App Remover."
            }.getOrNull()
        }
    }
    val dateFmt = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }

    Scaffold(topBar = { BackTopBar("Settings", onBack = { vm.back() }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingRow(
                "Expert mode",
                "Unlocks packages marked Unsafe and shows system apps that are not in the debloat list. Protected core packages always stay locked.",
                expert,
            ) { if (it) askExpert = true else vm.prefs.setExpertMode(false) }
            SettingRow(
                "Delete app data when removing",
                "Pre-selects \"Delete app data\" in the remove dialog. Frees space, but the data is gone for good and restored apps start fresh. " +
                    "Off (recommended): data is kept, so apps can be restored with all their data.",
                !keepData,
            ) { vm.prefs.setKeepData(!it) }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Backups", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Backups cannot be turned off. Every disable or remove creates a restore point and a verified APK copy first. " +
                            "App data cannot be copied without root, so choose \"Keep app data\" when you might want an app back with its data.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Debloat lists", style = MaterialTheme.typography.titleMedium)
                    Text(stats ?: "Loading…", style = MaterialTheme.typography.bodySmall)
                    Text(
                        vm.listUpdater.lastUpdated()?.let { "Using lists downloaded on ${dateFmt.format(java.util.Date(it))}." }
                            ?: "Using the lists built into this version of the app.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    vm.listUpdater.lastChecked()?.let {
                        Text("Last checked: ${dateFmt.format(java.util.Date(it))}", style = MaterialTheme.typography.labelSmall)
                    }
                    when (val u = listUpdate) {
                        is ListUpdateState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(u.step, style = MaterialTheme.typography.bodySmall)
                        }
                        is ListUpdateState.Done -> Text(
                            (if (u.report.anyUpdated) "Lists updated.\n" else "No newer lists.\n") + u.report.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (u.report.anyUpdated) OkColor else MaterialTheme.colorScheme.onSurface,
                        )
                        is ListUpdateState.Failed -> Text(
                            "Update failed, nothing was changed: ${u.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorColor,
                        )
                        ListUpdateState.Idle -> {}
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { askUpdate = true }, enabled = listUpdate !is ListUpdateState.Running) { Text("Update lists") }
                        if (vm.listUpdater.usesDownloadedLists) {
                            OutlinedButton(onClick = vm::resetLists, enabled = listUpdate !is ListUpdateState.Running) { Text("Use built-in") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Check weekly", style = MaterialTheme.typography.bodyMedium)
                            Text("Download newer lists automatically when the app opens, at most once a week.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = autoUpdate, onCheckedChange = vm.prefs::setAutoUpdateLists)
                    }
                }
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("About", style = MaterialTheme.typography.titleMedium)
                    Text("Debloater ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                    Text("When lists disagree, the most cautious rating is used.", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "License: GPL-3.0. Includes the Universal Android Debloater Next Generation list (GPL-3.0, " +
                            "github.com/Universal-Debloater-Alliance) and overrides from System App Remover by " +
                            "santhosh-D-subramani (GPL-3.0), and the Android Debloat List by Muntashir Al-Islam " +
                            "(AGPL-3.0, adl.muntashir.dev). See NOTICE.md in the source code.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Supports Android 11 to 17 through Shizuku (ADB or root). Commands used: pm disable-user, pm enable, pm uninstall -k --user, cmd package install-existing, pm install-create/write/commit.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (askUpdate) {
        AlertDialog(
            onDismissRequest = { askUpdate = false },
            title = { Text("Update debloat lists?") },
            text = {
                Text(
                    "Debloater will use the internet to download about 3 MB of list files from GitHub:\n" +
                        "• UAD-NG (Universal-Debloater-Alliance)\n" +
                        "• Android Debloat List (MuntashirAkon)\n" +
                        "• System App Remover overrides (santhosh-D-subramani)\n" +
                        "• Debloater's own list (only if its repository is public)\n\n" +
                        "Nothing is uploaded. The new lists are checked before they are used, and you can go back to the built-in lists at any time. " +
                        "Your phone is not changed; only the ratings shown in the app.",
                )
            },
            confirmButton = { TextButton(onClick = { askUpdate = false; vm.updateLists() }) { Text("Download") } },
            dismissButton = { TextButton(onClick = { askUpdate = false }) { Text("Cancel") } },
        )
    }

    if (askExpert) {
        AlertDialog(
            onDismissRequest = { askExpert = false },
            title = { Text("Enable expert mode?") },
            text = { Text("Unsafe packages can break calls, Wi-Fi, notifications or cause a boot loop. Always run a test first and prefer Disable over Remove. If the phone fails to boot, only a factory reset may fix it.") },
            confirmButton = { TextButton(onClick = { vm.prefs.setExpertMode(true); askExpert = false }) { Text("Enable", color = ErrorColor) } },
            dismissButton = { TextButton(onClick = { askExpert = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
