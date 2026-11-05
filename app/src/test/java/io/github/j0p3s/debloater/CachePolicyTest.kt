package io.github.j0p3s.debloater

import io.github.j0p3s.debloater.data.CacheGroup
import io.github.j0p3s.debloater.data.CachePolicy
import io.github.j0p3s.debloater.data.CacheSort
import io.github.j0p3s.debloater.data.CacheScope
import io.github.j0p3s.debloater.shizuku.ShellResult
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CachePolicyTest {
    @Test fun timedOutClearCountsOnlyWhenTheCacheReallyShrank() {
        assertTrue(CachePolicy.clearedAfterTimeout(2_200_000_000, 0))
        assertTrue(CachePolicy.clearedAfterTimeout(2_200_000_000, 40_000_000))
        assertFalse(CachePolicy.clearedAfterTimeout(2_200_000_000, 2_100_000_000))
        assertFalse(CachePolicy.clearedAfterTimeout(2_200_000_000, null))
        assertFalse(CachePolicy.clearedAfterTimeout(null, 5))
        assertTrue(CachePolicy.clearedAfterTimeout(null, 0))
    }

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

    @Test fun categoryTotalsIncludeOnlyTheRequestedAppTypes() {
        val user = row("user", 100)
        val system = row("system", 250).copy(isSystem = true)
        val unknownSystem = row("unknown", null).copy(isSystem = true)
        val all = listOf(user, system, unknownSystem)
        assertEquals(listOf(user), CachePolicy.inScope(all, CacheScope.USER))
        assertEquals(listOf(system, unknownSystem), CachePolicy.inScope(all, CacheScope.SYSTEM))
        assertEquals(all, CachePolicy.inScope(all, CacheScope.BOTH))
        assertEquals(100L, CachePolicy.inScope(all, CacheScope.USER).sumOf { it.bytes ?: 0 })
        assertEquals(250L, CachePolicy.inScope(all, CacheScope.SYSTEM).sumOf { it.bytes ?: 0 })
        assertEquals(350L, CachePolicy.inScope(all, CacheScope.BOTH).sumOf { it.bytes ?: 0 })
    }

    @Test fun switchingToUserAppsCannotClearSelectedSystemApps() {
        val user = row("user", 100)
        val system = row("system", 250).copy(isSystem = true)
        val all = listOf(user, system)
        val selected = all.map { it.key }.toSet()
        assertEquals(listOf(user), CachePolicy.selectedInScope(all, selected, CacheScope.USER))
        assertEquals(listOf(system), CachePolicy.selectedInScope(all, selected, CacheScope.SYSTEM))
        assertEquals(all, CachePolicy.selectedInScope(all, selected, CacheScope.BOTH))
        assertTrue(CachePolicy.selectedInScope(all, setOf(system.key), CacheScope.USER).isEmpty())
        assertTrue(CachePolicy.selectedInScope(all, emptySet(), CacheScope.BOTH).isEmpty())
    }

}
