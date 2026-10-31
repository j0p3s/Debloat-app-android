package io.github.j0p3s.debloater.data

import android.content.Context
import io.github.j0p3s.debloater.BuildConfig
import io.github.j0p3s.debloater.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Downloads the newest debloat lists from their GitHub repositories so they can be updated without
 * a new APK. Files go to a temporary folder first; the whole merged list is loaded and checked, and
 * only then does it replace the current one. A source that fails keeps its previous version.
 * Only these public list files are downloaded; nothing is uploaded.
 */
class ListUpdater(private val context: Context) {

    data class SourceResult(val name: String, val status: Status, val detail: String = "") {
        enum class Status { UPDATED, UNCHANGED, SKIPPED, FAILED }
    }

    data class Report(val results: List<SourceResult>, val packages: Int) {
        val anyUpdated get() = results.any { it.status == SourceResult.Status.UPDATED }
        val summary: String
            get() = results.joinToString("\n") { r ->
                val s = when (r.status) {
                    SourceResult.Status.UPDATED -> "updated"
                    SourceResult.Status.UNCHANGED -> "already up to date"
                    SourceResult.Status.SKIPPED -> "skipped"
                    SourceResult.Status.FAILED -> "failed"
                }
                "${r.name}: $s" + if (r.detail.isNotBlank()) " (${r.detail})" else ""
            }
    }

    private val root = File(context.filesDir, ListFiles.DIR)
    private val meta = File(root, "meta.json")

    /** When the lists were last updated in the app, or null if the built-in lists are used. */
    fun lastUpdated(): Long? = runCatching { JSONObject(meta.readText()).getLong("updatedAt") }.getOrNull()

    fun lastChecked(): Long? = runCatching { JSONObject(meta.readText()).optLong("checkedAt").takeIf { it > 0 } }.getOrNull()

    val usesDownloadedLists: Boolean get() = root.isDirectory && (root.list()?.any { it != "meta.json" } == true)

    /** Removes downloaded lists; the built-in ones are used again. */
    fun reset() {
        root.deleteRecursively()
        DebloatDatabase.reset()
    }

