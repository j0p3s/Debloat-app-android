package io.github.j0p3s.debloater.shizuku

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import io.github.j0p3s.debloater.IShellService
import io.github.j0p3s.debloater.leftovers.LeftoverStore
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Shizuku user service. Shizuku starts this class in a separate process that runs with
 * the shell (adb) or root identity, which is what allows `pm disable-user`,
 * `pm uninstall --user` and so on.
 */
class ShellService : IShellService.Stub {

    @Keep
    constructor() : super()

    @Keep
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : super()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exit() {
        destroy()
    }

    override fun exec(command: String): Bundle = run(command, null)
    override fun execCache(command: String): Bundle = run(command, null, 15L)

    @Synchronized
    override fun leftovers(action: String, userId: Int, argument: String): Bundle = try {
        require(userId in 0..9999) { "Invalid Android user" }
        val store = LeftoverStore(File("/storage/emulated/$userId")) {
            // Include other users, disabled packages and retained/uninstalled package records.
            val inventory = run("pm list packages -u --user all", null, 15L)
            check(inventory.getInt(KEY_EXIT, -1) == 0 && inventory.getString(KEY_STDERR).isNullOrBlank()) { "Cannot verify package owners" }
            val lines = inventory.getString(KEY_STDOUT).orEmpty().lineSequence().filter { it.isNotBlank() }.toList()
            check(lines.isNotEmpty() && lines.all { it.startsWith("package:") && Regex("[A-Za-z0-9_.]+").matches(it.removePrefix("package:")) }) {
                "Incomplete package inventory; refusing cleanup"
            }
            lines.map { it.removePrefix("package:") }.toSet().also { check("android" in it) { "Missing system package inventory" } }
        }
        val output = when(action) {
            "scan" -> store.scan().toString()
            "recovery" -> store.recoveryList().toString()
            "quarantine" -> store.quarantine(argument)
            "restore" -> { store.restore(argument); "Restored without overwriting files" }
            "purge" -> { store.purge(argument); "Permanently deleted recovery payload" }
            else -> error("Unknown leftovers action")
        }
        result(0, output, "")
    } catch (error: Exception) { result(1, "", error.message.orEmpty()) }

    override fun installApks(
        apks: Array<ParcelFileDescriptor>,
        sizes: LongArray,
        names: Array<String>,
        userId: Int,
    ): Bundle {
        val log = StringBuilder()
        val create = run("pm install-create -r --user $userId", null)
        log.appendLine(create.getString(KEY_STDOUT)).appendLine(create.getString(KEY_STDERR))
        val session = Regex("""\[(\d+)]""").find(create.getString(KEY_STDOUT).orEmpty())?.groupValues?.get(1)
        if (session == null) {
            apks.forEach { runCatching { it.close() } }
            return result(1, log.toString(), "Could not create install session")
        }
        try {
            apks.forEachIndexed { i, pfd ->
                val name = names[i].replace(Regex("[^A-Za-z0-9_.-]"), "_")
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                    val r = run("pm install-write -S ${sizes[i]} $session $name -", input)
                    log.appendLine(r.getString(KEY_STDOUT)).appendLine(r.getString(KEY_STDERR))
                    if (r.getInt(KEY_EXIT) != 0) {
                        run("pm install-abandon $session", null)
                        return result(r.getInt(KEY_EXIT), log.toString(), "install-write failed for $name")
                    }
                }
            }
            val commit = run("pm install-commit $session", null)
            log.appendLine(commit.getString(KEY_STDOUT)).appendLine(commit.getString(KEY_STDERR))
            val ok = commit.getInt(KEY_EXIT) == 0 && commit.getString(KEY_STDOUT).orEmpty().contains("Success")
            return result(if (ok) 0 else maxOf(1, commit.getInt(KEY_EXIT)), log.toString(), if (ok) "" else "install-commit failed")
        } catch (t: Throwable) {
            run("pm install-abandon $session", null)
            return result(1, log.toString(), t.toString())
        }
    }

    override fun copyFile(path: String, dest: ParcelFileDescriptor): Long {
        return try {
            FileInputStream(File(path)).use { input ->
                ParcelFileDescriptor.AutoCloseOutputStream(dest).use { output -> input.copyTo(output) }
            }
        } catch (t: Throwable) {
            runCatching { dest.close() }
            -1L
        }
    }

    private fun run(command: String, stdin: InputStream?, timeoutSeconds: Long = COMMAND_TIMEOUT_SECONDS): Bundle {
        return try {
            val process = ProcessBuilder("sh", "-c", command).start()
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val readFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
            // Killing a timed-out process closes its pipes. Never let reader exceptions
            // escape a worker thread and crash the entire privileged service.
            val outReader = thread {
                CommandOutputReader.capture(process.inputStream, stdout, readFailure)
            }
            val errReader = thread {
                CommandOutputReader.capture(process.errorStream, stderr, readFailure)
            }
            process.outputStream.use { os -> stdin?.copyTo(os) }
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                outReader.join(1000); errReader.join(1000)
                return result(124, stdout.toString(), "Timed out after ${timeoutSeconds}s; outcome unknown\n$stderr")
            }
            outReader.join(); errReader.join()
            readFailure.get()?.let { return result(-1, stdout.toString(), "Command output read failed: ${it.message}") }
            result(process.exitValue(), stdout.toString(), stderr.toString())
        } catch (t: Throwable) {
            result(-1, "", t.toString())
        }
    }

    private fun result(code: Int, out: String, err: String) = Bundle().apply {
        putInt(KEY_EXIT, code)
        putString(KEY_STDOUT, out.trim())
        putString(KEY_STDERR, err.trim())
    }

    companion object {
        const val KEY_EXIT = "exitCode"
        const val KEY_STDOUT = "stdout"
        const val KEY_STDERR = "stderr"
        private const val COMMAND_TIMEOUT_SECONDS = 180L
    }
}
