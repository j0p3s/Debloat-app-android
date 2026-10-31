package io.github.j0p3s.debloater.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * The debloat list. It merges three sources:
 *
 *  1. `debloat_list.json`: written for this app (short descriptions, warnings, per-Android-version rules),
 *  2. `uad_lists.json`: Universal Android Debloater Next Generation (GPL-3.0), about 5,000 packages
 *     with detailed descriptions and dependencies,
 *  3. `uad_overrides.json`: corrections from System App Remover (GPL-3.0).
 *
 * When sources disagree the more cautious rating wins, so merging can only make the app safer.
 */
class DebloatDatabase private constructor(private val entries: Map<String, DebloatEntry>) {

    fun get(packageName: String, sdk: Int): DebloatEntry? = entries[packageName]?.forSdk(sdk)

    val size get() = entries.size

    fun countFrom(source: String) = entries.values.count { source in it.sources }

    companion object {
        const val SOURCE_OWN = "Debloater"
        const val SOURCE_UAD = "UAD-NG"
        const val SOURCE_SAR = "System App Remover"
        const val SOURCE_ADL = "Android Debloat List"

        /** ADL list files in assets/adl. */
        val ADL_FILES = listOf("aosp", "carrier", "google", "misc", "oem")

        @Volatile private var instance: DebloatDatabase? = null

        fun get(context: Context): DebloatDatabase = instance ?: synchronized(this) {
            instance ?: run {
                // Lists downloaded in the app (Settings > Update lists) win over the built-in ones.
                val downloaded = runCatching { loadFrom(ListFiles(context, preferDownloaded = true)) }.getOrNull()
                downloaded ?: loadFrom(ListFiles(context, preferDownloaded = false))
            }.also { instance = it }
        }

        /** Forget the loaded list, e.g. after an update, so the next [get] reads the files again. */
        fun reset() {
            synchronized(this) { instance = null }
        }

        /** Loads all sources through [files] (path relative to the list root, e.g. "adl/oem.json"). */
        fun loadFrom(files: ListSource): DebloatDatabase {
            val adl = ADL_FILES.mapNotNull { files.read("adl/$it.json") }
            val suggestions = files.list("adl/suggestions").filter { it.endsWith(".json") }
                .mapNotNull { f -> files.read("adl/suggestions/$f")?.let { f.removeSuffix(".json") to it } }
                .toMap()
            return load(
                files.read("debloat_list.json") ?: error("debloat_list.json missing"),
                files.read("uad_lists.json"), files.read("uad_overrides.json"), adl, suggestions,
            )
        }

        fun load(
            ownJson: String,
            uadJson: String? = null,
            overridesJson: String? = null,
            adlJsons: List<String> = emptyList(),
            adlSuggestions: Map<String, String> = emptyMap(),
        ): DebloatDatabase {
            val map = LinkedHashMap<String, DebloatEntry>()

            // 1. Our own list.
            val arr = JSONObject(ownJson).getJSONArray("packages")
            for (i in 0 until arr.length()) {
                val e = parseOwn(arr.getJSONObject(i))
                map[e.packageName] = e
            }

            // 2. UAD-NG: {"package.name": {list, description, dependencies, neededBy, labels, removal}}
            if (uadJson != null) {
                for ((pkg, value) in readObjectEntries(uadJson)) {
                    val o = value as? JSONObject ?: continue
                    if (!PackageRepository.isValidPackageName(pkg)) continue
                    val level = uadLevel(o.optString("removal")) ?: continue
                    val details = o.optString("description").trim().ifBlank { null }
                    val neededBy = o.optJSONArray("neededBy").strings()
                    val deps = o.optJSONArray("dependencies").strings()
                    val existing = map[pkg]
                    map[pkg] = if (existing == null) {
                        DebloatEntry(
                            packageName = pkg,
                            name = nameFrom(details, pkg),
                            vendor = vendorFor(pkg, o.optString("list")),
                            level = level,
                            description = summaryFrom(details, pkg),
                            warning = uadWarning(level),
                            details = details,
                            neededBy = neededBy,
                            dependencies = deps,
                            sources = listOf(SOURCE_UAD),
                        )
                    } else {
                        val merged = SafetyLevel.stricter(existing.level, level)
                        existing.copy(
                            level = merged,
                            warning = existing.warning ?: uadWarning(merged),
                            details = details,
                            neededBy = neededBy,
                            dependencies = deps,
                            sources = existing.sources + SOURCE_UAD,
                        )
                    }
                }
            }

            // 3. Android Debloat List (AGPL-3.0): [{id, label, description, removal, warning, web, suggestions, …}]
            val alternatives = adlSuggestions.mapValues { (_, json) -> parseAlternatives(json) }
            for (json in adlJsons) {
                val list = JSONArray(json)
                for (i in 0 until list.length()) {
                    val o = list.optJSONObject(i) ?: continue
                    val pkg = o.optString("id")
                    if (!PackageRepository.isValidPackageName(pkg)) continue
                    val removal = o.optString("removal")
                    val level = adlLevel(removal) ?: continue
                    val label = o.optString("label").trim().ifBlank { null }
                    val warning = o.optString("warning").trim().ifBlank { null }
                    val desc = o.optString("description").trim().ifBlank { null }
                    val web = o.optJSONArray("web").strings()
                    val alts = o.optString("suggestions").ifBlank { null }?.let { alternatives[it] }.orEmpty()
                    val existing = map[pkg]
                    map[pkg] = if (existing == null) {
                        DebloatEntry(
                            packageName = pkg,
                            name = label ?: nameFrom(desc, pkg),
                            vendor = vendorFor(pkg, ""),
                            level = level,
                            description = summaryFrom(desc, pkg),
                            warning = warning ?: adlWarning(level),
                            details = desc,
                            neededBy = o.optJSONArray("required_by").strings(),
                            dependencies = o.optJSONArray("dependencies").strings(),
                            sources = listOf(SOURCE_ADL),
                            adlRemoval = removal,
                            webLinks = web,
                            alternatives = alts,
                        )
                    } else {
                        val merged = SafetyLevel.stricter(existing.level, level)
                        existing.copy(
                            // ADL labels are real app names; UAD-only entries only had a guess.
                            name = if (SOURCE_OWN in existing.sources || label == null) existing.name else label,
                            level = merged,
                            // Keep our hand-written warnings; otherwise the warning must match the final rating.
                            warning = when {
                                SOURCE_OWN in existing.sources && existing.warning != null -> existing.warning
                                merged != existing.level -> warning ?: adlWarning(merged) ?: existing.warning
                                else -> existing.warning ?: warning ?: adlWarning(merged)
                            },
                            details = existing.details ?: desc,
                            description = existing.description.ifBlank { summaryFrom(desc, pkg) },
                            neededBy = (existing.neededBy + o.optJSONArray("required_by").strings()).distinct(),
                            dependencies = (existing.dependencies + o.optJSONArray("dependencies").strings()).distinct(),
                            sources = existing.sources + SOURCE_ADL,
                            adlRemoval = removal,
                            webLinks = web,
                            alternatives = alts,
                        )
                    }
                }
            }

            // 4. Overrides: "Unsafe", "Advanced" or "CantUninstall". Keys ending in "_" are prefixes.
            if (overridesJson != null) {
                for ((key, kind) in parseOverrides(overridesJson)) {
                    val targets = if (key.endsWith("_")) map.keys.filter { it.startsWith(key) } else listOf(key)
                    for (pkg in targets) {
                        if (!PackageRepository.isValidPackageName(pkg)) continue
                        val base = map[pkg] ?: DebloatEntry(
                            pkg, pkg, VendorCatalog.vendorOf(pkg, null), SafetyLevel.UNKNOWN, "", null,
                        )
                        map[pkg] = when (kind) {
                            "Unsafe" -> base.copy(level = SafetyLevel.UNSAFE, warning = base.warning ?: uadWarning(SafetyLevel.UNSAFE))
                            "Advanced" -> SafetyLevel.stricter(base.level, SafetyLevel.EXTREME).let {
                                base.copy(level = it, warning = base.warning ?: uadWarning(it))
                            }
                            "CantUninstall" -> base.copy(oftenCantUninstall = true)
                            else -> base
                        }.let { if (SOURCE_SAR in it.sources) it else it.copy(sources = it.sources + SOURCE_SAR) }
                    }
                }
                // Overrides that only mark CantUninstall must not invent a rating for unknown packages.
                map.entries.removeAll { it.value.level == SafetyLevel.UNKNOWN }
            }
            return DebloatDatabase(map)
        }

        /**
         * Reads the top-level object entry by entry. Unlike JSONObject(String) this accepts duplicate
         * keys (UAD-NG's file has a few): every occurrence is returned and merged with the stricter rating.
         */
        /** Number of top-level entries in a JSON object, duplicates included. */
        fun countEntries(json: String): Int = readObjectEntries(json).size

        private fun readObjectEntries(json: String): List<Pair<String, Any>> {
            val t = JSONTokener(json)
            val out = ArrayList<Pair<String, Any>>(6000)
            if (t.nextClean() != '{') throw JSONException("Expected an object")
            if (t.nextClean() == '}') return out
            t.back()
            while (true) {
                val key = t.nextValue() as? String ?: throw JSONException("Expected a key")
                if (t.nextClean() != ':') throw JSONException("Expected ':' after $key")
                out += key to t.nextValue()
                when (t.nextClean()) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw JSONException("Expected ',' or '}' after $key")
                }
            }
        }

