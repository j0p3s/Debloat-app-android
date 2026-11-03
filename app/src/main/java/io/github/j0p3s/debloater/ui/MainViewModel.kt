package io.github.j0p3s.debloater.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.backup.BackupEntry
import io.github.j0p3s.debloater.backup.BackupManager
import io.github.j0p3s.debloater.backup.BackupSession
import io.github.j0p3s.debloater.backup.EntryStatus
import io.github.j0p3s.debloater.data.Action
import io.github.j0p3s.debloater.data.AppInfo
import io.github.j0p3s.debloater.data.DebloatDatabase
import io.github.j0p3s.debloater.data.ListUpdater
import io.github.j0p3s.debloater.data.PackageRepository
import io.github.j0p3s.debloater.data.Prefs
import io.github.j0p3s.debloater.data.StorageInfo
import io.github.j0p3s.debloater.ops.OperationEngine
import io.github.j0p3s.debloater.ops.RestoreItem
import io.github.j0p3s.debloater.shizuku.ShizukuManager
import io.github.j0p3s.debloater.shizuku.ShizukuStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State of an in-app debloat list update. */
sealed interface ListUpdateState {
    data object Idle : ListUpdateState
    data class Running(val step: String) : ListUpdateState
    data class Done(val report: ListUpdater.Report) : ListUpdateState
    data class Failed(val message: String) : ListUpdateState
}

enum class Screen { HOME, DEBLOAT, APP_MANAGER, OPERATION, BACKUPS, SETTINGS, ADL_CHECK, USER_CACHE, SYSTEM_CACHE, CLEANUP, HELP }

/** A request that needs the user's confirmation before it runs. */
data class PendingOperation(
    val apps: List<AppInfo>,
    val action: Action,
    val dryRun: Boolean,
)

/** What was last run, so a passed test can be followed by the real run. */
private sealed interface LastRequest {
    data class Run(val apps: List<String>, val action: Action, val keepData: Boolean) : LastRequest
    data class Restore(val items: List<RestoreItem>, val title: String) : LastRequest
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = PackageRepository(app)
    val backups = BackupManager(app)
    val engine = OperationEngine(repo, backups)
    val cache = CacheController(app, repo, viewModelScope, { !engine.isRunning }, { refreshApps() })
    val prefs = Prefs(app)
    /** Current debloat list (re-read after an in-app update). */
    val database: DebloatDatabase get() = DebloatDatabase.get(getApplication())
    val listUpdater = ListUpdater(app)

    private val _listUpdate = MutableStateFlow<ListUpdateState>(ListUpdateState.Idle)
    val listUpdate: StateFlow<ListUpdateState> = _listUpdate.asStateFlow()

    val shizukuStatus: StateFlow<ShizukuStatus> = ShizukuManager.status
    val shizukuUid: StateFlow<Int> = ShizukuManager.serverUid
    val operation = engine.state

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _sessions = MutableStateFlow<List<BackupSession>>(emptyList())
    val sessions: StateFlow<List<BackupSession>> = _sessions.asStateFlow()

    private val _backStack = MutableStateFlow(listOf(Screen.HOME))
    val backStack: StateFlow<List<Screen>> = _backStack.asStateFlow()

    private val _pending = MutableStateFlow<PendingOperation?>(null)
    val pending: StateFlow<PendingOperation?> = _pending.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private var lastRequest: LastRequest? = null

    private val _storage = MutableStateFlow<StorageInfo?>(null)
    /** Total and free internal storage of the phone. */
    val storage: StateFlow<StorageInfo?> = _storage.asStateFlow()

    private val _backupsBytes = MutableStateFlow(0L)
    /** Disk space used by all APK backups and restore points. */
    val backupsBytes: StateFlow<Long> = _backupsBytes.asStateFlow()

    private val _usageAccess = MutableStateFlow(repo.hasUsageAccess())
    /** Whether exact app/data/cache sizes are available. */
    val usageAccess: StateFlow<Boolean> = _usageAccess.asStateFlow()
    private var triedUsageGrant = false

    /** Selections survive configuration changes because they live here. */
    private val _debloatSelection = MutableStateFlow<Set<String>>(emptySet())
    val debloatSelection: StateFlow<Set<String>> = _debloatSelection.asStateFlow()
    private val _managerSelection = MutableStateFlow<Set<String>>(emptySet())
    val managerSelection: StateFlow<Set<String>> = _managerSelection.asStateFlow()

