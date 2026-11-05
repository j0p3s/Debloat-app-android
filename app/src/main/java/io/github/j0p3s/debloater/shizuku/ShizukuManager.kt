package io.github.j0p3s.debloater.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import io.github.j0p3s.debloater.BuildConfig
import io.github.j0p3s.debloater.IShellService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import io.github.j0p3s.debloater.debug.DebugLog

/** Result of a shell command executed through Shizuku. */
data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    /** `pm` sometimes returns 0 even on failure, so also look at the text. */
    val isSuccess: Boolean
        get() = exitCode == 0 &&
            !stdout.contains("Failure", ignoreCase = false) &&
            !stdout.startsWith("Error") &&
            !stderr.contains("Exception") &&
            !stderr.startsWith("Error")

    val output: String
        get() = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n")
}

enum class ShizukuStatus {
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    OUTDATED,
    READY,
}

object ShizukuManager {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val TAG = "ShizukuManager"
    private const val REQUEST_CODE = 4242

    private lateinit var appContext: Context
    private val _status = MutableStateFlow(ShizukuStatus.NOT_RUNNING)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    /** uid the Shizuku server runs as: 0 = root, 2000 = shell (adb). -1 if unknown. */
    private val _serverUid = MutableStateFlow(-1)
    val serverUid: StateFlow<Int> = _serverUid.asStateFlow()

    @Volatile private var service: IShellService? = null
    @Volatile private var pendingService: CompletableDeferred<IShellService>? = null
    private val bindMutex = Mutex()

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(appContext.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                val s = IShellService.Stub.asInterface(binder)
                service = s
                pendingService?.complete(s)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        refresh()
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { code, _ ->
        if (code == REQUEST_CODE) refresh()
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun isShizukuInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun refresh() {
        _status.value = computeStatus()
        if (_status.value == ShizukuStatus.READY) {
            _serverUid.value = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        }
    }

    private fun computeStatus(): ShizukuStatus {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) return if (isShizukuInstalled()) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
        return try {
            // UserService needs Shizuku API v11+ (server version >= 11).
            if (Shizuku.getVersion() < 11) return ShizukuStatus.OUTDATED
            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuStatus.READY
                Shizuku.shouldShowRequestPermissionRationale() -> ShizukuStatus.PERMISSION_DENIED
                else -> ShizukuStatus.PERMISSION_REQUIRED
            }
        } catch (t: Throwable) {
            Log.w(TAG, "status check failed", t)
            DebugLog.error("shizuku", "Status check failed", t)
            ShizukuStatus.NOT_RUNNING
        }
    }

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure { Log.w(TAG, "requestPermission failed", it); DebugLog.error("shizuku", "requestPermission failed", it) }
    }

    fun isReady() = status.value == ShizukuStatus.READY

    private suspend fun getService(): IShellService {
        service?.let { if (it.asBinder().pingBinder()) return it }
        return bindMutex.withLock {
            service?.let { if (it.asBinder().pingBinder()) return@withLock it }
            check(isReady()) { "Shizuku is not ready (${status.value})" }
            val deferred = CompletableDeferred<IShellService>()
            pendingService = deferred
            withContext(Dispatchers.Main) { Shizuku.bindUserService(userServiceArgs, connection) }
            try {
                withTimeout(15_000) { deferred.await() }
            } finally {
                pendingService = null
            }
        }
    }

    suspend fun exec(command: String): ShellResult = execInternal(command, false)
    suspend fun execCache(command: String): ShellResult = execInternal(command, true)

    suspend fun leftovers(action: String, userId: Int, argument: String = ""): ShellResult =
        record("leftovers", "$action user=$userId $argument".trim(), leftoversRaw(action, userId, argument))

    private suspend fun leftoversRaw(action: String, userId: Int, argument: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val result = getService().leftovers(action, userId, argument)
            ShellResult(result.getInt(ShellService.KEY_EXIT, -1), result.getString(ShellService.KEY_STDOUT).orEmpty(), result.getString(ShellService.KEY_STDERR).orEmpty())
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { ShellResult(-1, "", error.message.orEmpty()) }
    }

    /** Every privileged command and its outcome goes to the debug log (output truncated). */
    private fun record(kind: String, command: String, result: ShellResult): ShellResult {
        // Help text is only used to detect support; log whether --cache-only appears, not the whole page.
        val output = if (command.endsWith(" help")) "(${result.output.length} chars; mentions --cache-only: ${result.output.contains("--cache-only")})" else result.output.take(4000)
        val text = "$kind exit=${result.exitCode}: $command" + output.takeIf { it.isNotBlank() }?.let { "\n" + it }.orEmpty()
        if (result.exitCode == 0) DebugLog.info("shell", text) else DebugLog.warn("shell", text)
        return result
    }

    private suspend fun execInternal(command: String, cache: Boolean): ShellResult =
        record(if (cache) "execCache" else "exec", command, execRaw(command, cache))

    private suspend fun execRaw(command: String, cache: Boolean): ShellResult = withContext(Dispatchers.IO) {
        try {
            val s = getService()
            val b = if (cache) s.execCache(command) else s.exec(command)
            ShellResult(
                b.getInt(ShellService.KEY_EXIT, -1),
                b.getString(ShellService.KEY_STDOUT).orEmpty(),
                b.getString(ShellService.KEY_STDERR).orEmpty(),
            )
        } catch (t: Throwable) {
            ShellResult(-1, "", "Shizuku error: ${t.message ?: t}")
        }
    }

    suspend fun installApks(files: List<java.io.File>, userId: Int): ShellResult =
        record("installApks", files.joinToString { it.name } + " user=$userId", installApksRaw(files, userId))

    private suspend fun installApksRaw(files: List<java.io.File>, userId: Int): ShellResult = withContext(Dispatchers.IO) {
        try {
            val fds = files.map { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }.toTypedArray()
            val b = getService().installApks(fds, files.map { it.length() }.toLongArray(), files.map { it.name }.toTypedArray(), userId)
            fds.forEach { runCatching { it.close() } }
            ShellResult(
                b.getInt(ShellService.KEY_EXIT, -1),
                b.getString(ShellService.KEY_STDOUT).orEmpty(),
                b.getString(ShellService.KEY_STDERR).orEmpty(),
            )
        } catch (t: Throwable) {
            ShellResult(-1, "", "Shizuku error: ${t.message ?: t}")
        }
    }

    /** Copies [path] into [dest] using shell privileges. Returns bytes copied or -1. */
    suspend fun copyFileAsShell(path: String, dest: java.io.File): Long = withContext(Dispatchers.IO) {
        try {
            val pfd = ParcelFileDescriptor.open(
                dest,
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
            )
            val copied = getService().copyFile(path, pfd)
            runCatching { pfd.close() }
            copied.also { DebugLog.info("shell", "copyFile $path -> ${dest.name}: $it bytes") }
        } catch (t: Throwable) {
            DebugLog.error("shell", "copyFile $path failed", t)
            -1L
        }
    }
}
