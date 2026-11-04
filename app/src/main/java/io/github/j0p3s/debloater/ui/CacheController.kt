package io.github.j0p3s.debloater.ui

import android.app.Application
import android.app.usage.StorageStatsManager
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.*
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.data.*
import io.github.j0p3s.debloater.shizuku.ShizukuManager
import io.github.j0p3s.debloater.shizuku.ShizukuStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owned by MainViewModel so selections, scan results and work survive rotation. */
class CacheController(
    private val app: Application,
    private val repo: PackageRepository,
    private val scope: CoroutineScope,
    private val canRun: () -> Boolean,
    private val afterClear: () -> Unit,
) {
    var groups by mutableStateOf(emptyList<CacheGroup>()); private set
    var selected by mutableStateOf(emptySet<String>()); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf(0 to 0); private set
    var scannedAt by mutableStateOf<Long?>(null); private set
    var sort by mutableStateOf(CacheSort.LARGEST)
    var query by mutableStateOf("")
    var nonzeroOnly by mutableStateOf(false)
    var supported by mutableStateOf<Boolean?>(null); private set
    var pending by mutableStateOf<List<CacheGroup>?>(null)
    var notice by mutableStateOf<UiMessage?>(null); private set
    val log = mutableStateListOf<String>()

    fun visible(category: CacheScope): List<CacheGroup> = CachePolicy.sorted(CachePolicy.inScope(groups, category).filter { group ->
        (!nonzeroOnly || (group.bytes ?: 0) > 0) &&
            (group.label.contains(query, true) || group.packages.any { it.contains(query, true) })
    }, sort)
    fun toggle(key: String) { if(!busy) selected = if(key in selected) selected - key else selected + key }
    fun selectShown(category: CacheScope) { if(!busy) selected = selected + visible(category).map { it.key } }
    fun deselect(category: CacheScope) { if(!busy) selected = selected - CachePolicy.inScope(groups, category).map { it.key }.toSet() }
    fun requestClear(category: CacheScope) {
        if(!busy && canRun()) pending = CachePolicy.selectedInScope(groups, selected, category).takeIf { it.isNotEmpty() }
    }

    private suspend fun readGroups() {
        check(repo.hasUsageAccess()) { app.getString(R.string.cache_usage_help) }
        val pm = app.packageManager
        val stats = app.getSystemService(StorageStatsManager::class.java)
        val applications = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0).filter { it.flags and ApplicationInfo.FLAG_INSTALLED != 0 }
                .groupBy { it.uid to it.storageUuid }.values.toList()
        }
        val result = mutableListOf<CacheGroup>()
        for((index, members) in applications.withIndex()) {
            progress = index + 1 to applications.size
            result += withContext(Dispatchers.IO) {
                val first = members.first()
                val packages = members.map { it.packageName }.sorted()
                val label = members.map { runCatching { it.loadLabel(pm).toString() }.getOrDefault(it.packageName) }.sorted().joinToString(" / ")
                val system = members.any { it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
                try {
                    // Query once for each UID and volume; shared UID caches must not inflate totals.
                    val bytes = stats.queryStatsForUid(first.storageUuid, first.uid).cacheBytes.coerceAtLeast(0)
                    CacheGroup(first.uid, first.storageUuid, packages, label, system, bytes)
                } catch(e: Exception) {
                    if(e is CancellationException) throw e
                    CacheGroup(first.uid, first.storageUuid, packages, label, system, null, e.message ?: e.javaClass.simpleName)
                }
            }
        }
        groups = result
        selected = selected.intersect(result.map { it.key }.toSet())
        scannedAt = System.currentTimeMillis()
    }

    fun scan() {
        if(busy || !canRun()) return
        busy = true
        notice = null
        scope.launch {
            try {
                supported = if(ShizukuManager.status.value == ShizukuStatus.READY)
                    CachePolicy.supportsCacheOnly(ShizukuManager.exec("pm help")) else null
                readGroups()
                notice = UiMessage(R.string.cache_scan_done, listOf(groups.count { it.bytes == null }))
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                notice = UiMessage(R.string.cache_error, listOf(e.message.orEmpty()))
            } finally { busy = false; progress = 0 to 0 }
        }
    }

    fun confirmClear() {
        val targets = pending ?: return
        pending = null
        if(busy || !canRun() || ShizukuManager.status.value != ShizukuStatus.READY) return
        busy = true
        notice = null
        scope.launch {
            var changed = false
            try {
                // No fallback to plain pm clear, global trim-caches or raw directory deletion.
                supported = CachePolicy.supportsCacheOnly(ShizukuManager.exec("pm help"))
                if(supported != true) {
                    notice = UiMessage(R.string.cache_unsupported)
                    return@launch
                }
                var successes = 0
                var failures = 0
                val packages = targets.flatMap { it.packages }.distinct()
                for((index, pkg) in packages.withIndex()) {
                    progress = index + 1 to packages.size
                    val command = CachePolicy.clearCommand(repo.userId, pkg)
                    val result = ShizukuManager.exec(command)
                    val success = CachePolicy.succeeded(result)
                    if(success) successes++ else failures++
                    changed = changed || success
                    log += "${if(success) "OK" else "FAIL"}  $command\n${result.output}"
                }
                selected = selected - targets.map { it.key }.toSet()
                // Never leave pre-clean measurements on screen as if they were current.
                groups = emptyList()
                scannedAt = null
                try {
                    readGroups()
                    notice = UiMessage(R.string.cache_clear_done, listOf(successes, failures))
                } catch(e: Exception) {
                    if(e is CancellationException) throw e
                    notice = UiMessage(R.string.cache_rescan_failed, listOf(successes, failures, e.message.orEmpty()))
                }
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                groups = emptyList()
                scannedAt = null
                notice = UiMessage(R.string.cache_error, listOf(e.message.orEmpty()))
            } finally {
                busy = false
                progress = 0 to 0
                if(changed) afterClear()
            }
        }
    }
}
