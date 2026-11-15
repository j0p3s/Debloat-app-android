package io.github.j0p3s.debloater

import io.github.j0p3s.debloater.debug.DebugLogStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DebugLogStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun appendsIndentedMultilineEntriesInOrder() {
        val store = DebugLogStore(tmp.newFolder())
        store.append("I", "cache", "first\nsecond line")
        store.append("E", "shell", "boom")
        val text = store.read()
        assertTrue(text.contains("I/cache: first\n    second line\n"))
        assertTrue(text.indexOf("I/cache") < text.indexOf("E/shell: boom"))
    }

    @Test fun rotatesOneGenerationAndKeepsSizeBounded() {
        val store = DebugLogStore(tmp.newFolder(), maxBytes = 200)
        repeat(50) { store.append("I", "t", "entry $it " + "x".repeat(40)) }
        assertTrue(store.previous.isFile)
        assertTrue(store.size() < 600)
        assertTrue(store.read().contains("entry 49"))
        assertFalse(store.read().contains("entry 0 "))
    }

    @Test fun clearRemovesEverything() {
        val store = DebugLogStore(tmp.newFolder(), maxBytes = 10)
        repeat(3) { store.append("W", "t", "line") }
        store.clear()
        assertEquals(0L, store.size())
        assertEquals("", store.read())
    }
}
