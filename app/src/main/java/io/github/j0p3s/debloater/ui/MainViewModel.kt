package io.github.j0p3s.debloater.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.j0p3s.debloater.backup.BackupEntry
import io.github.j0p3s.debloater.backup.BackupManager
import io.github.j0p3s.debloater.backup.BackupSession
import io.github.j0p3s.debloater.backup.EntryStatus
import io.github.j0p3s.debloater.data.Action
import io.github.j0p3s.debloater.data.AppInfo
import io.github.j0p3s.debloater.data.DebloatDatabase
import io.github.j0p3s.debloater.data.PackageRepository
import io.github.j0p3s.debloater.data.Prefs
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

enum class Screen { HOME, DEBLOAT, APP_MANAGER, OPERATION, BACKUPS, SETTINGS }

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
    val prefs = Prefs(app)
    val database: DebloatDatabase by lazy { DebloatDatabase.get(app) }

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

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var lastRequest: LastRequest? = null

    /** Selections survive configuration changes because they live here. */
    private val _debloatSelection = MutableStateFlow<Set<String>>(emptySet())
    val debloatSelection: StateFlow<Set<String>> = _debloatSelection.asStateFlow()
    private val _managerSelection = MutableStateFlow<Set<String>>(emptySet())
    val managerSelection: StateFlow<Set<String>> = _managerSelection.asStateFlow()

    fun setDebloatSelection(s: Set<String>) { _debloatSelection.value = s }
    fun setManagerSelection(s: Set<String>) { _managerSelection.value = s }

    init {
        refreshApps()
        refreshSessions()
    }

    // ------------------------------------------------------------ navigation

    fun navigate(screen: Screen) {
        _backStack.value = _backStack.value + screen
    }

    /** Returns false when there is nothing to go back to. */
    fun back(): Boolean {
        val stack = _backStack.value
        if (stack.last() == Screen.OPERATION && engine.isRunning) return true
        if (stack.size <= 1) return false
        if (stack.last() == Screen.OPERATION) engine.clear()
        _backStack.value = stack.dropLast(1)
        return true
    }

    fun consumeMessage() { _message.value = null }

    // ------------------------------------------------------------ data

    fun refreshShizuku() = ShizukuManager.refresh()

    fun refreshApps() {
        viewModelScope.launch {
            _loading.value = true
            val list = runCatching { repo.loadAll() }.getOrDefault(emptyList())
            _apps.value = list
            _loading.value = false
            withContext(Dispatchers.IO) { runCatching { backups.writeBaselineIfMissing(list) } }
        }
    }

    fun refreshSessions() {
        viewModelScope.launch(Dispatchers.IO) { _sessions.value = backups.listSessions() }
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
        if (engine.isRunning) return
        lastRequest = LastRequest.Run(apps.map { it.packageName }, action, keepData)
        if (!dryRun) {
            _debloatSelection.value = emptySet()
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

    fun restoreSession(session: BackupSession, entries: List<BackupEntry>, dryRun: Boolean) {
        val restorable = entries.filter { it.status == EntryStatus.DONE || it.status == EntryStatus.RESTORE_FAILED || it.status == EntryStatus.RESTORED }
        if (restorable.isEmpty()) {
            _message.value = "Nothing to restore in this selection"
            return
        }
        startRestore(restorable.map { RestoreItem(session.id, it) }, dryRun, "Restore ${restorable.size} app(s) from ${session.id}")
    }

    fun restoreBaseline(dryRun: Boolean) {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { engine.baselineRestoreItems(_apps.value) }
            if (items.isEmpty()) {
                _message.value = "Everything already matches the original state"
                return@launch
            }
            startRestore(items, dryRun, "Undo all changes (${items.size} app(s))")
        }
    }

    private fun startRestore(items: List<RestoreItem>, dryRun: Boolean, title: String) {
        if (engine.isRunning) return
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
        }
    }

    fun exportBackups(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _message.value = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { backups.exportZip(it) }
                "Backups exported"
            }.getOrElse { "Export failed: ${it.message}" }
        }
    }

    fun importBackups(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _message.value = runCatching {
                val n = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { backups.importZip(it) }
                "Imported $n file(s)"
            }.getOrElse { "Import failed: ${it.message}" }
            _sessions.value = backups.listSessions()
        }
    }

    fun logText(): String = engine.state.value?.let { engine.renderLog(it) }.orEmpty()
}
