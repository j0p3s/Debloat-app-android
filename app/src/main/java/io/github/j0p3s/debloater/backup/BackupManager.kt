package io.github.j0p3s.debloater.backup

import android.content.Context
import android.os.StatFs
import io.github.j0p3s.debloater.data.Action
import io.github.j0p3s.debloater.data.AppInfo
import io.github.j0p3s.debloater.shizuku.ShizukuManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backups are mandatory. Before any package is disabled or removed, this class:
 *  1. writes a restore point with the state of every package on the device (snapshot.json),
 *  2. records each package's previous state in session.json,
 *  3. copies every APK split of the package into the session folder and checks the size.
 * If step 3 fails for a package, that package is skipped and never modified.
 */
class BackupManager(private val context: Context) {

    val root: File by lazy {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, "backups").apply { mkdirs() }
    }

    val logsDir: File by lazy { File(context.filesDir, "logs").apply { mkdirs() } }

    private fun sessionDir(id: String) = File(root, id)

    fun newSessionId(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())

    fun createSession(action: Action, userId: Int, allApps: List<AppInfo>): BackupSession {
        val session = BackupSession(
            id = newSessionId(),
            createdAt = System.currentTimeMillis(),
            action = action,
            userId = userId,
            device = DeviceInfo.current(),
            entries = mutableListOf(),
        )
        val dir = sessionDir(session.id).apply { mkdirs() }
        writeAtomic(File(dir, SNAPSHOT), snapshotJson(allApps).toString(2))
        save(session)
        return session
    }

    fun save(session: BackupSession) {
        writeAtomic(File(sessionDir(session.id), SESSION), session.toJson().toString(2))
    }

    /** Full-device restore point, written once (the first time the app sees the package list). */
    fun writeBaselineIfMissing(allApps: List<AppInfo>) {
        val f = File(root, BASELINE)
        if (!f.exists() && allApps.isNotEmpty()) writeAtomic(f, snapshotJson(allApps).toString(2))
    }

    private fun snapshotJson(apps: List<AppInfo>) = JSONObject()
        .put("createdAt", System.currentTimeMillis())
        .put("device", DeviceInfo.current().toJson())
        .put("packages", JSONArray().also { a ->
            apps.forEach {
                a.put(
                    JSONObject().put("package", it.packageName).put("label", it.label)
                        .put("system", it.isSystem).put("state", it.state.name)
                        .put("versionCode", it.versionCode),
                )
            }
        })

    /**
     * Copies all APK files of [app] into the session. Tries a direct read first (APKs are
     * normally world-readable) and falls back to reading them as the shell user via Shizuku.
     */
    suspend fun backupApks(session: BackupSession, app: AppInfo): Result<List<File>> {
        if (app.apkPaths.isEmpty()) return Result.failure(IllegalStateException("No APK path known for ${app.packageName}"))
        val dir = File(sessionDir(session.id), "apks/${app.packageName}").apply { mkdirs() }
        val out = mutableListOf<File>()
        for ((index, path) in app.apkPaths.withIndex()) {
            val src = File(path)
            val name = if (index == 0) "base.apk" else src.name.ifBlank { "split_$index.apk" }
            val dest = File(dir, name)
            val expected = runCatching { src.length() }.getOrDefault(0L)
            val direct = runCatching {
                src.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
            }
            val copied = if (direct.isSuccess) dest.length() else ShizukuManager.copyFileAsShell(path, dest)
            if (copied <= 0 || (expected > 0 && copied != expected)) {
                dir.deleteRecursively()
                return Result.failure(IllegalStateException("Could not back up $path (copied $copied of $expected bytes)"))
            }
            out += dest
        }
        return Result.success(out)
    }

    fun apkFilesFor(sessionId: String, entry: BackupEntry): List<File> {
        val dir = File(sessionDir(sessionId), "apks/${entry.packageName}")
        val files = entry.apkFiles.map { File(dir, it) }.filter { it.isFile && it.length() > 0 }
        // base.apk must come first for pm install sessions.
        return files.sortedBy { if (it.name == "base.apk") 0 else 1 }
    }

    /** Looks through every session (newest first) for an APK backup of [packageName]. */
    fun latestApkBackup(packageName: String): List<File> {
        for (s in listSessions()) {
            val e = s.entries.firstOrNull { it.packageName == packageName && it.apkFiles.isNotEmpty() } ?: continue
            val files = apkFilesFor(s.id, e)
            if (files.isNotEmpty() && files.size == e.apkFiles.size) return files
        }
        return emptyList()
    }

    fun listSessions(): List<BackupSession> =
        root.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            runCatching { BackupSession.fromJson(JSONObject(File(dir, SESSION).readText())) }.getOrNull()
        }?.sortedByDescending { it.createdAt }.orEmpty()

    fun sessionSize(id: String): Long = sessionDir(id).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun deleteSession(id: String) {
        sessionDir(id).deleteRecursively()
    }

    fun freeBytes(): Long = runCatching { StatFs(root.absolutePath).availableBytes }.getOrDefault(0L)

    /** Checks that the backup folder can be written to, without leaving anything behind. */
    fun canWrite(): Boolean = runCatching {
        val probe = File(root, ".probe-${System.nanoTime()}")
        probe.writeText("ok")
        val ok = probe.readText() == "ok"
        probe.delete()
        ok
    }.getOrDefault(false)

    fun writeLog(name: String, text: String): File {
        val f = File(logsDir, "$name.txt")
        f.writeText(text)
        return f
    }

    fun listLogs(): List<File> = logsDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()

    /** Writes the whole backup folder (sessions, APKs, snapshots) into a zip. */
    fun exportZip(output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                zip.putNextEntry(ZipEntry(f.relativeTo(root).path.replace(File.separatorChar, '/')))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Imports a zip made by [exportZip]. Existing sessions with the same id are kept. Returns the number of files imported. */
    fun importZip(input: InputStream): Int {
        var count = 0
        val canonicalRoot = root.canonicalFile
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(root, entry.name).canonicalFile
                // Block "zip slip" paths that point outside the backup folder.
                if (!target.path.startsWith(canonicalRoot.path + File.separator)) continue
                if (entry.isDirectory) {
                    target.mkdirs()
                } else if (!target.exists()) {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                    count++
                }
            }
        }
        return count
    }

    private fun writeAtomic(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    companion object {
        const val SESSION = "session.json"
        const val SNAPSHOT = "snapshot.json"
        const val BASELINE = "baseline-snapshot.json"
    }
}
