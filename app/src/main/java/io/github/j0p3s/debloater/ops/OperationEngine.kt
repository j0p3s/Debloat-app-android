package io.github.j0p3s.debloater.ops

import io.github.j0p3s.debloater.backup.BackupEntry
import io.github.j0p3s.debloater.backup.BackupManager
import io.github.j0p3s.debloater.backup.BackupSession
import io.github.j0p3s.debloater.backup.EntryStatus
import io.github.j0p3s.debloater.data.Action
import io.github.j0p3s.debloater.data.AppInfo
import io.github.j0p3s.debloater.data.AppState
import io.github.j0p3s.debloater.data.PackageRepository
import io.github.j0p3s.debloater.data.SafetyLevel
import io.github.j0p3s.debloater.data.SpaceEstimator
import io.github.j0p3s.debloater.data.StorageInfo
import io.github.j0p3s.debloater.shizuku.ShellResult
import io.github.j0p3s.debloater.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LineKind { STEP, INFO, OK, WARN, ERROR, COMMAND, OUTPUT }

data class LogLine(val time: Long, val kind: LineKind, val text: String, val packageName: String? = null)

data class OperationState(
    val title: String,
    val dryRun: Boolean,
    val action: Action,
    val total: Int,
    val processed: Int = 0,
    val current: String? = null,
    val lines: List<LogLine> = emptyList(),
    val ok: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val warnings: Int = 0,
    val finished: Boolean = false,
    val cancelled: Boolean = false,
    val sessionId: String? = null,
    val logFile: String? = null,
    val keepData: Boolean = true,
    /** Phone storage at the start and the end of the run. */
    val storageBefore: StorageInfo? = null,
    val storageAfter: StorageInfo? = null,
    /** Estimated space this run frees (shown for tests and before real runs). */
    val estimatedBytes: Long = 0,
    /** Measured space freed (sum of per-app measurements around each command). */
    val freedBytes: Long = 0,
    /** Space used by the APK backups of this run. */
    val backupBytes: Long = 0,
    /** Packages whose command ran but did not take effect (e.g. cannot be uninstalled on this ROM). */
    val failedPackages: List<String> = emptyList(),
) {
    val hasErrors get() = failed > 0
}

/** One package to restore from a backup session. */
data class RestoreItem(val sessionId: String?, val entry: BackupEntry)

