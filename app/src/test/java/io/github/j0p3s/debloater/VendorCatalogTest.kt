package io.github.j0p3s.debloater

import io.github.j0p3s.debloater.data.VendorCatalog
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class VendorCatalogTest {
    private fun detect(manufacturer: String?, brand: String?) = VendorCatalog.detect(manufacturer, brand)?.name

    @Test
    fun detectsBrands() {
        assertEquals("Samsung", detect("samsung", "samsung"))
        assertEquals("Xiaomi / Redmi / POCO", detect("Xiaomi", "Redmi"))
        assertEquals("Xiaomi / Redmi / POCO", detect("Xiaomi", "POCO"))
        assertEquals("Nubia / RedMagic", detect("nubia", "RedMagic"))
        assertEquals("ZTE", detect("ZTE", "ZTE"))
        assertEquals("LG", detect("LGE", "lge"))
        assertEquals("OnePlus / OPPO / Realme", detect("realme", "realme"))
        assertEquals("Nokia / HMD", detect("HMD Global", "Nokia"))
        assertEquals("TECNO / Infinix / itel", detect("TECNO MOBILE LIMITED", "TECNO"))
        assertEquals("vivo / iQOO", detect("vivo", "iQOO"))
        assertEquals("Google", detect("Google", "google"))
        assertNull(detect("Acme", "Acme"))
        assertNull(detect(null, null))
    }

    @Test
    fun shortKeysDoNotMatchPrefixesOfOtherWords() {
        // "lg" must not match "lgx"; "itel" must not match "itelx".
        assertNull(detect("lgx", "lgx"))
        assertNull(detect("itelx", "itelx"))
    }

    @Test
    fun guessesVendorFromPackageName() {
        assertEquals("Samsung", VendorCatalog.vendorOf("com.sec.android.app.foo", null))
        assertEquals("Nubia / RedMagic", VendorCatalog.vendorOf("cn.nubia.something", null))
        assertEquals("ZTE", VendorCatalog.vendorOf("com.zte.something", null))
        assertEquals("Xiaomi / Redmi / POCO", VendorCatalog.vendorOf("com.mi.health", null))
        assertEquals("Microsoft", VendorCatalog.vendorOf("com.microsoft.office.word", null))
        assertEquals(VendorCatalog.OTHER, VendorCatalog.vendorOf("org.example.app", null))
        // A listed vendor wins over the prefix guess.
        assertEquals("vivo / iQOO", VendorCatalog.vendorOf("com.android.bbkmusic", "vivo / iQOO"))
    }

    @Test
    fun everyVendorInTheListIsKnown() {
        val arr = JSONObject(File("src/main/assets/debloat_list.json").readText()).getJSONArray("packages")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            assertNotNull("unknown vendor '${o.getString("vendor")}' for ${o.getString("id")}", VendorCatalog.byName(o.getString("vendor")))
        }
    }
}
