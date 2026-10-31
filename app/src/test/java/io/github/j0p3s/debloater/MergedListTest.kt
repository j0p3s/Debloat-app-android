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
    private val merged = DebloatDatabase.load(own, read("uad_lists.json"), read("uad_overrides.json"))
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
}
