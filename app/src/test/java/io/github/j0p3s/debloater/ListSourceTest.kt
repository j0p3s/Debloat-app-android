package io.github.j0p3s.debloater

import io.github.j0p3s.debloater.data.DebloatDatabase
import io.github.j0p3s.debloater.data.ListSource
import io.github.j0p3s.debloater.data.SafetyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ListSourceTest {
    /** Reads list files from the assets folder, like the app does from the APK. */
    private class Dir(val root: File, val overrides: Map<String, String> = emptyMap()) : ListSource {
        override fun read(path: String) = overrides[path] ?: File(root, path).takeIf { it.isFile }?.readText()
        override fun list(dir: String) = File(root, dir).list().orEmpty().toList()
    }

    private val assets = File("src/main/assets")

    @Test
    fun loadsEverythingThroughAListSource() {
        val db = DebloatDatabase.loadFrom(Dir(assets))
        assertTrue(db.size > 5000)
        assertTrue(db.countFrom(DebloatDatabase.SOURCE_ADL) > 4500)
        assertTrue(db.get("com.chrome.beta", 34)!!.alternatives.isNotEmpty())
    }

    @Test
    fun acceptsTheFlatOverridesFormatWithDuplicateKeys() {
        // The format published by System App Remover: a flat object with repeated keys.
        val flat = """{"com.transsion.phonemaster": "Unsafe", "com.vivo.appstore": "CantUninstall", "com.transsion.phonemaster": "Unsafe"}"""
        val parsed = DebloatDatabase.parseOverrides(flat)
        assertEquals(listOf("com.transsion.phonemaster" to "Unsafe", "com.vivo.appstore" to "CantUninstall"), parsed)
        val db = DebloatDatabase.loadFrom(Dir(assets, mapOf("uad_overrides.json" to flat)))
        assertEquals(SafetyLevel.UNSAFE, db.get("com.transsion.phonemaster", 34)!!.level)
        assertTrue(db.get("com.vivo.appstore", 34)!!.oftenCantUninstall)
    }

    @Test
    fun wrappedAndFlatOverridesAreEquivalent() {
        val wrapped = File(assets, "uad_overrides.json").readText()
        val flat = "{" + DebloatDatabase.parseOverrides(wrapped).joinToString(",") { "\"${it.first}\": \"${it.second}\"" } + "}"
        assertEquals(DebloatDatabase.parseOverrides(wrapped).toSet(), DebloatDatabase.parseOverrides(flat).toSet())
    }

    @Test(expected = IllegalStateException::class)
    fun missingOwnListIsAnError() {
        DebloatDatabase.loadFrom(Dir(File("does-not-exist")))
    }

    @Test
    fun countEntriesCountsDuplicates() {
        assertEquals(3, DebloatDatabase.countEntries("""{"a": {}, "b": {}, "a": {}}"""))
    }
}
