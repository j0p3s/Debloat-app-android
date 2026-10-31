package io.github.j0p3s.debloater

import io.github.j0p3s.debloater.data.DebloatDatabase
import io.github.j0p3s.debloater.data.PackageRepository
import io.github.j0p3s.debloater.data.SafetyLevel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DebloatListTest {
    private val json = File("src/main/assets/debloat_list.json").readText()

    @Test
    fun listParsesAndHasNoDuplicates() {
        val arr = JSONObject(json).getJSONArray("packages")
        val ids = (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
        assertEquals("duplicate ids", ids.size, ids.toSet().size)
        assertEquals(ids.size, DebloatDatabase.load(json).size)
    }

    @Test
    fun everyPackageNameIsValidForShellUse() {
        val arr = JSONObject(json).getJSONArray("packages")
        for (i in 0 until arr.length()) {
            val id = arr.getJSONObject(i).getString("id")
            assertTrue("invalid package name: $id", PackageRepository.isValidPackageName(id))
        }
    }

    @Test
    fun extremeAndUnsafeEntriesAlwaysHaveWarnings() {
        val db = DebloatDatabase.load(json)
        val arr = JSONObject(json).getJSONArray("packages")
        for (sdk in 30..37) {
            for (i in 0 until arr.length()) {
                val e = db.get(arr.getJSONObject(i).getString("id"), sdk)!!
                if (e.level == SafetyLevel.EXTREME || e.level == SafetyLevel.UNSAFE) {
                    assertFalse("${e.packageName} needs a warning on API $sdk", e.warning.isNullOrBlank())
                }
            }
        }
    }

    @Test
    fun sdkOverridesApply() {
        val db = DebloatDatabase.load(json)
        assertEquals(SafetyLevel.SAFE, db.get("com.google.android.healthconnect.controller", 33)!!.level)
        assertEquals(SafetyLevel.EXTREME, db.get("com.google.android.healthconnect.controller", 34)!!.level)
    }

    @Test
    fun criticalPackagesAreNotRecommendedOrSafe() {
        val db = DebloatDatabase.load(json)
        listOf("com.google.android.gms", "com.google.android.gsf", "com.android.carrierconfig").forEach {
            assertEquals(it, SafetyLevel.UNSAFE, db.get(it, 34)!!.level)
        }
    }

    @Test
    fun packageNameValidatorRejectsShellInjection() {
        assertTrue(PackageRepository.isValidPackageName("com.example.app_1"))
        listOf("", "com.x; reboot", "com.x && rm -rf /", "com.x\$(id)", "a b", "com..x", ".com.x", "com.x'").forEach {
            assertFalse("should reject '$it'", PackageRepository.isValidPackageName(it))
        }
    }
}