    fun setDebloatSelection(s: Set<String>) { _debloatSelection.value = s }

    /** Guided clean-up selection; null until the screen is first opened (then everything is selected). */
    private val _cleanupSelection = MutableStateFlow<Set<String>?>(null)
    val cleanupSelection: StateFlow<Set<String>?> = _cleanupSelection.asStateFlow()
    fun setCleanupSelection(s: Set<String>) { _cleanupSelection.value = s }
    fun setManagerSelection(s: Set<String>) { _managerSelection.value = s }

    init {
        refreshApps()
        refreshSessions()
        maybeAutoUpdateLists()
        // Once Shizuku is ready, give ourselves Usage access so exact data sizes can be read.
        viewModelScope.launch {
            shizukuStatus.collect { st ->
                if (st == ShizukuStatus.READY && !repo.hasUsageAccess() && !triedUsageGrant) {
                    triedUsageGrant = true
                    grantUsageAccessWithShizuku()
                }
            }
        }
    }

    // ------------------------------------------------------------ list updates

    /** Downloads the newest lists from GitHub, validates them and reloads the app list. */
    fun updateLists(silent: Boolean = false) {
        if (_listUpdate.value is ListUpdateState.Running) return
        viewModelScope.launch {
            _listUpdate.value = ListUpdateState.Running("Starting…")
            _listUpdate.value = try {
                val report = listUpdater.update { step -> _listUpdate.value = ListUpdateState.Running(step) }
                refreshApps()
                if (silent && report.anyUpdated) _message.value = UiMessage(R.string.message_lists_updated, listOf(report.packages))
                ListUpdateState.Done(report)
            } catch (t: Throwable) {
                ListUpdateState.Failed(t.message ?: t.toString())
            }
        }
    }