class OperationEngine(
    private val repo: PackageRepository,
    private val backups: BackupManager,
) {
    private val _state = MutableStateFlow<OperationState?>(null)
    val state: StateFlow<OperationState?> = _state.asStateFlow()

    @Volatile private var cancelRequested = false

    fun cancel() {
        cancelRequested = true
    }

    fun clear() {
        if (_state.value?.finished != false) _state.value = null
    }

    val isRunning get() = _state.value?.let { !it.finished } ?: false

    // ---------------------------------------------------------------- logging

    private fun log(kind: LineKind, text: String, pkg: String? = null) {
        val tag = "operation"
        val line = "${kind.name} ${pkg?.let { "[$it] " }.orEmpty()}$text"
        if (kind == LineKind.ERROR) io.github.j0p3s.debloater.debug.DebugLog.warn(tag, line) else io.github.j0p3s.debloater.debug.DebugLog.info(tag, line)
        _state.update { s -> s?.copy(lines = s.lines + LogLine(System.currentTimeMillis(), kind, text, pkg)) }
    }

    private fun bump(ok: Int = 0, skipped: Int = 0, failed: Int = 0, warnings: Int = 0) {
        _state.update { s ->
            s?.copy(ok = s.ok + ok, skipped = s.skipped + skipped, failed = s.failed + failed, warnings = s.warnings + warnings)
        }
    }

    private fun logResult(r: ShellResult) {
        if (r.output.isNotBlank()) log(LineKind.OUTPUT, r.output)
        if (r.exitCode != 0) log(LineKind.OUTPUT, "exit code ${r.exitCode}")
    }

    private fun begin(title: String, action: Action, dryRun: Boolean, total: Int) {
        cancelRequested = false
        _state.value = OperationState(title = title, dryRun = dryRun, action = action, total = total)
    }

    private fun finish(name: String) {
        val s = _state.value ?: return
        val summary = buildString {
            if (s.dryRun) {
                append(
                    if (s.failed == 0) "TEST PASSED: no errors found. Nothing on your device was changed."
                    else "TEST FOUND ${s.failed} PROBLEM(S). Nothing on your device was changed.",
                )
            } else {
                append("Finished: ${s.ok} succeeded, ${s.skipped} skipped, ${s.failed} failed.")
            }
            if (s.cancelled) append(" (cancelled by user)")
            if (s.action == Action.REMOVE || s.action == Action.DISABLE) {
                if (s.dryRun) append(" Estimated space freed: ${fmtSize(s.estimatedBytes)}.")
                else append(" Space freed: ${fmtSize(s.freedBytes)}.")
            }
        }
        log(if (s.failed == 0) LineKind.OK else LineKind.ERROR, summary)
        val text = renderLog(_state.value!!)
        val file = runCatching { backups.writeLog(name, text) }.getOrNull()
        _state.update { it?.copy(finished = true, current = null, logFile = file?.absolutePath) }
    }

    fun renderLog(s: OperationState): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        return buildString {
            appendLine("# ${s.title}")
            appendLine("# ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            s.lines.forEach { appendLine("${fmt.format(Date(it.time))} [${it.kind}] ${it.text}") }
        }
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    // ---------------------------------------------------------------- commands

    private fun command(app: AppInfo, action: Action, keepData: Boolean): String {
        val u = repo.userId
        val p = app.packageName
        return when (action) {
            Action.DISABLE -> "pm disable-user --user $u $p"
            // System apps are always removed per-user with -k so they can be reinstalled with install-existing.
            // Without -k the app data is deleted (frees space). System apps can still be brought back
            // with install-existing because their APK stays on the system partition.
            Action.REMOVE -> if (keepData) "pm uninstall -k --user $u $p" else "pm uninstall --user $u $p"
            Action.ENABLE -> "pm enable --user $u $p"
            // Restore handles both removed (install-existing) and disabled packages.
            Action.RESTORE -> if (repo.stateOf(p) == AppState.UNINSTALLED) {
                "cmd package install-existing --user $u $p && pm enable --user $u $p"
            } else {
                "pm enable --user $u $p"
            }
        }
    }

    private fun targetReached(action: Action, state: AppState?): Boolean = when (action) {
        Action.DISABLE -> state == AppState.DISABLED
        Action.REMOVE -> state == AppState.UNINSTALLED || state == null
        Action.ENABLE, Action.RESTORE -> state == AppState.ENABLED
    }

    /** Returns null if the app can be processed, or a reason to skip/block it. Pair(isError, message). */
    private fun precheck(app: AppInfo, action: Action, allowUnsafe: Boolean): Pair<Boolean, String>? {
        if (!PackageRepository.isValidPackageName(app.packageName)) return true to "Invalid package name, refusing to continue"
        if (action == Action.DISABLE || action == Action.REMOVE) {
            app.protectedReason?.let { return true to "Protected package ($it). It is never modified." }
            if ((app.level == SafetyLevel.UNSAFE || app.level == SafetyLevel.EXPERT) && !allowUnsafe) {
                return true to "Rated ${app.level.title}. Turn on expert mode in Settings to unlock it."
            }
        }
        val current = repo.stateOf(app.packageName) ?: return true to "Package not found on device"
        return when {
            targetReached(action, current) -> false to "Already ${action.pastTense}, nothing to do"
            action == Action.DISABLE && current == AppState.UNINSTALLED -> false to "Package is removed; disabling not needed"
            action == Action.ENABLE && current == AppState.UNINSTALLED -> false to "Package is removed; use Restore instead"
            else -> null
        }
    }

    // ---------------------------------------------------------------- main entry

    /**
     * Runs [action] on [apps]. With [dryRun] the whole process is simulated and checked
     * (Shizuku access, package visibility, APK readability, free space for backups) but
     * nothing on the device is changed.
     */
    suspend fun run(
        apps: List<AppInfo>,
        action: Action,
        dryRun: Boolean,
        keepData: Boolean,
        allowUnsafe: Boolean,
        allApps: List<AppInfo>,
    ) {
        val title = if (dryRun) "Test run: ${action.verb.lowercase()} ${apps.size} app(s)" else "${action.verb} ${apps.size} app(s)"
        // Publish the state before switching threads so the UI never sees a null state.
        begin(title, action, dryRun, apps.size)
        withContext(Dispatchers.IO) { runInternal(apps, action, dryRun, keepData, allowUnsafe, allApps) }
    }

    private suspend fun runInternal(
        apps: List<AppInfo>,
        action: Action,
        dryRun: Boolean,
        keepData: Boolean,
        allowUnsafe: Boolean,
        allApps: List<AppInfo>,
    ) {
        val needsApkBackup = action == Action.DISABLE || action == Action.REMOVE

        // ---- global checks
        log(LineKind.STEP, "Checking environment")
        val storageBefore = repo.storageInfo()
        val estimate = SpaceEstimator.estimate(apps, action, keepData)
        _state.update { it?.copy(storageBefore = storageBefore, estimatedBytes = estimate, keepData = keepData) }
        log(LineKind.INFO, "Storage: ${fmtSize(storageBefore.freeBytes)} free of ${fmtSize(storageBefore.totalBytes)} (${fmtSize(storageBefore.usedBytes)} used)")
        when {
            action == Action.DISABLE -> log(LineKind.INFO, "Disabling does not free storage: apps and their data stay on the phone")
            action == Action.REMOVE && keepData -> log(LineKind.INFO, "Keeping app data: almost no storage is freed (data and APKs stay for restoring with data)")
            action == Action.REMOVE -> log(LineKind.WARN, "App data will be DELETED. Estimated space freed: ${fmtSize(estimate)}${if (apps.any { !it.sizeExact }) " (some app sizes unknown, grant Usage access for exact values)" else ""}")
            else -> {}
        }
        log(LineKind.INFO, "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), user ${repo.userId}")
        if (android.os.Build.VERSION.SDK_INT > 37) {
            log(LineKind.WARN, "This Android version is newer than Android 17 and has not been tested")
            bump(warnings = 1)
        }
        if (!ShizukuManager.isReady()) {
            log(LineKind.ERROR, "Shizuku is not ready (${ShizukuManager.status.value}). Start Shizuku and grant permission.")
            bump(failed = apps.size)
            finish("${if (dryRun) "test" else "run"}-${stamp()}")
            return
        }
        val id = ShizukuManager.exec("id")
        if (!id.isSuccess) {
            log(LineKind.ERROR, "Could not run commands through Shizuku: ${id.output}")
            bump(failed = apps.size)
            finish("${if (dryRun) "test" else "run"}-${stamp()}")
            return
        }
        log(LineKind.OK, "Shizuku shell works: ${id.stdout.substringBefore(" groups")}")

        if (needsApkBackup) {
            if (!backups.canWrite()) {
                log(LineKind.ERROR, "Backup folder is not writable: ${backups.root}")
                bump(failed = apps.size)
                finish("${if (dryRun) "test" else "run"}-${stamp()}")
                return
            }
            val needed = apps.filter { it.state != AppState.UNINSTALLED }.sumOf { it.apkSize }
            val free = backups.freeBytes()
            val margin = needed + needed / 5 + 20L * 1024 * 1024
            if (free < margin) {
                log(LineKind.ERROR, "Not enough free space for backups: need about ${fmtSize(margin)}, ${fmtSize(free)} free")
                bump(failed = apps.size)
                finish("${if (dryRun) "test" else "run"}-${stamp()}")
                return
            }
            log(LineKind.OK, "Backup folder writable, ${fmtSize(free)} free, about ${fmtSize(needed)} needed for APK backups")
        }

        val session: BackupSession? = if (!dryRun) {
            log(LineKind.STEP, "Creating restore point")
            val s = backups.createSession(action, repo.userId, allApps)
            log(LineKind.OK, "Restore point ${s.id} saved (state of ${allApps.size} packages)")
            _state.update { it?.copy(sessionId = s.id) }
            s
        } else null

        // ---- per package
        for ((index, app) in apps.withIndex()) {
            if (cancelRequested) {
                log(LineKind.WARN, "Cancelled. Remaining ${apps.size - index} app(s) were not touched.")
                _state.update { it?.copy(cancelled = true) }
                break
            }
            _state.update { it?.copy(current = app.label, processed = index) }
            log(LineKind.STEP, "[${index + 1}/${apps.size}] ${app.label} (${app.packageName})", app.packageName)
            if (app.level == SafetyLevel.EXTREME || app.level == SafetyLevel.EXPERT || app.level == SafetyLevel.UNSAFE) {
                log(LineKind.WARN, "${app.level.title}: ${app.entry?.warning ?: "may break features"}", app.packageName)
                bump(warnings = 1)
            }
            app.entry?.neededBy?.takeIf { it.isNotEmpty() }?.let {
                log(LineKind.WARN, "Needed by: ${it.joinToString()}", app.packageName)
                bump(warnings = 1)
            }
            if (action == Action.REMOVE && app.entry?.oftenCantUninstall == true) {
                log(LineKind.INFO, "This package often cannot be uninstalled on its ROM. If removal fails, use Disable.", app.packageName)
            }
            precheck(app, action, allowUnsafe)?.let { (isError, msg) ->
                log(if (isError) LineKind.ERROR else LineKind.INFO, msg, app.packageName)
                if (isError) bump(failed = 1) else bump(skipped = 1)
                if (session != null && isError) {
                    session.entries += entryFor(app, action, keepData).copy(status = EntryStatus.SKIPPED, message = msg)
                    backups.save(session)
                }
                continue
            }
            val cmd = command(app, action, keepData)
            if (dryRun) simulate(app, cmd, needsApkBackup) else execute(session!!, app, action, keepData, cmd)
        }
        _state.update { it?.copy(processed = apps.size) }
        val storageAfter = repo.storageInfo()
        _state.update { it?.copy(storageAfter = storageAfter) }
        val st = _state.value!!
        log(LineKind.STEP, "Storage")
        if (dryRun) {
            log(LineKind.INFO, "Would free about ${fmtSize(st.estimatedBytes)}; APK backups would use about ${fmtSize(apps.filter { it.state != AppState.UNINSTALLED }.sumOf { it.apkSize })}")
        } else {
            log(LineKind.OK, "Space freed on the phone: ${fmtSize(st.freedBytes)} (estimated ${fmtSize(st.estimatedBytes)})")
            if (st.backupBytes > 0) log(LineKind.INFO, "APK backups of this run use ${fmtSize(st.backupBytes)} (delete old backups to reclaim it)")
        }
        log(LineKind.INFO, "Free now: ${fmtSize(storageAfter.freeBytes)} of ${fmtSize(storageAfter.totalBytes)}")
        session?.let {
            it.finished = true
            it.freeBefore = storageBefore.freeBytes
            it.freeAfter = storageAfter.freeBytes
            it.totalStorage = storageAfter.totalBytes
            it.backupBytes = st.backupBytes
            it.estimatedBytes = st.estimatedBytes
            backups.save(it)
        }
        finish(if (dryRun) "test-${stamp()}" else (session?.id ?: "run-${stamp()}"))
    }

    private suspend fun simulate(app: AppInfo, cmd: String, needsApkBackup: Boolean) {
        val path = ShizukuManager.exec("pm path ${app.packageName}")
        if (!path.isSuccess || !path.stdout.contains("package:")) {
            // Removed-for-user system apps can still be seen by PackageManager, so treat as a warning.
            val listed = ShizukuManager.exec("pm list packages -u ${app.packageName}")
            if (!listed.stdout.lines().any { it.trim() == "package:${app.packageName}" }) {
                log(LineKind.ERROR, "Shell cannot see this package: ${path.output}", app.packageName)
                bump(failed = 1)
                return
            }
            log(LineKind.WARN, "pm path returned nothing; package exists but is hidden for this user", app.packageName)
            bump(warnings = 1)
        } else {
            log(LineKind.OK, "Shell can see the package (${path.stdout.lines().size} APK file(s))", app.packageName)
        }
        if (needsApkBackup) {
            log(LineKind.INFO, "Would back up state and ${app.apkPaths.size} APK file(s), ${fmtSize(app.apkSize)}", app.packageName)
            for (p in app.apkPaths) {
                val readable = File(p).canRead() || ShizukuManager.exec("test -r '${p.replace("'", "")}'").exitCode == 0
                if (!readable) {
                    log(LineKind.ERROR, "APK not readable, backup would fail: $p", app.packageName)
                    bump(failed = 1)
                    return
                }
            }
            log(LineKind.OK, "All APK files readable, backup possible", app.packageName)
        }
        log(LineKind.INFO, "Size: ${sizeText(app)}", app.packageName)
        val est = SpaceEstimator.estimate(app, _state.value?.action ?: Action.DISABLE, _state.value?.keepData ?: true)
        if (est > 0) log(LineKind.INFO, "Would free about ${fmtSize(est)}", app.packageName)
        log(LineKind.COMMAND, "Would run: $cmd", app.packageName)
        log(LineKind.OK, "OK, ready (no changes made)", app.packageName)
        bump(ok = 1)
    }

    private fun entryFor(app: AppInfo, action: Action, keepData: Boolean) = BackupEntry(
        packageName = app.packageName,
        label = app.label,
        isSystem = app.isSystem,
        versionName = app.versionName,
        versionCode = app.versionCode,
        stateBefore = repo.stateOf(app.packageName) ?: app.state,
        action = action,
        keepData = keepData,
        sizeBytes = app.totalBytes,
    )

    private suspend fun execute(session: BackupSession, app: AppInfo, action: Action, keepData: Boolean, cmd: String) {
        var entry = entryFor(app, action, keepData)
        session.entries += entry
        val idx = session.entries.lastIndex
        fun update(e: BackupEntry) {
            entry = e
            session.entries[idx] = e
            backups.save(session)
        }
        backups.save(session)
        log(LineKind.INFO, "Previous state recorded: ${entry.stateBefore.title}", app.packageName)

        // Mandatory APK backup before anything destructive.
        if (action == Action.DISABLE || action == Action.REMOVE) {
            log(LineKind.INFO, "Backing up ${app.apkPaths.size} APK file(s)…", app.packageName)
            val result = backups.backupApks(session, app)
            val files = result.getOrElse {
                log(LineKind.ERROR, "Backup failed, package NOT modified: ${it.message}", app.packageName)
                update(entry.copy(status = EntryStatus.FAILED, message = "Backup failed: ${it.message}"))
                bump(failed = 1)
                return
            }
            update(entry.copy(apkFiles = files.map { it.name }, apkBytes = files.sumOf { it.length() }, status = EntryStatus.BACKED_UP))
            log(LineKind.OK, "Backup verified (${fmtSize(entry.apkBytes)})", app.packageName)
            _state.update { it?.copy(backupBytes = it.backupBytes + entry.apkBytes) }
        }

        log(LineKind.INFO, "Size: ${sizeText(app)}", app.packageName)
        log(LineKind.COMMAND, "$ $cmd", app.packageName)
        // Measure free space right around the command so APK backups are not counted.
        val freeBefore = repo.freeBytesNow()
        val r = ShizukuManager.exec(cmd)
        val freed = (repo.freeBytesNow() - freeBefore).coerceAtLeast(0L)
        logResult(r)
        val after = repo.stateOf(app.packageName)
        if (targetReached(action, after)) {
            log(LineKind.OK, "${app.label} ${action.pastTense}" + if (freed > 0) ", freed ${fmtSize(freed)}" else "", app.packageName)
            update(entry.copy(status = EntryStatus.DONE, message = r.output.take(500), freedBytes = freed))
            _state.update { it?.copy(freedBytes = it.freedBytes + freed) }
            bump(ok = 1)
        } else {
            val msg = "Command did not take effect (state is ${after?.title ?: "gone"}): ${r.output}".take(500)
            log(LineKind.ERROR, msg, app.packageName)
            update(entry.copy(status = EntryStatus.FAILED, message = msg))
            bump(failed = 1)
            _state.update { it?.copy(failedPackages = it.failedPackages + app.packageName) }
        }
    }

    // ---------------------------------------------------------------- restore

    /** Puts packages back into the state they had before they were changed. */
    suspend fun restore(items: List<RestoreItem>, dryRun: Boolean, title: String) {
        begin(if (dryRun) "Test run: $title" else title, Action.RESTORE, dryRun, items.size)
        withContext(Dispatchers.IO) { restoreInternal(items, dryRun) }
    }

    private suspend fun restoreInternal(items: List<RestoreItem>, dryRun: Boolean) {
        val storageBefore = repo.storageInfo()
        _state.update { it?.copy(storageBefore = storageBefore) }
        log(LineKind.INFO, "Storage: ${fmtSize(storageBefore.freeBytes)} free of ${fmtSize(storageBefore.totalBytes)}")
        if (!ShizukuManager.isReady()) {
            log(LineKind.ERROR, "Shizuku is not ready (${ShizukuManager.status.value})")
            bump(failed = items.size)
            finish("restore-${stamp()}")
            return
        }
        val sessions = HashMap<String, BackupSession>()
        backups.listSessions().forEach { sessions[it.id] = it }
        val u = repo.userId

        for ((index, item) in items.withIndex()) {
            if (cancelRequested) {
                log(LineKind.WARN, "Cancelled. Remaining ${items.size - index} app(s) were not touched.")
                _state.update { it?.copy(cancelled = true) }
                break
            }
            val e = item.entry
            val pkg = e.packageName
            _state.update { it?.copy(current = e.label, processed = index) }
            log(LineKind.STEP, "[${index + 1}/${items.size}] ${e.label} ($pkg)", pkg)
            if (!PackageRepository.isValidPackageName(pkg)) {
                log(LineKind.ERROR, "Invalid package name", pkg); bump(failed = 1); continue
            }
            val target = if (e.stateBefore == AppState.UNINSTALLED) AppState.UNINSTALLED else e.stateBefore
            val current = repo.stateOf(pkg)
            if (current == target) {
                log(LineKind.INFO, "Already in its original state (${target.title})", pkg)
                bump(skipped = 1)
                markRestored(sessions, item, EntryStatus.RESTORED, "Already restored", dryRun)
                continue
            }
            val steps = mutableListOf<String>()
            var apks: List<File> = emptyList()
            when (current) {
                null -> {
                    apks = item.sessionId?.let { backups.apkFilesFor(it, e) }.orEmpty()
                        .ifEmpty { backups.latestApkBackup(pkg) }
                    if (apks.isEmpty()) {
                        log(LineKind.ERROR, "Package is not on the device and no APK backup was found", pkg)
                        bump(failed = 1)
                        markRestored(sessions, item, EntryStatus.RESTORE_FAILED, "No APK backup", dryRun)
                        continue
                    }
                    steps += "INSTALL"
                }
                AppState.UNINSTALLED -> steps += "cmd package install-existing --user $u $pkg"
                else -> {}
            }
            when (target) {
                AppState.ENABLED -> if (current == AppState.DISABLED || current == AppState.UNINSTALLED) {
                    steps += "pm enable --user $u $pkg"
                }
                AppState.DISABLED -> steps += "pm disable-user --user $u $pkg"
                AppState.UNINSTALLED -> steps.clear().also { steps += "pm uninstall -k --user $u $pkg" }
            }
            if (dryRun) {
                steps.forEach {
                    log(LineKind.COMMAND, if (it == "INSTALL") "Would reinstall from APK backup: ${apks.joinToString { f -> f.name }}" else "Would run: $it", pkg)
                }
                log(LineKind.OK, "OK, restore possible (no changes made)", pkg)
                bump(ok = 1)
                continue
            }
            var stepFailed = false
            for (step in steps) {
                if (step == "INSTALL") {
                    log(LineKind.COMMAND, "Installing from backup: ${apks.joinToString { it.name }}", pkg)
                    val r = ShizukuManager.installApks(apks, u)
                    logResult(r)
                    if (r.exitCode != 0) { stepFailed = true; break }
                } else {
                    log(LineKind.COMMAND, "$ $step", pkg)
                    val r = ShizukuManager.exec(step)
                    logResult(r)
                    if (!r.isSuccess && step.startsWith("cmd package install-existing")) {
                        // Fall back to the APK backup if install-existing is refused.
                        val files = item.sessionId?.let { backups.apkFilesFor(it, e) }.orEmpty().ifEmpty { backups.latestApkBackup(pkg) }
                        if (files.isNotEmpty()) {
                            log(LineKind.WARN, "install-existing failed, trying the APK backup", pkg)
                            val r2 = ShizukuManager.installApks(files, u)
                            logResult(r2)
                            if (r2.exitCode != 0) { stepFailed = true; break }
                        } else { stepFailed = true; break }
                    }
                }
            }
            val after = repo.stateOf(pkg)
            if (!stepFailed && after == target) {
                log(LineKind.OK, "${e.label} restored (${target.title})", pkg)
                bump(ok = 1)
                markRestored(sessions, item, EntryStatus.RESTORED, "Restored", false)
            } else {
                log(LineKind.ERROR, "Restore did not complete (state is ${after?.title ?: "not installed"})", pkg)
                bump(failed = 1)
                markRestored(sessions, item, EntryStatus.RESTORE_FAILED, "State after restore: ${after?.title ?: "not installed"}", false)
            }
        }
        _state.update { it?.copy(processed = items.size) }
        val storageAfter = repo.storageInfo()
        _state.update { it?.copy(storageAfter = storageAfter) }
        if (!dryRun) {
            val used = (storageBefore.freeBytes - storageAfter.freeBytes).coerceAtLeast(0L)
            log(LineKind.INFO, "Restored apps use ${fmtSize(used)} again. Free now: ${fmtSize(storageAfter.freeBytes)} of ${fmtSize(storageAfter.totalBytes)}")
        }
        finish("${if (dryRun) "test-" else ""}restore-${stamp()}")
    }

    private fun markRestored(sessions: Map<String, BackupSession>, item: RestoreItem, status: EntryStatus, msg: String, dryRun: Boolean) {
        if (dryRun) return
        val s = item.sessionId?.let { sessions[it] } ?: return
        val i = s.entries.indexOfFirst { it.packageName == item.entry.packageName && it.action == item.entry.action }
        if (i >= 0) {
            s.entries[i] = s.entries[i].copy(status = status, message = msg)
            backups.save(s)
        }
    }

    /**
     * Builds restore items that bring every package back to the state recorded in the
     * baseline snapshot (taken the first time the app listed packages).
     */
    fun baselineRestoreItems(current: List<AppInfo>): List<RestoreItem> {
        val f = File(backups.root, BackupManager.BASELINE)
        if (!f.exists()) return emptyList()
        val arr = JSONObject(f.readText()).getJSONArray("packages")
        val now = current.associateBy { it.packageName }
        val items = mutableListOf<RestoreItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pkg = o.getString("package")
            val before = runCatching { AppState.valueOf(o.getString("state")) }.getOrDefault(AppState.ENABLED)
            val cur = now[pkg]
            val changed = when {
                cur == null -> before != AppState.UNINSTALLED && backups.latestApkBackup(pkg).isNotEmpty()
                else -> cur.state != before && before != AppState.UNINSTALLED
            }
            if (changed) {
                items += RestoreItem(
                    null,
                    BackupEntry(
                        packageName = pkg, label = o.optString("label", pkg), isSystem = o.optBoolean("system"),
                        versionName = "", versionCode = o.optLong("versionCode"), stateBefore = before,
                        action = Action.RESTORE, keepData = true,
                    ),
                )
            }
        }
        return items
    }

    companion object {
        fun sizeText(app: AppInfo): String =
            if (app.sizeExact) "app ${fmtSize(app.appBytes)} + data ${fmtSize(app.dataBytes)} (cache ${fmtSize(app.cacheBytes)}) = ${fmtSize(app.totalBytes)}"
            else "APK ${fmtSize(app.apkSize)} (data size unknown)"

        fun fmtSize(bytes: Long): String = when {
            bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
            bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
