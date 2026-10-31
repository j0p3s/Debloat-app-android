package io.github.j0p3s.debloater.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Curated debloat list, loaded from assets/debloat_list.json. */
class DebloatDatabase private constructor(private val entries: Map<String, DebloatEntry>) {

    fun get(packageName: String, sdk: Int): DebloatEntry? = entries[packageName]?.forSdk(sdk)

    val size get() = entries.size

    companion object {
        @Volatile private var instance: DebloatDatabase? = null

        fun get(context: Context): DebloatDatabase = instance ?: synchronized(this) {
            instance ?: load(context.assets.open("debloat_list.json").bufferedReader().use { it.readText() })
                .also { instance = it }
        }

        fun load(json: String): DebloatDatabase {
            val root = JSONObject(json)
            val arr = root.getJSONArray("packages")
            val map = LinkedHashMap<String, DebloatEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val e = parse(arr.getJSONObject(i))
                map[e.packageName] = e
            }
            return DebloatDatabase(map)
        }

        private fun parse(o: JSONObject): DebloatEntry {
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
            )
        }
    }
}