    /** Goes back to the lists built into the APK. */
    fun resetLists() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { listUpdater.reset() }
            _listUpdate.value = ListUpdateState.Idle
            refreshApps()
            _message.value = UiMessage(R.string.message_lists_reset)
        }
    }

    private fun maybeAutoUpdateLists() {
        if (!prefs.autoUpdateLists.value) return
        val last = listUpdater.lastChecked() ?: 0L
        if (System.currentTimeMillis() - last > 7L * 24 * 60 * 60 * 1000) updateLists(silent = true)
    }

    fun grantUsageAccessWithShizuku() {
        viewModelScope.launch {
            val pkg = getApplication<Application>().packageName
            ShizukuManager.exec("appops set $pkg GET_USAGE_STATS allow")
            if (repo.hasUsageAccess()) refreshApps()
            else _usageAccess.value = false
        }
    }

    // ------------------------------------------------------------ navigation

    fun navigate(screen: Screen) {
        if (cache.busy) return
        _backStack.value = _backStack.value + screen
    }

    /** Returns false when there is nothing to go back to. */
    fun back(): Boolean {
        if (cache.busy) return true
        val stack = _backStack.value
        if (stack.last() == Screen.OPERATION && engine.isRunning) return true
        if (stack.size <= 1) return false
        if (stack.last() == Screen.OPERATION) engine.clear()
        _backStack.value = stack.dropLast(1)
        return true
    }

    fun consumeMessage() { _message.value = null }

    // ------------------------------------------------------------ data

    fun refreshShizuku() {
        ShizukuManager.refresh()
        // The user may have granted Usage access in Settings meanwhile.
        if (repo.hasUsageAccess() != _usageAccess.value) refreshApps()
    }

    fun refreshApps() {
        viewModelScope.launch {
            _loading.value = true
            _usageAccess.value = repo.hasUsageAccess()
            _storage.value = withContext(Dispatchers.IO) { runCatching { repo.storageInfo() }.getOrNull() }
            val list = runCatching { repo.loadAll() }.getOrDefault(emptyList())
            _apps.value = list
            _loading.value = false
            withContext(Dispatchers.IO) { runCatching { backups.writeBaselineIfMissing(list) } }
        }
    }

    fun refreshSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            _sessions.value = backups.listSessions()
            _backupsBytes.value = backups.totalBytes()
        }
    }

    // ------------------------------------------------------------ operations

    fun request(apps: List<AppInfo>, action: Action, dryRun: Boolean) {
        if (apps.isEmpty()) return
        _pending.value = PendingOperation(apps, action, dryRun)
    }

    fun dismissPending() { _pending.value = null }

    fun confirmPending(keepData: Boolean, dryRun: Boolean, action: Action) {
        val p = _pending.value ?: return
        _pending.value = null
        start(p.apps, action, dryRun, keepData)
    }

    private fun start(apps: List<AppInfo>, action: Action, dryRun: Boolean, keepData: Boolean) {
        if (engine.isRunning || cache.busy) return
        lastRequest = LastRequest.Run(apps.map { it.packageName }, action, keepData)
        if (!dryRun) {
            _debloatSelection.value = emptySet()
            _cleanupSelection.value = null
            _managerSelection.value = emptySet()
        }
        if (_backStack.value.last() != Screen.OPERATION) navigate(Screen.OPERATION)
        viewModelScope.launch {
            engine.run(apps, action, dryRun, keepData, prefs.expertMode.value, _apps.value)
            afterOperation()
        }
    }

    /** After a passed test: run the same request for real (goes through confirmation again). */
    fun runTestedForReal() {
        when (val r = lastRequest) {
            is LastRequest.Run -> {
                val byPkg = _apps.value.associateBy { it.packageName }
                val apps = r.apps.mapNotNull { byPkg[it] }
                back() // leave the finished test screen
                _pending.value = PendingOperation(apps, r.action, dryRun = false)
            }
            is LastRequest.Restore -> startRestore(r.items, dryRun = false, title = r.title)
            null -> {}
        }
    }

    fun cancelOperation() = engine.cancel()

    /** Removal failed for some packages (often: not uninstallable on this ROM). Offer to disable them instead. */
    fun retryFailedWithDisable() {
        val failed = engine.state.value?.failedPackages.orEmpty().toSet()
        val apps = _apps.value.filter { it.packageName in failed && it.state == io.github.j0p3s.debloater.data.AppState.ENABLED }
        if (apps.isEmpty()) {
            _message.value = UiMessage(R.string.message_nothing_disable)
            return
        }
        back()
        _pending.value = PendingOperation(apps, Action.DISABLE, dryRun = false)
    }

    fun restoreSession(session: BackupSession, entries: List<BackupEntry>, dryRun: Boolean) {
        val restorable = entries.filter { it.status == EntryStatus.DONE || it.status == EntryStatus.RESTORE_FAILED || it.status == EntryStatus.RESTORED }
        if (restorable.isEmpty()) {
            _message.value = UiMessage(R.string.message_nothing_restore)
            return
        }
        startRestore(restorable.map { RestoreItem(session.id, it) }, dryRun, "Restore ${restorable.size} app(s) from ${session.id}")
    }

    fun restoreBaseline(dryRun: Boolean) {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { engine.baselineRestoreItems(_apps.value) }
            if (items.isEmpty()) {
                _message.value = UiMessage(R.string.message_original_state)
                return@launch
            }
            startRestore(items, dryRun, "Undo all changes (${items.size} app(s))")
        }
    }

    private fun startRestore(items: List<RestoreItem>, dryRun: Boolean, title: String) {
        if (engine.isRunning || cache.busy) return
        lastRequest = LastRequest.Restore(items, title)
        if (_backStack.value.last() != Screen.OPERATION) navigate(Screen.OPERATION)
        viewModelScope.launch {
            engine.restore(items, dryRun, title)
            afterOperation()
        }
    }

    private fun afterOperation() {
        refreshApps()
        refreshSessions()
    }

    // ------------------------------------------------------------ backups

    fun deleteSession(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            backups.deleteSession(id)
            _sessions.value = backups.listSessions()
            _backupsBytes.value = backups.totalBytes()
            _storage.value = runCatching { repo.storageInfo() }.getOrNull()
        }
    }

    fun exportBackups(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _message.value = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { backups.exportZip(it) }
                UiMessage(R.string.message_exported)
            }.getOrElse { UiMessage(R.string.message_export_failed, listOf(it.message.orEmpty())) }
        }
    }

    fun importBackups(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _message.value = runCatching {
                val n = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { backups.importZip(it) }
                UiMessage(R.string.message_imported, listOf(n))
            }.getOrElse { UiMessage(R.string.message_import_failed, listOf(it.message.orEmpty())) }
            _sessions.value = backups.listSessions()
            _backupsBytes.value = backups.totalBytes()
        }
    }

    fun logText(): String = engine.state.value?.let { engine.renderLog(it) }.orEmpty()
}
