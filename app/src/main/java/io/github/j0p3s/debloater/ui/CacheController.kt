package io.github.j0p3s.debloater.ui

import android.app.Application
import android.app.usage.StorageStatsManager
import android.content.pm.ApplicationInfo
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.core.content.edit
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.MainActivity
import io.github.j0p3s.debloater.cache.SettingsCachePolicy
import io.github.j0p3s.debloater.cache.SettingsCacheResult
import io.github.j0p3s.debloater.cache.SettingsCacheService
import io.github.j0p3s.debloater.data.*
import io.github.j0p3s.debloater.shizuku.ShizukuManager
import io.github.j0p3s.debloater.shizuku.ShizukuStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext

/** Owned by MainViewModel so selections, scan results and work survive rotation. */
class CacheController(
    private val app: Application,
    private val repo: PackageRepository,
    private val scope: CoroutineScope,
    private val canRun: () -> Boolean,
    private val afterClear: (Set<String>) -> Unit,
) {
    var groups by mutableStateOf(emptyList<CacheGroup>()); private set
    var selected by mutableStateOf(emptySet<String>()); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf(0 to 0); private set
    var partial by mutableStateOf(false); private set
    var scannedAt by mutableStateOf<Long?>(null); private set
    var sort by mutableStateOf(CacheSort.LARGEST)
    var query by mutableStateOf("")
    var nonzeroOnly by mutableStateOf(false)
    var supported by mutableStateOf<Boolean?>(null); private set
    var pending by mutableStateOf<List<CacheGroup>?>(null)
    var method by mutableStateOf(CacheClearMethod.SETTINGS)
    var pendingMethod by mutableStateOf(CacheClearMethod.SETTINGS); private set
    private var activeMethod: CacheClearMethod? = null
    var notice by mutableStateOf<UiMessage?>(null); private set
    /** Apps Shizuku could not clear (and, when Android ignored it, apps not tried), offered for the Settings method. */
    var fallback by mutableStateOf<CacheFallback?>(null)
    private val residuePrefs = app.getSharedPreferences("cache_residue", android.content.Context.MODE_PRIVATE)
    /** Per group key: cache still reported after the last confirmed clear (see [CacheResidue]). */
    var residues by mutableStateOf(residuePrefs.all.mapNotNull { (k, v) -> (v as? String)?.let(CacheResidue::decode)?.let { k to it } }.toMap())
        private set
    fun residueFor(group: CacheGroup): CacheResidue? = residues[group.key]?.takeIf { CachePolicy.residueApplies(it, group.bytes) }

    private suspend fun recordResidues(cleared: List<CacheGroup>) {
        val now = System.currentTimeMillis()
        val updated = residues.toMutableMap()
        for(target in cleared) {
            val after = groups.firstOrNull { it.key == target.key }?.bytes
            val residue = CachePolicy.residueAfterClear(target.bytes, after)
            if(residue == null) { updated.remove(target.key); continue }
            // Shell can read shared storage: measure what is really inside the app's cache folders there.
            val folder = if(ShizukuManager.isReady()) target.packages.fold(0L as Long?) { sum, pkg ->
                val measured = CachePolicy.parseDuKb(ShizukuManager.execCache("du -sk '${CachePolicy.sharedCacheDir(repo.userId, pkg)}'"))
                if(sum == null || measured == null) null else sum + measured
            } else null
            updated[target.key] = CacheResidue(residue, now, folder)
            log += "RESIDUE ${target.packages.joinToString()}: before ${target.bytes} bytes, after $residue bytes; " +
                "shared-storage cache folder ${folder?.let { "$it bytes" } ?: "not measured"}; internal cache folder cannot be read without root. " +
                "Android counts this as cache, but Clear cache did not remove it (or the app refilled it at once)."
        }
        residuePrefs.edit { clear(); updated.forEach { (k, v) -> putString(k, v.encode()) } }
        residues = updated
    }
    val log = io.github.j0p3s.debloater.debug.DebugLog.Mirror("cache")
    private val reader = BoundedCacheReader()
    private var job: Job? = null
    var stopRequested by mutableStateOf(false); private set
    var phase by mutableIntStateOf(R.string.cache_scanner); private set
    var currentApp by mutableStateOf(""); private set

    fun cancel() {
        stopRequested = true
        if (phase != R.string.cache_clear || activeMethod == CacheClearMethod.SETTINGS) job?.cancel()
    }

    fun visible(category: CacheScope): List<CacheGroup> = CachePolicy.sorted(CachePolicy.inScope(groups, category).filter { group ->
        (!nonzeroOnly || (group.bytes ?: 0) > 0) &&
            (group.label.contains(query, true) || group.packages.any { it.contains(query, true) })
    }, sort)
    fun toggle(key: String) { if(!busy) selected = if(key in selected) selected - key else selected + key }
    /** Apps whose reported cache a previous clear could not remove are skipped; they can still be ticked one by one. */
    fun selectShown(category: CacheScope) {
        if(!busy) selected = selected + visible(category).filter { residueFor(it) == null }.map { it.key }
    }
    fun deselect(category: CacheScope) { if(!busy) selected = selected - CachePolicy.inScope(groups, category).map { it.key }.toSet() }
    /** The user chose apps from the Shizuku fallback dialog; the normal Settings confirmation follows. */
    fun useSettingsFor(keys: Set<String>) {
        val offer = fallback ?: return
        fallback = null
        if(busy || !canRun()) return
        val chosen = (offer.failed + offer.untried).filter { it.key in keys }
        if(chosen.isEmpty()) return
        pendingMethod = CacheClearMethod.SETTINGS
        pending = chosen
    }

    fun requestClear(category: CacheScope) {
        if(!busy && canRun()) {
            pendingMethod = method
            pending = CachePolicy.selectedInScope(groups, selected, category).takeIf { it.isNotEmpty() }
        }
    }

    private suspend fun readGroups(category: CacheScope = CacheScope.BOTH, keys: Set<String>? = null) {
        val pm = app.packageManager
        val stats = app.getSystemService(StorageStatsManager::class.java)
        val inventory = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0).filter { it.flags and ApplicationInfo.FLAG_INSTALLED != 0 }
                .groupBy { it.uid to it.storageUuid }.values.map { members ->
                    val first = members.first()
                    val system = members.any { it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
                    val descriptor = CacheGroup(first.uid, first.storageUuid, members.map { it.packageName }.sorted(),
                        members.map { runCatching { it.loadLabel(pm).toString() }.getOrDefault(it.packageName) }.sorted().joinToString(" / "), system, null)
                    descriptor
                }
        }
        val applications = inventory.filter { category.includes(it) && (keys == null || it.key in keys) }
        val refreshed = applications.map { it.key }.toSet()
        // Old numbers for this operation are invalid until each new measurement arrives.
        val previous = groups.associateBy { it.key }
        groups = inventory.map { descriptor ->
            if(descriptor.key in refreshed) descriptor else previous[descriptor.key] ?: descriptor
        }
        scannedAt = System.currentTimeMillis()
        partial = true
        progress = 0 to applications.size
        // Inventory remains selectable for Settings UI clearing without Usage Access.
        check(repo.hasUsageAccess()) { app.getString(R.string.cache_usage_help) }
        for(batch in applications.chunked(2)) {
            if(stopRequested) break
            currentApp = batch.joinToString(" / ") { it.label }
            val results = coroutineScope {
                batch.map { descriptor -> async {
                    try {
                        descriptor.copy(bytes = reader.read {
                            stats.queryStatsForUid(descriptor.volume, descriptor.uid).cacheBytes.coerceAtLeast(0)
                        })
                    } catch(e: Exception) {
                        if(e is CancellationException && e !is TimeoutCancellationException) throw e
                        descriptor.copy(error = e.message ?: e.javaClass.simpleName)
                    }
                } }.awaitAll()
            }
            val updates = results.associateBy { it.key }
            groups = groups.map { updates[it.key] ?: it }
            progress = progress.first + results.size to applications.size
        }
        selected = selected.intersect(groups.map { it.key }.toSet())
        scannedAt = System.currentTimeMillis()
        partial = stopRequested
    }

    /** Polls this group's cache size after a timed-out clear. Returns the last size, 0 when cleared, null if unreadable. */
    /**
     * Diagnostic only: records the measured size right after a clear. It never decides success,
     * because an app that is open (or reopened) can rebuild its cache at once.
     */
    private suspend fun logSizeAfter(pkg: String, group: CacheGroup) {
        if(!repo.hasUsageAccess()) return
        val stats = app.getSystemService(StorageStatsManager::class.java)
        val now = runCatching { reader.read { stats.queryStatsForUid(group.volume, group.uid).cacheBytes.coerceAtLeast(0) } }
            .getOrElse { if(it is CancellationException && it !is TimeoutCancellationException) throw it; null }
        log += "SIZE $pkg: cache before ${group.bytes ?: "unknown"} bytes, right after ${now ?: "unreadable"} bytes (an open app can refill its cache)"
    }

    private suspend fun awaitCacheCleared(group: CacheGroup): Long? {
        if(!repo.hasUsageAccess()) return null
        val stats = app.getSystemService(StorageStatsManager::class.java)
        var last: Long? = null
        val deadline = System.currentTimeMillis() + CachePolicy.TIMEOUT_GRACE_MS
        while(System.currentTimeMillis() < deadline && !stopRequested) {
            last = runCatching { reader.read { stats.queryStatsForUid(group.volume, group.uid).cacheBytes.coerceAtLeast(0) } }
                .getOrElse { if(it is CancellationException && it !is TimeoutCancellationException) throw it; null }
            if(CachePolicy.clearedAfterTimeout(group.bytes, last)) return last
            // Not a single byte changed for a while: Android most likely ignored the request.
            if(CachePolicy.likelyIgnored(group.bytes, last) &&
                System.currentTimeMillis() - (deadline - CachePolicy.TIMEOUT_GRACE_MS) >= CachePolicy.IGNORED_AFTER_MS) return last
            kotlinx.coroutines.delay(3_000)
        }
        return last
    }

    fun scan(category: CacheScope) {
        if(busy || !canRun()) return
        busy = true
        notice = null
        stopRequested = false
        phase = R.string.cache_scanner
        activeMethod = null
        job = scope.launch {
            try {
                supported = if(method == CacheClearMethod.SHIZUKU && ShizukuManager.status.value == ShizukuStatus.READY)
                    CachePolicy.supportsCacheOnly(ShizukuManager.execCache("pm help")) else null
                readGroups(category)
                notice = UiMessage(R.string.cache_scan_done, listOf(CachePolicy.inScope(groups, category).count { it.bytes == null }))
            } catch(e: Exception) {
                if(e is CancellationException) { notice = UiMessage(R.string.cache_stopped); return@launch }
                notice = UiMessage(R.string.cache_error, listOf(e.message.orEmpty()))
            } finally { busy = false; currentApp = "" }
        }
    }

    private fun returnFromSettings() {
        runCatching { app.startActivity(Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
    }

    fun confirmClear() {
        val targets = pending ?: return
        val clearMethod = pendingMethod
        pending = null
        if(busy || !canRun()) return
        if(clearMethod == CacheClearMethod.SHIZUKU && ShizukuManager.status.value != ShizukuStatus.READY) return
        if(clearMethod == CacheClearMethod.SETTINGS && !SettingsCacheService.available.value) {
            notice = UiMessage(R.string.cache_settings_enable_help)
            return
        }
        busy = true
        activeMethod = clearMethod
        notice = null
        stopRequested = false
        phase = R.string.cache_clear
        job = scope.launch {
            val changedPackages = mutableSetOf<String>()
            var stopNotice: UiMessage? = null
            val clearedPackages = mutableSetOf<String>()
            val shellFailed = mutableListOf<String>()
            var shellIgnoredAt: Int? = null
            var shellPackages = emptyList<String>()
            try {
                // No fallback to plain pm clear, global trim-caches or raw directory deletion.
                if(clearMethod == CacheClearMethod.SHIZUKU) {
                    supported = CachePolicy.supportsCacheOnly(ShizukuManager.execCache("pm help"))
                    if(supported != true) {
                        notice = UiMessage(R.string.cache_unsupported)
                        return@launch
                    }
                }
                // Resolve current labels and UIDs rather than trusting a stale scan for identity.
                val settingsTargets = if(clearMethod == CacheClearMethod.SETTINGS) withContext(Dispatchers.IO) {
                    val systemContext = app.createConfigurationContext(Configuration(app.resources.configuration)
                        .apply { setLocales(io.github.j0p3s.debloater.cache.SystemLocales.get(app)) })
                    val pm = systemContext.packageManager
                    @Suppress("DEPRECATION")
                    val installed = pm.getInstalledApplications(0)
                        .filter { it.flags and ApplicationInfo.FLAG_INSTALLED != 0 }
                    val labels = installed.associate { info ->
                        val display = info.loadLabel(pm).toString()
                        val localized = runCatching {
                            if(info.labelRes == 0) display else systemContext.createPackageContext(info.packageName, 0)
                                .createConfigurationContext(systemContext.resources.configuration).resources.getString(info.labelRes)
                        }.getOrDefault(display)
                        info.packageName to setOf(display, localized)
                    }
                    val counts = labels.values.flatten().groupingBy(SettingsCachePolicy::normalize).eachCount()
                    installed.filter { info -> targets.any { it.uid == info.uid && info.packageName in it.packages } }
                        .associate { info ->
                            val names = labels.getValue(info.packageName)
                            info.packageName to SettingsCachePolicy.Target(info.packageName, names.first(),
                                names.all { counts[SettingsCachePolicy.normalize(it)] == 1 }, names)
                        }
                } else emptyMap()
                var successes = 0
                var failures = 0
                val packages = targets.filter { it.bytes != 0L }.flatMap { it.packages }.distinct()
                shellPackages = packages
                progress = 0 to packages.size
                for((index, pkg) in packages.withIndex()) {
                    if(stopRequested) break
                    currentApp = pkg
                    progress = index + 1 to packages.size
                    // Invalidate first: cancellation or timeout must not leave stale successful-looking sizes.
                    groups = groups.map { if(pkg in it.packages) it.copy(bytes = null, error = null) else it }
                    scannedAt = null
                    changedPackages += pkg
                    if(clearMethod == CacheClearMethod.SHIZUKU) {
                        val command = CachePolicy.clearCommand(repo.userId, pkg)
                        log += "START $pkg: cache before ${targets.first { pkg in it.packages }.bytes ?: "unknown"} bytes"
                        val result = ShizukuManager.execCache(command)
                        var success = CachePolicy.succeeded(result)
                        log += "${if(success) "OK" else if(result.exitCode == 124) "WAIT" else "FAIL"}  $command\n${result.output}"
                        if(result.exitCode == 124) {
                            // Android keeps deleting after pm is stopped; check the real cache size before giving up.
                            val group = targets.first { pkg in it.packages }
                            val remaining = awaitCacheCleared(group)
                            success = CachePolicy.clearedAfterTimeout(group.bytes, remaining)
                            log += if(success) "OK $pkg: Android finished clearing after the command timeout (cache was ${group.bytes} bytes, now $remaining bytes)."
                                else "UNKNOWN $pkg: cache still ${remaining?.let { "$it bytes" } ?: "unreadable"} after waiting up to ${CachePolicy.TIMEOUT_GRACE_MS / 1000} s; batch stopped."
                            if(!success) {
                                failures++
                                stopRequested = true
                                if(CachePolicy.likelyIgnored(group.bytes, remaining)) {
                                    shellFailed += pkg
                                    shellIgnoredAt = index
                                    stopNotice = UiMessage(R.string.cache_shell_ignored)
                                    log += "IGNORED? $pkg: cache size did not change by a single byte, so Android probably ignored the shell request on this phone. Use the Settings method. (If the app was open, it may also have refilled its cache.)"
                                }
                                break
                            }
                        }
                        logSizeAfter(pkg, targets.first { pkg in it.packages })
                        if(!success) shellFailed += pkg
                        if(success) { successes++; clearedPackages += pkg } else failures++
                    } else {
                        log += "START Settings UI: $pkg → Storage → Clear cache (no shell command); cache before ${targets.first { pkg in it.packages }.bytes ?: "unknown"} bytes"
                        val result = settingsTargets[pkg]?.let { SettingsCacheService.clear(it, index, packages.size) { detail -> log += "$pkg: $detail" } } ?: SettingsCacheResult.SKIPPED
                        when(result) {
                            SettingsCacheResult.CONFIRMED -> { successes++; clearedPackages += pkg; log += "OK $pkg: Settings disabled Clear cache after the press; rescan checks the size." }
                            SettingsCacheResult.SKIPPED -> { failures++; log += "SKIP $pkg: unavailable, empty, protected assistant package or unsupported Settings layout." }
                            SettingsCacheResult.UNAVAILABLE -> { failures++; log += "SKIP $pkg: cache control could not be identified safely; no cache button was pressed. Continuing with the next app." }
                            SettingsCacheResult.UNKNOWN -> { failures++; log += "UNKNOWN $pkg: screen changed, cache outcome unverified or timed out; batch stopped." }
                        }
                        logSizeAfter(pkg, targets.first { pkg in it.packages })
                        if(result == SettingsCacheResult.UNKNOWN) { stopRequested = true; break }
                    }
                }
                selected = selected - targets.map { it.key }.toSet()
                // Never leave pre-clean measurements on screen as if they were current.
                scannedAt = null
                if(stopRequested) { notice = stopNotice ?: UiMessage(R.string.cache_stopped); return@launch }
                phase = R.string.cache_refreshing
                if(clearMethod == CacheClearMethod.SETTINGS) returnFromSettings()
                try {
                    readGroups(keys = targets.filter { it.bytes != 0L }.map { it.key }.toSet())
                    recordResidues(targets.filter { g -> g.bytes != 0L && g.packages.any { it in clearedPackages } })
                    notice = UiMessage(R.string.cache_clear_done, listOf(successes, failures))
                } catch(e: Exception) {
                    if(e is CancellationException) { notice = UiMessage(R.string.cache_stopped); return@launch }
                    notice = UiMessage(R.string.cache_rescan_failed, listOf(successes, failures, e.message.orEmpty()))
                }
            } catch(e: Exception) {
                if(e is CancellationException) { notice = UiMessage(R.string.cache_stopped); return@launch }
                groups = emptyList()
                scannedAt = null
                notice = UiMessage(R.string.cache_error, listOf(e.message.orEmpty()))
            } finally {
                if(clearMethod == CacheClearMethod.SHIZUKU) {
                    // Untried apps are offered only when Android ignored Shizuku, not after a user stop.
                    val untried = shellIgnoredAt?.let { shellPackages.drop(it + 1) }.orEmpty()
                    fallback = CachePolicy.fallback(targets, shellFailed, untried)
                    fallback?.let { f -> log += "OFFER Settings method for: failed ${f.failed.flatMap { it.packages }}; not tried ${f.untried.flatMap { it.packages }}" }
                }
                if(clearMethod == CacheClearMethod.SETTINGS) {
                    log += "Settings UI session ended; completed actions are not undone."
                    returnFromSettings()
                }
                busy = false
                activeMethod = null
                currentApp = ""
                if(changedPackages.isNotEmpty()) afterClear(changedPackages)
            }
        }
    }
}
