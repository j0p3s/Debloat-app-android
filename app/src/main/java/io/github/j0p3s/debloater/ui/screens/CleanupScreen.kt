package io.github.j0p3s.debloater.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.data.Action
import io.github.j0p3s.debloater.data.AppInfo
import io.github.j0p3s.debloater.data.sortedBy
import io.github.j0p3s.debloater.shizuku.ShizukuStatus
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.AppRow
import io.github.j0p3s.debloater.ui.components.BackTopBar
import io.github.j0p3s.debloater.ui.theme.WarnColor

/** Recommended-tier apps that are active and can be changed without expert mode: the beginner clean-up. */
fun cleanupCandidates(apps: List<AppInfo>) =
    apps.filter { it.level == io.github.j0p3s.debloater.data.SafetyLevel.RECOMMENDED && it.state == io.github.j0p3s.debloater.data.AppState.ENABLED && isSelectable(it, expert = false) }

/**
 * The beginner path: the Recommended apps on this phone, all preselected, with "Test" and
 * "Turn off" (disable, fully reversible) as the main actions.
 */
@Composable
fun CleanupScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val status by vm.shizukuStatus.collectAsStateWithLifecycle()
    val sort by vm.prefs.sort.collectAsStateWithLifecycle()
    val stored by vm.cleanupSelection.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf<AppInfo?>(null) }

    val candidates = remember(apps, sort) { cleanupCandidates(apps).sortedBy(sort) }
    // First visit: everything is selected.
    LaunchedEffect(candidates.isNotEmpty(), stored == null) {
        if (stored == null && candidates.isNotEmpty()) vm.setCleanupSelection(candidates.map { it.packageName }.toSet())
    }
    val selection = stored.orEmpty()
    val chosen = candidates.filter { it.packageName in selection }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.guide_cleanup_title), onBack = { vm.back() }) },
        bottomBar = {
            if (candidates.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.guide_cleanup_selected, chosen.size, candidates.size, io.github.j0p3s.debloater.ui.components.formatSize(chosen.sumOf { it.totalBytes })),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { vm.request(chosen, Action.DISABLE, dryRun = true) },
                                enabled = chosen.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.test)) }
                            Button(
                                onClick = { vm.request(chosen, Action.DISABLE, dryRun = false) },
                                enabled = chosen.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.guide_cleanup_turn_off)) }
                        }
                        TextButton(
                            onClick = { vm.request(chosen, Action.REMOVE, dryRun = false) },
                            enabled = chosen.isNotEmpty(),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text(stringResource(R.string.guide_cleanup_uninstall_instead)) }
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.guide_cleanup_steps), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.guide_cleanup_intro), style = MaterialTheme.typography.bodyLarge)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(stringResource(R.string.guide_cleanup_tip), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (status != ShizukuStatus.READY) {
                        Text(stringResource(R.string.guide_cleanup_shizuku_needed), color = WarnColor, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (candidates.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { vm.setCleanupSelection(candidates.map { it.packageName }.toSet()) }) { Text(stringResource(R.string.select_all)) }
                            TextButton(onClick = { vm.setCleanupSelection(emptySet()) }) { Text(stringResource(R.string.guide_cleanup_select_none)) }
                        }
                    }
                }
            }
            if (candidates.isEmpty()) {
                item {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.guide_cleanup_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.guide_cleanup_empty_text), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(candidates, key = { it.packageName }) { app ->
                val toggle = {
                    vm.setCleanupSelection(if (app.packageName in selection) selection - app.packageName else selection + app.packageName)
                }
                AppRow(app, app.packageName in selection, selectable = true, onToggle = toggle, onClick = toggle, onInfo = { details = app })
                HorizontalDivider()
            }
        }
    }

    details?.let { app -> AppDetailsDialog(app, isSelectable(app, false), vm) { details = null } }
}
