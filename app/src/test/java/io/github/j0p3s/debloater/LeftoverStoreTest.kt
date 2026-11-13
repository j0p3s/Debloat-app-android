package io.github.j0p3s.debloater

import io.github.j0p3s.debloater.leftovers.LeftoverStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class LeftoverStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun fixture(): Pair<File, LeftoverStore> {
        val root = temporary.newFolder()
        listOf("data", "obb", "media").forEach { File(root, "Android/$it").mkdirs() }
        return root to LeftoverStore(root) { setOf("com.installed.app", "com.disabled.app", "com.retained.app", "android") }
    }
    private fun folder(root: File, key: String, text: String = "personal file") = File(root, "Android/$key").apply {
        mkdirs(); File(this, "keep.txt").writeText(text)
    }
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Unsafe operation should have failed") } catch (_: IllegalArgumentException) { } catch (_: IllegalStateException) { }
    }

    @Test fun scanKeepsInstalledDisabledAndRetainedOwnersAndIgnoresGenericFolders() {
        val (root, store) = fixture()
        listOf("data/com.installed.app", "obb/com.disabled.app", "media/com.retained.app", "data/Downloads").forEach { folder(root, it) }
        folder(root, "media/com.uninstalled.app", "abc")
        val rows = store.scan().getJSONArray("candidates")
        assertEquals(1, rows.length())
        assertEquals("media/com.uninstalled.app", rows.getJSONObject(0).getString("key"))
        assertEquals(3, rows.getJSONObject(0).getLong("bytes"))
    }
    @Test fun quarantinePersistsRecoveryAndRestorePreservesAllContents() {
        val (root, store) = fixture()
        val original = folder(root, "data/com.old.app")
        val id = store.quarantine("data/com.old.app")
        assertFalse(original.exists())
        assertEquals(id, store.recoveryList().getJSONObject(0).getString("id"))
        // Reconstruct the store to simulate process death/restart.
        LeftoverStore(root) { setOf("android") }.restore(id)
        assertEquals("personal file", File(original, "keep.txt").readText())
        assertEquals(0, store.recoveryList().length())
    }
    @Test fun ownershipIsRecheckedImmediatelyBeforeQuarantine() {
        val root = temporary.newFolder()
        folder(root, "data/com.reinstalled.app")
        var owners = setOf("android")
        val store = LeftoverStore(root) { owners }
        assertEquals(1, store.scan().getJSONArray("candidates").length())
        owners = owners + "com.reinstalled.app"
        rejects { store.quarantine("data/com.reinstalled.app") }
        assertTrue(File(root, "Android/data/com.reinstalled.app/keep.txt").exists())
    }
    @Test fun unknownInventoryIsNeverTreatedAsUninstalled() {
        val root = temporary.newFolder()
        folder(root, "data/com.old.app")
        val store = LeftoverStore(root) { emptySet() }
        rejects { store.scan() }
        rejects { store.quarantine("data/com.old.app") }
        assertTrue(File(root, "Android/data/com.old.app/keep.txt").exists())
    }
    @Test fun restoreNeverOverwritesExistingAppData() {
        val (root, store) = fixture()
        folder(root, "data/com.old.app", "backup")
        val id = store.quarantine("data/com.old.app")
        val current = folder(root, "data/com.old.app", "new data")
        rejects { store.restore(id) }
        assertEquals("new data", File(current, "keep.txt").readText())
        assertEquals(1, store.recoveryList().length())
    }
    @Test fun purgeDeletesOnlyTheSelectedRecoveryPayload() {
        val (root, store) = fixture()
        folder(root, "data/com.old.app"); folder(root, "media/com.other.old")
        val first = store.quarantine("data/com.old.app")
        val second = store.quarantine("media/com.other.old")
        store.purge(first)
        assertEquals(second, store.recoveryList().getJSONObject(0).getString("id"))
        assertTrue(File(root, ".DebloaterRecovery/$second/payload/keep.txt").exists())
    }
    @Test fun untrustedKeysAndRecoveryRecordsCannotEscapeStorage() {
        val (root, store) = fixture()
        listOf("../com.old.app", "data/../../etc", "/data/com.old.app", "media/com.old.app/child", "data/com.old.app;id").forEach {
            rejects { store.quarantine(it) }
        }
        rejects { store.restore("../other") }
        folder(root, "data/com.old.app")
        val id = store.quarantine("data/com.old.app")
        val record = File(root, ".DebloaterRecovery/$id/record.json")
        record.writeText(JSONObject(record.readText()).put("key", "../outside").toString())
        rejects { store.purge(id) }
        assertTrue(File(root, ".DebloaterRecovery/$id/payload/keep.txt").exists())
    }
    @Test fun symbolicLinksNeverLeadToOutsideFiles() {
        val (root, store) = fixture()
        val outside = temporary.newFolder()
        val keep = File(outside, "keep.txt").apply { writeText("outside") }
        val original = folder(root, "data/com.old.app")
        try { Files.createSymbolicLink(File(original, "link").toPath(), outside.toPath()) }
        catch(error: Exception) { assumeNoException(error) }
        assertEquals(0, store.scan().getJSONArray("candidates").length())
        rejects { store.quarantine("data/com.old.app") }
        assertEquals("outside", keep.readText())
    }
}
