package io.github.j0p3s.debloater

import io.github.j0p3s.debloater.data.CacheGroup
import io.github.j0p3s.debloater.data.CachePolicy
import io.github.j0p3s.debloater.data.CacheSort
import io.github.j0p3s.debloater.shizuku.ShellResult
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CachePolicyTest {
    private fun help(text: String, code: Int = 0) = ShellResult(code, text, "")
    private fun row(label: String, bytes: Long?) = CacheGroup(label.hashCode(), UUID(0, 0), listOf("p.$label"), label, false, bytes)

    @Test fun olderHelpDoesNotEnableClearing() {
        assertFalse(CachePolicy.supportsCacheOnly(help("  clear [--user USER_ID] PACKAGE\n")))
        assertFalse(CachePolicy.supportsCacheOnly(help("--cache-only is mentioned elsewhere\n  clear PACKAGE")))
        assertFalse(CachePolicy.supportsCacheOnly(help("  clear [--cache-only-other] PACKAGE")))
    }
    @Test fun supportRequiresSuccessfulHelpFromTheClearCommand() {
        assertTrue(CachePolicy.supportsCacheOnly(help("  clear [--user USER_ID] [--cache-only] PACKAGE\n")))
        assertFalse(CachePolicy.supportsCacheOnly(help("  clear [--cache-only] PACKAGE", 1)))
        assertFalse(CachePolicy.supportsCacheOnly(ShellResult(0, "  clear [--cache-only] PACKAGE", "java.lang.SecurityException")))
    }
    @Test fun commandAlwaysScopesToTheUserAndCacheOnly() {
        assertEquals("pm clear --user 10 --cache-only com.example.app", CachePolicy.clearCommand(10, "com.example.app"))
    }
    @Test(expected = IllegalArgumentException::class) fun commandRejectsShellInjection() {
        CachePolicy.clearCommand(0, "com.example.app; pm clear com.other")
    }
    @Test(expected = IllegalArgumentException::class) fun commandRejectsAllUsers() {
        CachePolicy.clearCommand(-1, "com.example.app")
    }
    @Test fun exitZeroWithoutSuccessIsNotReportedAsCleared() {
        assertTrue(CachePolicy.succeeded(help("Success\n")))
        assertFalse(CachePolicy.succeeded(help("Failure [permission denied]")))
        assertFalse(CachePolicy.succeeded(help("")))
        assertFalse(CachePolicy.succeeded(help("Success", 1)))
    }
    @Test fun unknownSizesSortLastInBothSizeOrders() {
        val rows = listOf(row("unknown", null), row("big", 100), row("zero", 0), row("small", 10))
        assertEquals(listOf("big", "small", "zero", "unknown"), CachePolicy.sorted(rows, CacheSort.LARGEST).map { it.label })
        assertEquals(listOf("zero", "small", "big", "unknown"), CachePolicy.sorted(rows, CacheSort.SMALLEST).map { it.label })
    }
    @Test fun namesSortWithoutCaseAndWithStableTieBreaks() {
        val rows = listOf(row("banana", 1), row("Apple", 1), row("cherry", 1))
        assertEquals(listOf("Apple", "banana", "cherry"), CachePolicy.sorted(rows, CacheSort.NAME_ASC).map { it.label })
        assertEquals(listOf("cherry", "banana", "Apple"), CachePolicy.sorted(rows, CacheSort.NAME_DESC).map { it.label })
        assertEquals(listOf("Apple", "banana", "cherry"), CachePolicy.sorted(rows, CacheSort.LARGEST).map { it.label })
    }
}