    suspend fun update(progress: (String) -> Unit = {}): Report = withContext(Dispatchers.IO) {
        val current = ListFiles(context, preferDownloaded = true)
        val tmp = File(context.filesDir, ListFiles.DIR + ".tmp").apply { deleteRecursively(); mkdirs() }
        // Start from the current downloaded files so a failed source keeps its previous version.
        if (root.isDirectory) root.copyRecursively(tmp, overwrite = true)
        val results = mutableListOf<SourceResult>()

        fun changed(path: String, text: String) = sha(text) != current.read(path)?.let(::sha)
        fun write(path: String, text: String) = File(tmp, path).apply { parentFile?.mkdirs() }.writeText(text)

        // 1. UAD-NG
        progress("Downloading the UAD-NG list…")
        results += runSource("UAD-NG") {
            val text = download(UAD_URL)
            val n = DebloatDatabase.load("""{"packages":[]}""", uadJson = text).countFrom(DebloatDatabase.SOURCE_UAD)
            if (n < 1000) throw IOException("only $n packages, looks incomplete")
            if (!changed("uad_lists.json", text)) SourceResult("UAD-NG", SourceResult.Status.UNCHANGED, "$n packages")
            else { write("uad_lists.json", text); SourceResult("UAD-NG", SourceResult.Status.UPDATED, "$n packages") }
        }

        // 2. Android Debloat List (+ the suggestion files its entries refer to)
        progress("Downloading the Android Debloat List…")
        results += runSource("Android Debloat List") {
            val files = DebloatDatabase.ADL_FILES.associateWith { download("$ADL_BASE/$it.json") }
            var total = 0
            val suggestionIds = mutableSetOf<String>()
            files.values.forEach { text ->
                val a = JSONArray(text)
                total += a.length()
                for (i in 0 until a.length()) a.optJSONObject(i)?.optString("suggestions")?.takeIf { it.isNotBlank() }?.let { suggestionIds += it }
            }
            if (total < 1000) throw IOException("only $total packages, looks incomplete")
            // Validate actual package records before staging any file from this source.
            val parsed = DebloatDatabase.load("""{"packages":[]}""", adlJsons = files.values.toList())
            if (parsed.countFrom(DebloatDatabase.SOURCE_ADL) < 1000) throw IOException("too few valid ADL packages")
            val staged = files.mapKeys { (name, _) -> "adl/$name.json" }.toMutableMap()
            var missingSuggestions = 0
            progress("Downloading ${suggestionIds.size} suggestion lists…")
            for (id in suggestionIds) {
                if (!PackageRepository.isValidPackageName(id)) continue
                val text = runCatching { download("$ADL_BASE/suggestions/$id.json") }.getOrNull()
                if (text == null) { missingSuggestions++; continue }
                if (runCatching { JSONArray(text) }.isFailure) { missingSuggestions++; continue }
                staged["adl/suggestions/$id.json"] = text
            }
            var anyChange = false
            staged.forEach { (path, text) -> if (changed(path, text)) { write(path, text); anyChange = true } }
            val detail = "$total packages" + if (missingSuggestions > 0) "; $missingSuggestions suggestion lists unavailable (previous copies kept)" else ""
            SourceResult("Android Debloat List", if (anyChange) SourceResult.Status.UPDATED else SourceResult.Status.UNCHANGED, detail)
        }

        // 3. System App Remover overrides
        progress("Downloading System App Remover overrides…")
        results += runSource("System App Remover") {
            val text = download(SAR_URL)
            val n = DebloatDatabase.parseOverrides(text).size
            if (n == 0) throw IOException("no overrides found")
            // Compare by content: the built-in copy is a de-duplicated version of the same data.
            val same = current.read("uad_overrides.json")?.let { DebloatDatabase.parseOverrides(it).toSet() } ==
                DebloatDatabase.parseOverrides(text).toSet()
            if (same) SourceResult("System App Remover", SourceResult.Status.UNCHANGED, "$n overrides")
            else { write("uad_overrides.json", text); SourceResult("System App Remover", SourceResult.Status.UPDATED, "$n overrides") }
        }

        // The app repository is private. Do not send unauthenticated requests that always return 404,
        // or embed credentials in the APK. App-specific rules ship with APK updates.
        results += SourceResult("Debloater list", SourceResult.Status.SKIPPED, context.getString(R.string.own_list_builtin))

        // Validate the whole merged list from the new files before switching.
        progress("Checking the new lists…")
        val candidate = TmpThenAssets(context, tmp)
        val db = try {
            DebloatDatabase.loadFrom(candidate)
        } catch (t: Throwable) {
            tmp.deleteRecursively()
            throw IOException("The downloaded lists could not be loaded: ${t.message}", t)
        }
        if (db.size < 1000) {
            tmp.deleteRecursively()
            throw IOException("The downloaded lists look incomplete (${db.size} packages)")
        }

        val now = System.currentTimeMillis()
        val updated = results.any { it.status == SourceResult.Status.UPDATED }
        val previousUpdate = lastUpdated()
        File(tmp, "meta.json").writeText(
            JSONObject()
                .put("checkedAt", now)
                .put("updatedAt", if (updated) now else (previousUpdate ?: 0L))
                .toString(),
        )
        root.deleteRecursively()
        if (!tmp.renameTo(root)) {
            tmp.copyRecursively(root, overwrite = true)
            tmp.deleteRecursively()
        }
        if (!updated && previousUpdate == null) {
            // Nothing new and nothing downloaded before: keep only the check time.
            root.listFiles()?.filter { it.name != "meta.json" }?.forEach { it.deleteRecursively() }
        }
        DebloatDatabase.reset()
        Report(results, db.size)
    }

    private inline fun runSource(name: String, block: () -> SourceResult): SourceResult =
        try {
            block()
        } catch (t: Throwable) {
            SourceResult(name, SourceResult.Status.FAILED, t.message ?: t.toString())
        }

    /** Reads files from the temporary folder first, then from the APK. */
    private class TmpThenAssets(private val context: Context, private val dir: File) : ListSource {
        override fun read(path: String): String? {
            val f = File(dir, path)
            if (f.isFile) return f.readText()
            return runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
        }

        override fun list(path: String): List<String> =
            (runCatching { context.assets.list(path) }.getOrNull().orEmpty().toList() + File(dir, path).list().orEmpty()).distinct()
    }

    companion object {
        const val UAD_URL =
            "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
        const val ADL_BASE = "https://raw.githubusercontent.com/MuntashirAkon/android-debloat-list/master"
        const val SAR_URL = "https://raw.githubusercontent.com/santhosh-D-subramani/Debloater/main/uad_overrides.json"
        fun download(url: String): String =
            ListDownload.read(url, "Debloater/${BuildConfig.VERSION_NAME} (Android)")

        fun sha(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    }
}