        /**
         * Overrides come either wrapped ({"overrides": {...}}, the bundled copy) or as the flat object
         * published by System App Remover, which has duplicate keys. Both are accepted.
         */
        fun parseOverrides(json: String): List<Pair<String, String>> {
            val entries = readObjectEntries(json)
            val wrapped = entries.firstOrNull { it.first == "overrides" }?.second as? JSONObject
            if (wrapped != null) return wrapped.keys().asSequence().map { it to wrapped.optString(it) }.toList()
            return entries.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.distinctBy { it.first }
        }

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else List(length()) { optString(it) }.filter { it.isNotBlank() }

        /** UAD-NG removal ratings mapped onto ours. */
        fun uadLevel(removal: String): SafetyLevel? = when (removal) {
            "Recommended" -> SafetyLevel.RECOMMENDED
            "Advanced" -> SafetyLevel.EXTREME
            "Expert" -> SafetyLevel.EXPERT
            "Unsafe" -> SafetyLevel.UNSAFE
            else -> null
        }

        /** Android Debloat List removal values mapped onto ours. */
        fun adlLevel(removal: String): SafetyLevel? = when (removal) {
            "delete" -> SafetyLevel.RECOMMENDED
            "replace" -> SafetyLevel.SAFE
            "caution" -> SafetyLevel.EXTREME
            "unsafe" -> SafetyLevel.UNSAFE
            else -> null
        }

