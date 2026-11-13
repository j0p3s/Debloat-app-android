package io.github.j0p3s.debloater.debug

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.compose.runtime.mutableStateListOf
import io.github.j0p3s.debloater.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Append-only, size-bounded text log with one rotated generation. Pure JVM so it is unit-testable. */
class DebugLogStore(private val dir: File, private val maxBytes: Long = 1_000_000L) {
    val current: File get() = File(dir, "debug.log")
    val previous: File get() = File(dir, "debug.log.1")
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    @Synchronized
    fun append(level: String, tag: String, message: String, now: Long = System.currentTimeMillis()) {
        runCatching {
            dir.mkdirs()
            if (current.length() > maxBytes) {
                previous.delete()
                current.renameTo(previous)
            }
            val body = message.trimEnd().replace("\n", "\n    ")
            current.appendText("${time.format(Date(now))} $level/$tag: $body\n")
        }
    }

    @Synchronized
    fun read(): String = listOf(previous, current).filter { it.isFile }.joinToString("") { runCatching { it.readText() }.getOrDefault("") }

    @Synchronized
    fun size(): Long = previous.length() + current.length()

    @Synchronized
    fun clear() { previous.delete(); current.delete() }
}

/**
 * Process-wide debug log: tool logs (cache, leftovers, operations, access setup), Shizuku commands,
 * user-visible messages, caught errors and crashes. Stays on the device until the user exports it.
 */
object DebugLog {
    private var store: DebugLogStore? = null
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        store = DebugLogStore(File(appContext.filesDir, "debug"))
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            error("crash", "Uncaught exception on thread ${thread.name}", error)
            previousHandler?.uncaughtException(thread, error)
        }
        info("app", "Started Debloater ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) on ${deviceLine()}")
    }

    fun info(tag: String, message: String) = store?.append("I", tag, message)
    fun warn(tag: String, message: String) = store?.append("W", tag, message)
    fun error(tag: String, message: String, error: Throwable? = null) =
        store?.append("E", tag, if (error == null) message else "$message\n${stackTrace(error)}")

    fun size(): Long = store?.size() ?: 0L
    fun clear() { store?.clear(); info("app", "Debug log cleared") }

    private fun stackTrace(error: Throwable): String = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

    private fun deviceLine() = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} " +
        "(API ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}"

    fun fileName(now: Long = System.currentTimeMillis()): String =
        "debloater-debug-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(now))}.txt"

    /** Everything useful for a bug report, in one plain-text file. Blocking: call off the main thread. */
    fun buildReport(context: Context, operationLogs: List<File>, extra: String): String = buildString {
        appendLine("Debloater debug report")
        appendLine("Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date())}")
        appendLine("App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Device: ${deviceLine()}")
        appendLine("Fingerprint: ${Build.FINGERPRINT}")
        appendLine("Locale: ${Locale.getDefault().toLanguageTag()}")
        appendLine(extra.trimEnd())
        section("Debug log (tools, commands, messages, errors, crashes)")
        append(store?.read().orEmpty().ifEmpty { "(empty)\n" })
        section("Operation logs (newest first, up to 20)")
        if (operationLogs.isEmpty()) appendLine("(none)")
        operationLogs.take(20).forEach { file ->
            appendLine("----- ${file.name} -----")
            appendLine(runCatching { file.readText() }.getOrElse { "(unreadable: ${it.message})" }.trimEnd())
        }
        section("Logcat of the app process")
        appendLine(readLogcat().trimEnd())
    }

    private fun StringBuilder.section(title: String) { appendLine(); appendLine("===== $title ====="); }

    private fun readLogcat(): String = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "3000", "--pid=${android.os.Process.myPid()}")
            .redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = Thread { runCatching { process.inputStream.bufferedReader().forEachLine { output.appendLine(it) } } }
        reader.start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroy()
        reader.join(2_000)
        synchronized(output) { output.toString() }.ifBlank { "(empty)" }
    }.getOrElse { "(logcat unavailable: ${it.message})" }

    /** Writes [text] as a new file in a folder chosen with ACTION_OPEN_DOCUMENT_TREE. Returns the file name. */
    fun saveToTree(context: Context, tree: Uri, text: String): String {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = fileName()
        val doc = DocumentsContract.createDocument(resolver, parent, "text/plain", name)
            ?: throw IllegalStateException("Could not create $name in the chosen folder")
        writeTo(context, doc, text)
        return name
    }

    fun writeTo(context: Context, uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: throw IllegalStateException("Could not open the chosen file")
    }

    /** A list that shows in the UI as before and is also copied to the debug log. */
    class Mirror(private val tag: String) : AbstractList<String>() {
        private val lines = mutableStateListOf<String>()
        override val size: Int get() = lines.size
        override fun get(index: Int): String = lines[index]
        operator fun plusAssign(line: String) {
            lines += line
            if (line.startsWith("FAIL") || line.startsWith("UNKNOWN") || line.contains("Exception")) warn(tag, line) else info(tag, line)
        }
        fun clear() = lines.clear()
    }
}
