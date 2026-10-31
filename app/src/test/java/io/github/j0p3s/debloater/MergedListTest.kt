package io.github.j0p3s.debloater

import io.github.j0p3s.debloater.data.DebloatDatabase
import io.github.j0p3s.debloater.data.SafetyLevel
import io.github.j0p3s.debloater.data.VendorCatalog
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MergedListTest {
    private fun read(name: String) = File("src/main/assets/$name").readText()
    private val own = read("debloat_list.json")
    private val adl = DebloatDatabase.ADL_FILES.map { read("adl/$it.json") }
    private val suggestions = File("src/main/assets/adl/suggestions").listFiles()!!
        .filter { it.name.endsWith(".json") }.associate { it.name.removeSuffix(".json") to it.readText() }
    private val merged = DebloatDatabase.load(own, read("uad_lists.json"), read("uad_overrides.json"), adl, suggestions)
    private val adlIds = adl.flatMap { json -> org.json.JSONArray(json).let { a -> List(a.length()) { a.getJSONObject(it).getString("id") } } }
    private val ownOnly = DebloatDatabase.load(own)
    // Top-level keys only (the file has a few duplicate keys, which JSONObject rejects).
    private val uadIds = Regex("""(?m)^  "([^"]+)": \{""").findAll(read("uad_lists.json")).map { it.groupValues[1] }.toList()

    @Test
    fun uadKeysWereFound() {
        assertTrue("found ${uadIds.size}", uadIds.size > 5000)
    }

    @Test
    fun mergedListIsMuchLarger() {
        assertTrue("merged size ${merged.size}", merged.size > 5000)
        assertTrue(merged.countFrom(DebloatDatabase.SOURCE_UAD) > 5000)
    }

    @Test
    fun redMagicAndZteAreCovered() {
        val nubia = uadIds.count { it.startsWith("cn.nubia.") || it.startsWith("com.zte.") }
        assertTrue("nubia/zte entries: $nubia", nubia >= 100)
        assertEquals("Nubia / RedMagic", merged.get("cn.nubia.neostore", 34)!!.vendor)
    }

    @Test
    fun mergingNeverLowersOurRating() {
        val arr = JSONObject(own).getJSONArray("packages")
        for (sdk in 30..37) for (i in 0 until arr.length()) {
            val id = arr.getJSONObject(i).getString("id")
            val mine = ownOnly.get(id, sdk)!!.level
            val now = merged.get(id, sdk)!!.level
            assertTrue("$id on $sdk: $mine -> $now", now.ordinal >= mine.ordinal)
        }
    }

    @Test
    fun stricterRatingWins() {
        // Ours: SAFE, UAD-NG: Advanced.
        assertEquals(SafetyLevel.EXTREME, merged.get("com.android.chrome", 34)!!.level)
    }

    @Test
    fun riskyEntriesAlwaysHaveWarnings() {
        for (id in uadIds) {
            val e = merged.get(id, 34) ?: continue
            if (e.level == SafetyLevel.EXTREME || e.level == SafetyLevel.EXPERT || e.level == SafetyLevel.UNSAFE) {
                assertFalse("$id needs a warning", e.warning.isNullOrBlank())
            }
        }
    }

    @Test
    fun overridesApply() {
        assertEquals(SafetyLevel.UNSAFE, merged.get("com.transsion.phonemaster", 34)!!.level)
        assertTrue(merged.get("com.vivo.appstore", 34)!!.oftenCantUninstall)
        assertTrue(DebloatDatabase.SOURCE_SAR in merged.get("com.vivo.appstore", 34)!!.sources)
    }

    @Test
    fun criticalGoogleServicesStayUnsafe() {
        assertEquals(SafetyLevel.UNSAFE, merged.get("com.google.android.gms", 34)!!.level)
    }

    @Test
    fun everyMergedEntryHasAKnownVendor() {
        for (id in uadIds) {
            val v = merged.get(id, 34)?.vendor ?: continue
            assertTrue("$id has vendor $v", v == VendorCatalog.OTHER || VendorCatalog.byName(v) != null)
        }
    }

    @Test
    fun stricterHelper() {
        assertEquals(SafetyLevel.EXTREME, SafetyLevel.stricter(SafetyLevel.SAFE, SafetyLevel.EXTREME))
        assertEquals(SafetyLevel.SAFE, SafetyLevel.stricter(SafetyLevel.UNKNOWN, SafetyLevel.SAFE))
        assertEquals(SafetyLevel.UNSAFE, SafetyLevel.stricter(SafetyLevel.UNSAFE, SafetyLevel.RECOMMENDED))
    }

    @Test
    fun androidDebloatListIsMerged() {
        assertTrue("adl ids ${adlIds.size}", adlIds.size > 4500)
        assertEquals(adlIds.size, merged.countFrom(DebloatDatabase.SOURCE_ADL))
        val e = merged.get("com.facebook.appmanager", 34)!!
        assertTrue(e.inAdl)
        assertEquals("https://adl.muntashir.dev/bloatware/com.facebook.appmanager.html", e.adlUrl)
    }

    @Test
    fun adlLabelsAndAlternatives() {
        val e = merged.get("com.chrome.beta", 34)!!
        assertEquals("Chrome Beta", e.name)
        assertEquals("replace", e.adlRemoval)
        assertTrue("alternatives: ${e.alternatives}", e.alternatives.isNotEmpty())
        assertTrue(e.alternatives.all { it.label.isNotBlank() && it.packageName.isNotBlank() })
    }

    @Test
    fun adlMapping() {
        assertEquals(SafetyLevel.RECOMMENDED, DebloatDatabase.adlLevel("delete"))
        assertEquals(SafetyLevel.SAFE, DebloatDatabase.adlLevel("replace"))
        assertEquals(SafetyLevel.EXTREME, DebloatDatabase.adlLevel("caution"))
        assertEquals(SafetyLevel.UNSAFE, DebloatDatabase.adlLevel("unsafe"))
    }

    @Test
    fun adlEntriesWithRiskHaveWarnings() {
        for (id in adlIds) {
            val e = merged.get(id, 34) ?: continue
            if (e.level == SafetyLevel.EXTREME || e.level == SafetyLevel.EXPERT || e.level == SafetyLevel.UNSAFE) {
                assertFalse("$id needs a warning", e.warning.isNullOrBlank())
            }
        }
    }

    @Test
    fun adlNeverLowersUadRating() {
        val uadOnly = DebloatDatabase.load(own, read("uad_lists.json"), read("uad_overrides.json"))
        for (id in uadIds) {
            val before = uadOnly.get(id, 34) ?: continue
            val after = merged.get(id, 34)!!
            assertTrue("$id ${before.level} -> ${after.level}", after.level.ordinal >= before.level.ordinal)
        }
    }

    @Test
    fun summaryDoesNotRepeatTheName() {
        val e = merged.get("cn.nubia.photoeditor", 34)!!
        assertEquals("Photo Editor", e.name)
        assertFalse("summary: ${e.description}", e.description.startsWith("Photo Editor Photo Editor"))
        assertTrue(e.description.isNotBlank())
    }
}
