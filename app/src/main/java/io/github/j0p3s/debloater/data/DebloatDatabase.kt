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
                fun asset(name: String) = runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }.getOrNull()
                val adl = ADL_FILES.mapNotNull { asset("adl/$it.json") }
                val suggestions = runCatching { context.assets.list("adl/suggestions") }.getOrNull().orEmpty()
                    .filter { it.endsWith(".json") }
                    .mapNotNull { f -> asset("adl/suggestions/$f")?.let { f.removeSuffix(".json") to it } }
                    .toMap()
                load(asset("debloat_list.json")!!, asset("uad_lists.json"), asset("uad_overrides.json"), adl, suggestions)
            }.also { instance = it }
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
                for ((pkg, o) in readObjectEntries(uadJson)) {
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
                            description = summaryFrom(details),
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
                            description = summaryFrom(desc),
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
                            description = existing.description.ifBlank { summaryFrom(desc) },
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
                val ov = JSONObject(overridesJson).optJSONObject("overrides") ?: JSONObject()
                val keys = ov.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val kind = ov.optString(key)
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
        private fun readObjectEntries(json: String): List<Pair<String, JSONObject>> {
            val t = JSONTokener(json)
            val out = ArrayList<Pair<String, JSONObject>>(6000)
            if (t.nextClean() != '{') throw JSONException("Expected an object")
            if (t.nextClean() == '}') return out
            t.back()
            while (true) {
                val key = t.nextValue() as? String ?: throw JSONException("Expected a key")
                if (t.nextClean() != ':') throw JSONException("Expected ':' after $key")
                val value = t.nextValue()
                if (value is JSONObject) out += key to value
                when (t.nextClean()) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw JSONException("Expected ',' or '}' after $key")
                }
            }
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
        private fun summaryFrom(details: String?): String {
            if (details == null) return ""
            val lines = details.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("http") }
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
