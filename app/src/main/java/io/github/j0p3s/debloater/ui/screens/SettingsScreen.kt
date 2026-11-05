package io.github.j0p3s.debloater.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.BuildConfig
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.data.DebloatDatabase
import io.github.j0p3s.debloater.data.ListUpdater
import io.github.j0p3s.debloater.ui.ListUpdateState
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.BackTopBar
import io.github.j0p3s.debloater.ui.theme.ErrorColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val uriHandler = LocalUriHandler.current
    val expert by vm.prefs.expertMode.collectAsStateWithLifecycle()
    val keepData by vm.prefs.keepData.collectAsStateWithLifecycle()
    var askExpert by remember { mutableStateOf(false) }
    var askUpdate by remember { mutableStateOf(false) }
    val listUpdate by vm.listUpdate.collectAsStateWithLifecycle()
    val autoUpdate by vm.prefs.autoUpdateLists.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    // Counting is done off the main thread: after an update the list has to be read again.
    val stats by produceState<IntArray?>(null, apps, listUpdate) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val db = vm.database
                intArrayOf(db.size, db.countFrom(DebloatDatabase.SOURCE_OWN), db.countFrom(DebloatDatabase.SOURCE_UAD), db.countFrom(DebloatDatabase.SOURCE_ADL), db.countFrom(DebloatDatabase.SOURCE_SAR))
            }.getOrNull()
        }
    }
    val dateFmt = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings), onBack = { vm.back() }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            io.github.j0p3s.debloater.ui.components.LanguageSetting()
            io.github.j0p3s.debloater.ui.components.AppUpdateSetting()
            OutlinedButton(onClick = { vm.navigate(io.github.j0p3s.debloater.ui.Screen.PERMISSIONS) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.permissions_title))
            }
            SettingRow(
                stringResource(R.string.expert_mode),
                stringResource(R.string.expert_help),
                expert,
            ) { if (it) askExpert = true else vm.prefs.setExpertMode(false) }
            SettingRow(
                stringResource(R.string.delete_default),
                stringResource(R.string.delete_default_help),
                !keepData,
            ) { vm.prefs.setKeepData(!it) }
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.backups), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.backup_settings_help),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.debloat_lists), style = MaterialTheme.typography.titleMedium)
                    io.github.j0p3s.debloater.ui.components.ExpandableSection(stringResource(R.string.list_sources)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { uriHandler.openUri(ListUpdater.UAD_URL) }) { Text("UAD-NG") }
                        TextButton(onClick = { uriHandler.openUri("https://github.com/MuntashirAkon/android-debloat-list") }) { Text("Android Debloat List") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DebloatDatabase.ADL_FILES.forEach { name ->
                            TextButton(onClick = { uriHandler.openUri("${ListUpdater.ADL_BASE}/$name.json") }) { Text("$name.json") }
                        }
                    }
                    }
                    Text(stats?.let { stringResource(R.string.list_stats, *it.toTypedArray()) } ?: stringResource(R.string.loading), style = MaterialTheme.typography.bodySmall)
                    Text(
                        vm.listUpdater.lastUpdated()?.let { stringResource(R.string.lists_downloaded, dateFmt.format(java.util.Date(it))) }
                            ?: stringResource(R.string.lists_builtin),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    vm.listUpdater.lastChecked()?.let {
                        Text(stringResource(R.string.last_checked, dateFmt.format(java.util.Date(it))), style = MaterialTheme.typography.labelSmall)
                    }
                    when (val u = listUpdate) {
                        is ListUpdateState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.updating_lists), style = MaterialTheme.typography.bodySmall)
                        }
                        is ListUpdateState.Done -> {
                            Text(
                                if (u.report.anyUpdated) stringResource(R.string.lists_updated) else stringResource(R.string.lists_current),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            u.report.results.forEach { result ->
                                val statusRes = when (result.status) {
                                    ListUpdater.SourceResult.Status.UPDATED -> R.string.update_status_updated
                                    ListUpdater.SourceResult.Status.UNCHANGED -> R.string.update_status_current
                                    ListUpdater.SourceResult.Status.SKIPPED -> R.string.update_status_skipped
                                    ListUpdater.SourceResult.Status.FAILED -> R.string.update_status_failed
                                }
                                Text(result.name + ": " + stringResource(statusRes), style = MaterialTheme.typography.bodySmall)
                                if (result.detail.isNotBlank()) Text(result.detail, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        is ListUpdateState.Failed -> Text(
                            stringResource(R.string.update_failed, u.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorColor,
                        )
                        ListUpdateState.Idle -> {}
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { askUpdate = true }, enabled = listUpdate !is ListUpdateState.Running) { Text(stringResource(R.string.update_lists)) }
                        if (vm.listUpdater.usesDownloadedLists) {
                            OutlinedButton(onClick = vm::resetLists, enabled = listUpdate !is ListUpdateState.Running) { Text(stringResource(R.string.use_builtin)) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(stringResource(R.string.check_weekly), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.weekly_help), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = autoUpdate, onCheckedChange = vm.prefs::setAutoUpdateLists)
                    }
                }
            }
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium)
                    Text("Debloater ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.cautious_rating), style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(R.string.license_notice),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(stringResource(R.string.android_commands), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (askUpdate) {
        AlertDialog(
            onDismissRequest = { askUpdate = false },
            title = { Text(stringResource(R.string.update_question)) },
            text = {
                Text(
                    stringResource(R.string.update_help),
                )
            },
            confirmButton = { TextButton(onClick = { askUpdate = false; vm.updateLists() }) { Text(stringResource(R.string.download)) } },
            dismissButton = { TextButton(onClick = { askUpdate = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (askExpert) {
        AlertDialog(
            onDismissRequest = { askExpert = false },
            title = { Text(stringResource(R.string.enable_expert)) },
            text = { Text(stringResource(R.string.expert_dialog_help)) },
            confirmButton = { TextButton(onClick = { vm.prefs.setExpertMode(true); askExpert = false }) { Text(stringResource(R.string.enable), color = ErrorColor) } },
            dismissButton = { TextButton(onClick = { askExpert = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
