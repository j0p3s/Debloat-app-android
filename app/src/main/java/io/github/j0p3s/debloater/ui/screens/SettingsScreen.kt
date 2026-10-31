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
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.BackTopBar
import io.github.j0p3s.debloater.ui.theme.ErrorColor

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val expert by vm.prefs.expertMode.collectAsStateWithLifecycle()
    val keepData by vm.prefs.keepData.collectAsStateWithLifecycle()
    var askExpert by remember { mutableStateOf(false) }

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
                "Keep data when removing user apps",
                "Default for the checkbox in the remove dialog. With data kept, an app can be restored instantly with its data. System apps always keep their data.",
                keepData,
            ) { vm.prefs.setKeepData(it) }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Backups", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Backups cannot be turned off. Every disable or remove creates a restore point and a verified APK copy first. " +
                            "App data cannot be copied without root, so use \"keep data\" when you might want an app back.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("About", style = MaterialTheme.typography.titleMedium)
                    Text("Debloater ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                    Text("Debloat list: ${vm.database.size} packages", style = MaterialTheme.typography.bodySmall)
                    Text("Supports Android 11 to 17 through Shizuku (ADB or root). Commands used: pm disable-user, pm enable, pm uninstall -k --user, cmd package install-existing, pm install-create/write/commit.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
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
