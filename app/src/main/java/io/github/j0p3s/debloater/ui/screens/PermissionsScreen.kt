package io.github.j0p3s.debloater.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.cache.SettingsCacheService
import io.github.j0p3s.debloater.shizuku.ShizukuManager
import io.github.j0p3s.debloater.shizuku.ShizukuStatus
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.BackTopBar

@Composable
fun PermissionsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var revision by remember { mutableIntStateOf(0) }
    var disclosure by remember { mutableStateOf(false) }
    var autoDisclosure by remember { mutableStateOf(false) }
    val shizuku by vm.shizukuStatus.collectAsStateWithLifecycle()
    val accessibility by SettingsCacheService.available.collectAsStateWithLifecycle()
    fun refresh() { ShizukuManager.refresh(); revision++ }
    fun open(intent: Intent) { runCatching { context.startActivity(intent) } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val usage = remember(revision, vm.accessSetupRevision) { vm.repo.hasUsageAccess() }
    fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    Scaffold(topBar = { BackTopBar(stringResource(R.string.permissions_title), onBack = { vm.back() }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = ::refresh) { Text(stringResource(R.string.permissions_validate)) }
            Button(onClick = { autoDisclosure = true }, enabled = shizuku == ShizukuStatus.READY && !vm.configuringAccess && !vm.cache.busy && !vm.leftovers.busy && !vm.engine.isRunning) {
                Text(stringResource(R.string.permissions_auto))
            }
            if(vm.configuringAccess) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(vm.accessSetupLog.isNotEmpty()) {
                Text(stringResource(R.string.permissions_auto_result))
                vm.accessSetupLog.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            PermissionCard("Shizuku", shizuku == ShizukuStatus.READY, stringResource(R.string.leftovers_needs_shizuku)) {
                Text(shizuku.name, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { ShizukuManager.requestPermission(); refresh() }, enabled = shizuku == ShizukuStatus.PERMISSION_REQUIRED || shizuku == ShizukuStatus.PERMISSION_DENIED) { Text(stringResource(R.string.grant_shizuku)) }
                TextButton(onClick = { open(context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))) }) { Text("Shizuku") }
            }
            PermissionCard(stringResource(R.string.permissions_usage), usage, stringResource(R.string.cache_usage_help)) {
                TextButton(onClick = { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text(stringResource(R.string.open_settings)) }
                if(shizuku == ShizukuStatus.READY) TextButton(onClick = vm::grantUsageAccessWithShizuku) { Text(stringResource(R.string.grant_shizuku)) }
            }
            PermissionCard(stringResource(R.string.cache_settings_method), accessibility, stringResource(R.string.cache_settings_enable_help)) {
                TextButton(onClick = { disclosure = true }) { Text(stringResource(R.string.open_settings)) }
            }
            PermissionCard("android.permission.QUERY_ALL_PACKAGES", granted("android.permission.QUERY_ALL_PACKAGES"), stringResource(R.string.permissions_automatic)) {}
            PermissionCard("android.permission.INTERNET", granted("android.permission.INTERNET"), stringResource(R.string.permissions_automatic)) {}
        }
    }
    if(disclosure) AlertDialog(onDismissRequest = { disclosure = false }, title = { Text(stringResource(R.string.cache_settings_method)) },
        text = { Text(stringResource(R.string.cache_settings_disclosure), Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { disclosure = false; open(SettingsCacheService.settingsIntent()) }) { Text(stringResource(R.string.open_settings)) } },
        dismissButton = { TextButton(onClick = { disclosure = false }) { Text(stringResource(R.string.cancel)) } })
    if(autoDisclosure) AlertDialog(onDismissRequest = { autoDisclosure = false },
        title = { Text(stringResource(R.string.permissions_auto)) },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.permissions_auto_help))
            Text(stringResource(R.string.cache_settings_disclosure))
        } },
        confirmButton = { TextButton(onClick = { autoDisclosure = false; vm.configureAccessWithShizuku() }, enabled = !vm.configuringAccess) { Text(stringResource(R.string.permissions_auto)) } },
        dismissButton = { TextButton(onClick = { autoDisclosure = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun PermissionCard(title: String, available: Boolean, help: String, actions: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if(available) R.string.permissions_ready else R.string.permissions_missing))
            Text(help, style = MaterialTheme.typography.bodySmall)
            actions()
        }
    }
}
