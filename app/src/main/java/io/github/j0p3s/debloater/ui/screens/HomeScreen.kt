package io.github.j0p3s.debloater.ui.screens

import androidx.compose.material.icons.automirrored.filled.ArrowForward
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.data.AppState
import io.github.j0p3s.debloater.data.SafetyLevel
import io.github.j0p3s.debloater.shizuku.ShizukuStatus
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.Screen
import io.github.j0p3s.debloater.ui.components.StorageBar
import io.github.j0p3s.debloater.ui.components.formatSize
import io.github.j0p3s.debloater.ui.theme.levelColor

private val ANDROID_NAMES = mapOf(30 to "11", 31 to "12", 32 to "12L", 33 to "13", 34 to "14", 35 to "15", 36 to "16", 37 to "17")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val status by vm.shizukuStatus.collectAsStateWithLifecycle()
    val uid by vm.shizukuUid.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val backupsBytes by vm.backupsBytes.collectAsStateWithLifecycle()
    val usageAccess by vm.usageAccess.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debloater") },
                actions = {
                    IconButton(onClick = { vm.navigate(Screen.HELP) }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.guide_help_title))
                    }
                    IconButton(onClick = { vm.refreshShizuku(); vm.refreshApps() }) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                    IconButton(onClick = { vm.navigate(Screen.SETTINGS) }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val sdk = Build.VERSION.SDK_INT
            val ready = status == ShizukuStatus.READY
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.android_user, ANDROID_NAMES[sdk] ?: Build.VERSION.RELEASE, sdk, vm.repo.userId),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Until Shizuku works, show the numbered setup steps instead of a short status line.
            if (!ready) io.github.j0p3s.debloater.ui.components.ShizukuSetupCard(status, uid, onRefresh = { vm.refreshShizuku() })
            if (ready) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.shizuku_status, shizukuStatusText(status)), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                }
            }

            // Device and storage lead the dashboard, as in the earlier layout.
            storage?.let { st ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.storage), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.storage_free, formatSize(st.freeBytes)),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        StorageBar(st, showFree = false)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric(stringResource(R.string.space_freed), formatSize(sessions.sumOf { it.freedBytes }))
                            Metric(stringResource(R.string.backups_used), formatSize(backupsBytes))
                        }
                        if (!usageAccess) {
                            Text(stringResource(R.string.usage_help), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (ready) TextButton(onClick = vm::grantUsageAccessWithShizuku) { Text(stringResource(R.string.grant_shizuku)) }
                                TextButton(onClick = { runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }) {
                                    Text(stringResource(R.string.open_settings))
                                }
                            }
                        }
                    }
                }
            }

            // One compact group replaces the stack of large, repeated navigation cards.
            Card {
                Column {
                    val candidates = cleanupCandidates(apps)
                    NavTile(
                        Icons.Default.AutoFixHigh, stringResource(R.string.guide_cleanup_title),
                        if (candidates.isEmpty()) stringResource(R.string.guide_cleanup_none)
                        else stringResource(R.string.guide_cleanup_found, candidates.size, formatSize(candidates.sumOf { it.totalBytes })),
                        primary = true,
                    ) { vm.navigate(Screen.CLEANUP) }
                    @Composable fun divider() = androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    divider()
                    NavTile(Icons.Default.CleaningServices, stringResource(R.string.debloat), stringResource(R.string.nav_debloat_help)) { vm.navigate(Screen.DEBLOAT) }
                    divider()
                    NavTile(Icons.Default.Apps, stringResource(R.string.app_manager), stringResource(R.string.nav_manager_help)) { vm.navigate(Screen.APP_MANAGER) }
                    divider()
                    NavTile(Icons.Default.Cached, stringResource(R.string.cache_user), stringResource(R.string.cache_user_help)) { vm.navigate(Screen.USER_CACHE) }
                    divider()
                    NavTile(Icons.Default.Memory, stringResource(R.string.cache_system), stringResource(R.string.cache_system_help)) { vm.navigate(Screen.SYSTEM_CACHE) }
                    divider()
                    NavTile(Icons.Default.FolderDelete, stringResource(R.string.leftovers_title), stringResource(R.string.leftovers_nav_help)) { vm.navigate(Screen.LEFTOVERS) }
                    divider()
                    NavTile(Icons.Default.Backup, stringResource(R.string.backups_restore), stringResource(R.string.nav_backup_help, sessions.size)) { vm.navigate(Screen.BACKUPS) }
                    divider()
                    NavTile(Icons.AutoMirrored.Filled.FactCheck, stringResource(R.string.adl_check), stringResource(R.string.nav_adl_help)) { vm.navigate(Screen.ADL_CHECK) }
                }
            }

            if (apps.isNotEmpty()) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.on_device), style = MaterialTheme.typography.titleSmall)
                        val dark = isSystemInDarkTheme()
                        listOf(SafetyLevel.RECOMMENDED, SafetyLevel.SAFE, SafetyLevel.EXTREME).forEach { level ->
                            val ofLevel = apps.filter { it.level == level }
                            val active = ofLevel.filter { it.state == AppState.ENABLED }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(level.titleRes), style = MaterialTheme.typography.labelLarge,
                                    color = levelColor(level, dark), fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.tier_stats, active.size, formatSize(active.sumOf { it.totalBytes }), ofLevel.size - active.size),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(stringResource(R.string.device_stats, apps.size, apps.count { !it.isSystem }, apps.count { it.state == AppState.DISABLED }, apps.count { it.state == AppState.UNINSTALLED }),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Card {
                io.github.j0p3s.debloater.ui.components.ExpandableSection(stringResource(R.string.device)) {
                    Text(statusHelp(status, uid), style = MaterialTheme.typography.bodySmall)
                    val dv = vm.repo.deviceVendor
                    Text(if (dv != null) stringResource(R.string.vendor_active, dv.name, apps.count { it.vendor == dv.name && it.state == AppState.ENABLED })
                        else stringResource(R.string.vendor_unknown, Build.BRAND), style = MaterialTheme.typography.bodySmall)
                    if (sdk in 30..37) Text(stringResource(R.string.android_supported), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    val userApps = apps.filter { !it.isSystem && it.state != AppState.UNINSTALLED }
                    val systemApps = apps.filter { it.isSystem && it.state != AppState.UNINSTALLED }
                    Text(stringResource(R.string.user_count, userApps.size) + " · " + formatSize(userApps.sumOf { it.totalBytes }), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.system_count, systemApps.size) + " · " + formatSize(systemApps.sumOf { it.totalBytes }), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (sdk !in 30..37) Text(stringResource(R.string.android_untested), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            GettingStartedCard()
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NavTile(icon: ImageVector, title: String, subtitle: String, primary: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Surface(shape = MaterialTheme.shapes.medium,
            color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer) {
            Icon(icon, null, Modifier.padding(10.dp).size(22.dp),
                tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
internal fun shizukuStatusText(s: ShizukuStatus) = when (s) {
    ShizukuStatus.NOT_INSTALLED -> stringResource(R.string.shizuku_missing)
    ShizukuStatus.NOT_RUNNING -> stringResource(R.string.shizuku_stopped)
    ShizukuStatus.PERMISSION_REQUIRED -> stringResource(R.string.shizuku_permission)
    ShizukuStatus.PERMISSION_DENIED -> stringResource(R.string.shizuku_denied)
    ShizukuStatus.OUTDATED -> stringResource(R.string.shizuku_outdated)
    ShizukuStatus.READY -> stringResource(R.string.shizuku_ready)
}

@Composable
private fun statusHelp(s: ShizukuStatus, uid: Int) = when (s) {
    ShizukuStatus.NOT_INSTALLED -> stringResource(R.string.shizuku_install_help)
    ShizukuStatus.NOT_RUNNING -> stringResource(R.string.shizuku_start_help)
    ShizukuStatus.PERMISSION_REQUIRED -> stringResource(R.string.shizuku_allow_help)
    ShizukuStatus.PERMISSION_DENIED -> stringResource(R.string.shizuku_denied_help)
    ShizukuStatus.OUTDATED -> stringResource(R.string.shizuku_update_help)
    ShizukuStatus.READY -> stringResource(R.string.shizuku_ready_help, if (uid == 0) "root" else if (uid == 2000) "ADB shell" else "uid $uid")
}

@Composable
private fun GettingStartedCard() {
    Card {
        io.github.j0p3s.debloater.ui.components.ExpandableSection(stringResource(R.string.show_guide)) {
            Text(stringResource(R.string.start_steps), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.test_limits), style = MaterialTheme.typography.bodySmall)
        }
    }
}
