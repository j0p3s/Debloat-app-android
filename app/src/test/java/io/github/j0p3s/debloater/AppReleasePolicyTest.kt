package io.github.j0p3s.debloater
import io.github.j0p3s.debloater.ui.components.AppReleasePolicy
import org.junit.Assert.*
import org.junit.Test
class AppReleasePolicyTest {
    @Test fun fallbackAcceptsOnlyOfficialStableReleaseRedirects() {
        assertEquals("v1.11.2", AppReleasePolicy.redirectedTag("https://github.com/j0p3s/Debloat-app-android/releases/tag/v1.11.2"))
        listOf("https://evil.example/releases/tag/v1.11.2", "https://github.com/other/repo/releases/tag/v1.11.2", "https://github.com/j0p3s/Debloat-app-android/releases/tag/v1.12.0-beta").forEach {
            try { AppReleasePolicy.redirectedTag(it); fail("Unsafe redirect accepted") } catch(_: IllegalArgumentException) { }
        }
    }
    @Test fun versionsCompareNumerically() {
        assertTrue(AppReleasePolicy.newer("v1.12.0", "1.9.9"))
        assertFalse(AppReleasePolicy.newer("v1.11.2", "1.11.2"))
        assertFalse(AppReleasePolicy.newer("v1.10.0", "1.11.2"))
    }
    @Test fun unrecognizedVersionsNeverOfferADownload() {
        listOf("v1.12.0-beta", "../../evil", "v999999999999999.1.1").forEach {
            assertFalse(AppReleasePolicy.newer(it,"1.11.2"))
        }
    }
}
