package io.github.j0p3s.debloater

import io.github.j0p3s.debloater.cache.SettingsCachePolicy as Policy
import org.junit.Assert.*
import org.junit.Test

class SettingsCachePolicyTest {
    @Test fun missingControlContinuesOnlyIfNoCacheClickWasSentAndSettingsRemainsOpen() {
        assertEquals(io.github.j0p3s.debloater.cache.SettingsCacheResult.UNAVAILABLE, Policy.timedOutResult(false, true))
        assertEquals(io.github.j0p3s.debloater.cache.SettingsCacheResult.UNKNOWN, Policy.timedOutResult(true, true))
        assertEquals(io.github.j0p3s.debloater.cache.SettingsCacheResult.UNKNOWN, Policy.timedOutResult(false, false))
    }
    @Test fun storageRowSupportsCustomSettingsTitleAndMissingViewIds() {
        val row = Policy.Node(clickable = true)
        for(id in listOf("com.android.settings:id/title", "")) {
            val title = Policy.Node(id, "Storage & cache", parent = 1)
            assertEquals(1, Policy.storageRow(page(header(), row, title), target, labels))
            assertNull(Policy.storageRow(page(header("Other"), row, title), target, labels))
        }
    }
    private val target = Policy.Target("com.example.target", "Example", true)
    private val labels = Policy.Labels(setOf("Storage & cache"), setOf("Clear cache"), setOf("Clear storage", "Manage space"))
    private fun header(text: String = "Example") = Policy.Node("com.android.settings:id/entity_header_title", text)
    private fun button(text: String, id: String = "button2", enabled: Boolean = true) =
        Policy.Node("com.android.settings:id/$id", text, clickable = true, enabled = enabled)
    private fun page(vararg nodes: Policy.Node) = Policy.Page(Policy.SETTINGS_PACKAGE, nodes.toList())

    @Test fun clearDataBesideCacheIsNeverSelected() {
        assertEquals(2, Policy.clearButton(page(header(), button("Clear storage", "button1"), button("Clear cache")), target, labels))
        assertNull(Policy.clearButton(page(header(), button("Clear storage")), target, labels))
    }
    @Test fun sameTextInAnotherAppNeverQualifies() {
        val fake = page(header(), button("Clear cache")).copy(packageName = "com.example.impostor")
        assertNull(Policy.clearButton(fake, target, labels))
    }
    @Test fun differentAppAndAmbiguousNamesAreRejected() {
        assertNull(Policy.clearButton(page(header("Other"), button("Clear cache")), target, labels))
        assertNull(Policy.clearButton(page(header(), button("Clear cache")), target.copy(uniqueLabel = false), labels))
    }
    @Test fun explicitPackageCanResolveDuplicateLabelsButWrongPackageCannot() {
        val pkg = Policy.Node("com.android.settings:id/package_name", target.packageName)
        assertEquals(2, Policy.clearButton(page(header(), pkg, button("Clear cache")), target.copy(uniqueLabel = false), labels))
        assertNull(Policy.clearButton(page(header(), pkg.copy(text = "com.other.app"), button("Clear cache")), target, labels))
    }
    @Test fun dialogsAreNeverConfirmedEvenWithCacheText() {
        assertNull(Policy.clearButton(page(header(), Policy.Node("android:id/alertTitle", "Clear cache?"), button("Clear cache")), target, labels))
        assertNull(Policy.clearButton(page(header(), Policy.Node("android:id/parentPanel"), button("Clear cache")), target, labels))
    }
    @Test fun labelMustMatchExactlyAndBeFromSettingsResources() {
        assertNull(Policy.clearButton(page(header(), button("Clear cache and data")), target, labels))
        assertNull(Policy.clearButton(page(header(), button("Clear cache")), target, labels.copy(clearCache = emptySet())))
        assertNull(Policy.clearButton(page(header(), button("Clear cache", "unknown_action")), target, labels))
    }
    @Test fun localizedContentDescriptionAndClickableAncestorAreSupported() {
        val parent = button("", "action2")
        val label = Policy.Node(description = "LIMPAR\u00a0CACHE", parent = 1)
        assertEquals(1, Policy.clearButton(page(header(), parent, label), target, labels.copy(clearCache = setOf("Limpar cache"))))
    }
    @Test fun disabledButtonIsObservableButHiddenAndAmbiguousButtonsAreRejected() {
        val disabled = button("Clear cache", enabled = false)
        assertEquals(1, Policy.clearButton(page(header(), disabled), target, labels))
        assertNull(Policy.clearButton(page(header(), disabled.copy(visible = false)), target, labels))
        assertNull(Policy.clearButton(page(header(), disabled, button("Clear cache", "clear_cache")), target, labels))
    }
    @Test fun destructiveResourceCollisionIsRejected() {
        assertNull(Policy.clearButton(page(header(), button("Clear cache")), target, labels.copy(destructive = setOf("Clear cache"))))
    }
    @Test fun storageRequiresTheCorrectAppAndClickablePreferenceRow() {
        val row = Policy.Node(clickable = true)
        val title = Policy.Node("android:id/title", "Storage & cache", parent = 1)
        assertEquals(1, Policy.storageRow(page(header(), row, title), target, labels))
        assertNull(Policy.storageRow(page(header("Other"), row, title), target, labels))
        assertNull(Policy.storageRow(page(header(), row.copy(enabled = false), title), target, labels))
    }
    @Test fun truncatedTreesAndDetachedLabelsFailClosed() {
        assertNull(Policy.clearButton(page(header(), button("Clear cache")).copy(complete = false), target, labels))
        assertNull(Policy.clearButton(page(header(), Policy.Node(text = "Clear cache", parent = 99)), target, labels))
    }
}
