package io.github.j0p3s.debloater.ui.screens

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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.data.AppState
import io.github.j0p3s.debloater.data.SafetyLevel
import io.github.j0p3s.debloater.shizuku.ShizukuManager
import io.github.j0p3s.debloater.shizuku.ShizukuStatus
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.Screen
import io.github.j0p3s.debloater.ui.components.StorageBar
import io.github.j0p3s.debloater.ui.components.formatSize
import io.github.j0p3s.debloater.ui.theme.ErrorColor
import io.github.j0p3s.debloater.ui.theme.OkColor
import io.github.j0p3s.debloater.ui.theme.WarnColor
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
            // ---- Shizuku
            val ready = status == ShizukuStatus.READY
            Card(colors = CardDefaults.cardColors(containerColor = if (ready) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.Error, null, tint = if (ready) OkColor else ErrorColor)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.shizuku_status, statusText(status)), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(statusHelp(status, uid), style = MaterialTheme.typography.bodyMedium)
                    when (status) {
                        ShizukuStatus.PERMISSION_REQUIRED, ShizukuStatus.PERMISSION_DENIED ->
                            Button(onClick = { ShizukuManager.requestPermission() }) { Text(stringResource(R.string.permission)) }
                        ShizukuStatus.NOT_RUNNING, ShizukuStatus.OUTDATED -> Button(onClick = {
                            ctx.packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)?.let { ctx.startActivity(it) }
                        }) { Text(stringResource(R.string.open_shizuku)) }
                        ShizukuStatus.NOT_INSTALLED -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { open(ctx, "market://details?id=${ShizukuManager.SHIZUKU_PACKAGE}", "https://play.google.com/store/apps/details?id=${ShizukuManager.SHIZUKU_PACKAGE}") }) { Text(stringResource(R.string.play_store)) }
                            OutlinedButton(onClick = { open(ctx, "https://github.com/RikkaApps/Shizuku/releases", null) }) { Text(stringResource(R.string.github)) }
                        }
                        ShizukuStatus.READY -> {}
                    }
                }
            }

            NavTile(Icons.Default.CleaningServices, stringResource(R.string.debloat), stringResource(R.string.nav_debloat_help)) { vm.navigate(Screen.DEBLOAT) }
            GettingStartedCard()
            NavTile(Icons.Default.Apps, stringResource(R.string.app_manager), stringResource(R.string.nav_manager_help)) { vm.navigate(Screen.APP_MANAGER) }
            NavTile(Icons.Default.Backup, stringResource(R.string.backups_restore), stringResource(R.string.nav_backup_help, sessions.size)) { vm.navigate(Screen.BACKUPS) }
            NavTile(Icons.Default.FactCheck, stringResource(R.string.adl_check), stringResource(R.string.nav_adl_help)) { vm.navigate(Screen.ADL_CHECK) }

            // ---- Device
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.device), style = MaterialTheme.typography.titleMedium)
                    val sdk = Build.VERSION.SDK_INT
                    Text("${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
                    Text(stringResource(R.string.android_user, ANDROID_NAMES[sdk] ?: Build.VERSION.RELEASE, sdk, vm.repo.userId))
                    val dv = vm.repo.deviceVendor
                    Text(
                        if (dv != null) stringResource(R.string.vendor_active, dv.name, apps.count { it.vendor == dv.name && it.state == AppState.ENABLED })
                        else stringResource(R.string.vendor_unknown, Build.BRAND),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val supported = sdk in 30..37
                    Text(
                        if (supported) stringResource(R.string.android_supported) else stringResource(R.string.android_untested),
                        color = if (supported) OkColor else ErrorColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // ---- Storage
            storage?.let { st ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.storage), style = MaterialTheme.typography.titleMedium)
                        StorageBar(st)
                        val freed = sessions.sumOf { it.freedBytes }
                        Row {
                            Text(stringResource(R.string.space_freed), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(formatSize(freed), color = OkColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            Text(stringResource(R.string.backups_used), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(formatSize(backupsBytes), style = MaterialTheme.typography.bodySmall)
                        }
                        if (apps.isNotEmpty()) {
                            val userApps = apps.filter { !it.isSystem && it.state != AppState.UNINSTALLED }
                            Row {
                                Text(stringResource(R.string.user_count, userApps.size), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Text(formatSize(userApps.sumOf { it.totalBytes }), style = MaterialTheme.typography.bodySmall)
                            }
                            val sys = apps.filter { it.isSystem && it.state != AppState.UNINSTALLED }
                            Row {
                                Text(stringResource(R.string.system_count, sys.size), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Text(formatSize(sys.sumOf { it.totalBytes }), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (!usageAccess) {
                            Text(
                                stringResource(R.string.usage_help),
                                style = MaterialTheme.typography.bodySmall, color = WarnColor,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (status == ShizukuStatus.READY) OutlinedButton(onClick = vm::grantUsageAccessWithShizuku) { Text(stringResource(R.string.grant_shizuku)) }
                                TextButton(onClick = {
                                    runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                                }) { Text(stringResource(R.string.open_settings)) }
                            }
                        }
                    }
                }
            }

            // ---- Stats
            if (apps.isNotEmpty()) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.on_device), style = MaterialTheme.typography.titleMedium)
                        val dark = isSystemInDarkTheme()
                        listOf(SafetyLevel.RECOMMENDED, SafetyLevel.SAFE, SafetyLevel.EXTREME).forEach { level ->
                            val ofLevel = apps.filter { it.level == level }
                            val activeApps = ofLevel.filter { it.state == AppState.ENABLED }
                            Row {
                                Text(stringResource(level.titleRes), color = levelColor(level, dark), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.tier_stats, activeApps.size, formatSize(activeApps.sumOf { it.totalBytes }), ofLevel.size - activeApps.size))
                            }
                        }
                        Text(
                            stringResource(R.string.device_stats, apps.size, apps.count { !it.isSystem }, apps.count { it.state == AppState.DISABLED }, apps.count { it.state == AppState.UNINSTALLED }),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun statusText(s: ShizukuStatus) = when (s) {
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

private fun open(ctx: android.content.Context, uri: String, fallback: String?) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        fallback?.let { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}

@Composable
private fun GettingStartedCard() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.start_title), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.hide_guide else R.string.show_guide))
            }
            if (expanded) {
                Text(stringResource(R.string.start_steps), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.test_limits), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