        private fun adlWarning(level: SafetyLevel): String? = when (level) {
            SafetyLevel.EXTREME -> "Marked Caution by the Android Debloat List: it might not be safe to remove."
            SafetyLevel.UNSAFE -> "Marked Unsafe by the Android Debloat List."
            else -> null
        }

        private fun parseAlternatives(json: String): List<Alternative> = runCatching {
            val a = JSONArray(json)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                Alternative(
                    packageName = o.optString("id"),
                    label = o.optString("label"),
                    source = o.optString("source"),
                    repo = o.optString("repo").ifBlank { null },
                    reason = o.optString("reason").ifBlank { null },
                )
            }
        }.getOrDefault(emptyList())

        private fun uadWarning(level: SafetyLevel): String? = when (level) {
            SafetyLevel.EXTREME -> "Rated Advanced by UAD-NG: removing it can break related features. Read the details first."
            SafetyLevel.EXPERT -> "Rated Expert by UAD-NG: can break important features. Only for experienced users; prefer Disable."
            SafetyLevel.UNSAFE -> "Rated Unsafe: can cause boot loops or a broken system."
            else -> null
        }

        /** First line of the UAD description when it looks like an app name. */
        private fun nameFrom(details: String?, pkg: String): String {
            val first = details?.lineSequence()?.firstOrNull()?.trim().orEmpty()
            return if (first.isNotEmpty() && first.length <= 48 && !first.endsWith(".") && !first.startsWith("http")) first else pkg
        }

        /** A short summary for list rows: the first sentence or two lines, at most 220 characters. */
        private fun summaryFrom(details: String?, pkg: String = ""): String {
            if (details == null) return ""
            var lines = details.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("http") }
            // When the first line is the app name (used as the title), don't repeat it in the summary.
            if (lines.size > 1 && nameFrom(details, pkg) == lines.first()) lines = lines.drop(1)
            val text = lines.take(2).joinToString(" ")
            return if (text.length > 220) text.take(217).trimEnd() + "…" else text
        }

        private fun vendorFor(pkg: String, list: String): String {
            val guess = VendorCatalog.vendorOf(pkg, null)
            if (guess != VendorCatalog.OTHER) return guess
            return when (list) {
                "Google" -> "Google"
                "Aosp" -> "AOSP"
                "Carrier" -> "Carrier"
                else -> VendorCatalog.OTHER
            }
        }

        private fun parseOwn(o: JSONObject): DebloatEntry {
            val overrides = mutableListOf<SdkOverride>()
            o.optJSONArray("sdk")?.let { a: JSONArray ->
                for (j in 0 until a.length()) {
                    val so = a.getJSONObject(j)
                    overrides += SdkOverride(
                        fromSdk = so.optInt("from", 0),
                        toSdk = if (so.has("to")) so.getInt("to") else null,
                        level = SafetyLevel.valueOf(so.getString("level")),
                        warning = so.optString("warn").ifBlank { null },
                    )
                }
            }
            return DebloatEntry(
                packageName = o.getString("id"),
                name = o.getString("name"),
                vendor = o.optString("vendor", "Other"),
                level = SafetyLevel.valueOf(o.getString("level")),
                description = o.optString("desc"),
                warning = o.optString("warn").ifBlank { null },
                overrides = overrides,
                sources = listOf(SOURCE_OWN),
            )
        }
    }
}

/** Where list files come from: built-in assets, or files downloaded in the app. */
interface ListSource {
    fun read(path: String): String?
    fun list(dir: String): List<String>
}

/**
 * Built-in lists live in the APK assets; updated lists are downloaded to files/lists with the same paths.
 * With [preferDownloaded] a downloaded file is used when present, otherwise the built-in one.
 */
class ListFiles(private val context: Context, private val preferDownloaded: Boolean) : ListSource {
    private val root get() = java.io.File(context.filesDir, ListFiles.DIR)

    override fun read(path: String): String? {
        if (preferDownloaded) {
            val f = java.io.File(root, path)
            if (f.isFile) return runCatching { f.readText() }.getOrNull()
        }
        return runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
    }

    override fun list(dir: String): List<String> {
        val bundled = runCatching { context.assets.list(dir) }.getOrNull().orEmpty().toList()
        val downloaded = if (preferDownloaded) java.io.File(root, dir).list().orEmpty().toList() else emptyList()
        return (bundled + downloaded).distinct()
    }

    companion object {
        const val DIR = "lists"
    }
}
