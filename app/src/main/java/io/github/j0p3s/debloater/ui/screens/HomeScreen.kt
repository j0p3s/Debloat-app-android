package io.github.j0p3s.debloater.ui.screens

import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.data.AppState
import io.github.j0p3s.debloater.data.SafetyLevel
import io.github.j0p3s.debloater.shizuku.ShizukuManager
import io.github.j0p3s.debloater.shizuku.ShizukuStatus
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.Screen
import io.github.j0p3s.debloater.ops.OperationEngine
import io.github.j0p3s.debloater.ui.components.StorageBar
import io.github.j0p3s.debloater.ui.theme.ErrorColor
import io.github.j0p3s.debloater.ui.theme.WarnColor
import androidx.compose.material3.TextButton
import io.github.j0p3s.debloater.ui.theme.OkColor
import io.github.j0p3s.debloater.ui.theme.levelColor

private val ANDROID_NAMES = mapOf(30 to "11", 31 to "12", 32 to "12L", 33 to "13", 34 to "14", 35 to "15", 36 to "16", 37 to "17")

@OptIn(ExperimentalMaterial3Api::class)
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
                    IconButton(onClick = { vm.refreshShizuku(); vm.refreshApps() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = { vm.navigate(Screen.SETTINGS) }) { Icon(Icons.Default.Settings, "Settings") }
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
                        Text("Shizuku: " + statusText(status), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(statusHelp(status, uid), style = MaterialTheme.typography.bodyMedium)
                    when (status) {
                        ShizukuStatus.PERMISSION_REQUIRED, ShizukuStatus.PERMISSION_DENIED ->
                            Button(onClick = { ShizukuManager.requestPermission() }) { Text("Grant permission") }
                        ShizukuStatus.NOT_RUNNING, ShizukuStatus.OUTDATED -> Button(onClick = {
                            ctx.packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)?.let { ctx.startActivity(it) }
                        }) { Text("Open Shizuku") }
                        ShizukuStatus.NOT_INSTALLED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { open(ctx, "market://details?id=${ShizukuManager.SHIZUKU_PACKAGE}", "https://play.google.com/store/apps/details?id=${ShizukuManager.SHIZUKU_PACKAGE}") }) { Text("Play Store") }
                            OutlinedButton(onClick = { open(ctx, "https://github.com/RikkaApps/Shizuku/releases", null) }) { Text("GitHub") }
                        }
                        ShizukuStatus.READY -> {}
                    }
                }
            }

            // ---- Device
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Device", style = MaterialTheme.typography.titleMedium)
                    val sdk = Build.VERSION.SDK_INT
                    Text("${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
                    Text("Android ${ANDROID_NAMES[sdk] ?: Build.VERSION.RELEASE} (API $sdk) · user ${vm.repo.userId}")
                    val dv = vm.repo.deviceVendor
                    Text(
                        if (dv != null) "Vendor: ${dv.name} · ${apps.count { it.vendor == dv.name && it.state == AppState.ENABLED }} active vendor apps"
                        else "Vendor: not recognised (${Build.BRAND}); Google, AOSP and partner apps still apply",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val supported = sdk in 30..37
                    Text(
                        if (supported) "Supported (Android 11 to 17)" else "Newer than Android 17: untested, test runs are strongly advised",
                        color = if (supported) OkColor else ErrorColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // ---- Storage
            storage?.let { st ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Storage", style = MaterialTheme.typography.titleMedium)
                        StorageBar(st)
                        val freed = sessions.sumOf { it.freedBytes }
                        Row {
                            Text("Space freed by Debloater", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(OperationEngine.fmtSize(freed), color = OkColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            Text("Used by backups", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(OperationEngine.fmtSize(backupsBytes), style = MaterialTheme.typography.bodySmall)
                        }
                        if (apps.isNotEmpty()) {
                            val userApps = apps.filter { !it.isSystem && it.state != AppState.UNINSTALLED }
                            Row {
                                Text("User apps (${userApps.size})", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Text(OperationEngine.fmtSize(userApps.sumOf { it.totalBytes }), style = MaterialTheme.typography.bodySmall)
                            }
                            val sys = apps.filter { it.isSystem && it.state != AppState.UNINSTALLED }
                            Row {
                                Text("System apps (${sys.size})", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Text(OperationEngine.fmtSize(sys.sumOf { it.totalBytes }), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (!usageAccess) {
                            Text(
                                "App data sizes are unknown: only APK sizes are shown. " +
                                    if (status == ShizukuStatus.READY) "Tap to grant Usage access through Shizuku." else "Start Shizuku, or grant Usage access in Settings.",
                                style = MaterialTheme.typography.bodySmall, color = WarnColor,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (status == ShizukuStatus.READY) OutlinedButton(onClick = vm::grantUsageAccessWithShizuku) { Text("Grant with Shizuku") }
                                TextButton(onClick = {
                                    runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                                }) { Text("Open settings") }
                            }
                        }
                    }
                }
            }

            // ---- Stats
            if (apps.isNotEmpty()) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("On this device", style = MaterialTheme.typography.titleMedium)
                        val dark = isSystemInDarkTheme()
                        listOf(SafetyLevel.RECOMMENDED, SafetyLevel.SAFE, SafetyLevel.EXTREME).forEach { level ->
                            val ofLevel = apps.filter { it.level == level }
                            val activeApps = ofLevel.filter { it.state == AppState.ENABLED }
                            Row {
                                Text(level.title, color = levelColor(level, dark), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("${activeApps.size} active (${OperationEngine.fmtSize(activeApps.sumOf { it.totalBytes })}) · ${ofLevel.size - activeApps.size} done")
                            }
                        }
                        Text(
                            "${apps.size} packages · ${apps.count { !it.isSystem }} user apps · ${apps.count { it.state == AppState.DISABLED }} disabled · ${apps.count { it.state == AppState.UNINSTALLED }} removed",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            NavTile(Icons.Default.CleaningServices, "Debloat", "Recommended, safe and extreme debloat lists with test runs") { vm.navigate(Screen.DEBLOAT) }
            NavTile(Icons.Default.FactCheck, "Android Debloat List check", "See which apps from adl.muntashir.dev are installed on this phone") { vm.navigate(Screen.ADL_CHECK) }
            NavTile(Icons.Default.Apps, "App manager", "Disable, remove or restore any user or system app") { vm.navigate(Screen.APP_MANAGER) }
            NavTile(Icons.Default.Backup, "Backups & restore", "${sessions.size} backup(s). Restore points and APK copies are made before every change") { vm.navigate(Screen.BACKUPS) }
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

private fun statusText(s: ShizukuStatus) = when (s) {
    ShizukuStatus.NOT_INSTALLED -> "not installed"
    ShizukuStatus.NOT_RUNNING -> "not running"
    ShizukuStatus.PERMISSION_REQUIRED -> "permission needed"
    ShizukuStatus.PERMISSION_DENIED -> "permission denied"
    ShizukuStatus.OUTDATED -> "update required"
    ShizukuStatus.READY -> "ready"
}

private fun statusHelp(s: ShizukuStatus, uid: Int) = when (s) {
    ShizukuStatus.NOT_INSTALLED -> "Install Shizuku, then start it with Wireless debugging (Android 11+) or with root. No PC needed."
    ShizukuStatus.NOT_RUNNING -> "Open Shizuku and tap Start (Wireless debugging or root). It has to be started again after every reboot unless you have root."
    ShizukuStatus.PERMISSION_REQUIRED -> "Allow Debloater to use Shizuku."
    ShizukuStatus.PERMISSION_DENIED -> "Permission was denied. Tap to ask again, or allow Debloater in the Shizuku app."
    ShizukuStatus.OUTDATED -> "Your Shizuku version is too old. Please update Shizuku."
    ShizukuStatus.READY -> "Running as ${if (uid == 0) "root" else if (uid == 2000) "ADB shell" else "uid $uid"}. You can browse apps without Shizuku, but every change goes through it."
}

private fun open(ctx: android.content.Context, uri: String, fallback: String?) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        fallback?.let { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}
