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

        @Volatile private var instance: DebloatDatabase? = null

        fun get(context: Context): DebloatDatabase = instance ?: synchronized(this) {
            instance ?: run {
                fun asset(name: String) = runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }.getOrNull()
                load(asset("debloat_list.json")!!, asset("uad_lists.json"), asset("uad_overrides.json"))
            }.also { instance = it }
        }

        fun load(ownJson: String, uadJson: String? = null, overridesJson: String? = null): DebloatDatabase {
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

            // 3. Overrides: "Unsafe", "Advanced" or "CantUninstall". Keys ending in "_" are prefixes.
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
