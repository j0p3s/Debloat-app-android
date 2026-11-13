package io.github.j0p3s.debloater

import io.github.j0p3s.debloater.data.CacheGroup
import io.github.j0p3s.debloater.data.CachePolicy
import io.github.j0p3s.debloater.data.CacheResidue
import io.github.j0p3s.debloater.data.CacheSort
import io.github.j0p3s.debloater.data.CacheScope
import io.github.j0p3s.debloater.shizuku.ShellResult
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CachePolicyTest {
    @Test fun residueIsMarkedOnlyWhenMostOfALargeCacheRemains() {
        // RedMagic OS 11 report: Clash of Clans and SmartThings unchanged after a confirmed Settings clear.
        assertEquals(2_221_027_328L, CachePolicy.residueAfterClear(2_221_027_328, 2_221_027_328))
        assertEquals(340_840_448L, CachePolicy.residueAfterClear(340_840_448, 340_840_448))
        // Telegram and Instagram really cleared.
        assertNull(CachePolicy.residueAfterClear(1_045_032_960, 4_096))
        assertNull(CachePolicy.residueAfterClear(88_363_008, 12_288))
        // Small leftovers are normal.
        assertNull(CachePolicy.residueAfterClear(5_000_000, 5_000_000))
        assertNull(CachePolicy.residueAfterClear(null, 50_000_000))
    }

    @Test fun residueStopsApplyingOnceNewClearableCacheAppears() {
        val r = CacheResidue(2_221_027_328, 0)
        assertTrue(CachePolicy.residueApplies(r, 2_222_620_672))
        assertFalse(CachePolicy.residueApplies(r, 2_400_000_000))
        assertFalse(CachePolicy.residueApplies(r, 0))
        assertFalse(CachePolicy.residueApplies(null, 2_221_027_328))
        assertEquals(r.copy(sharedFolderBytes = 4096), CacheResidue.decode(r.copy(sharedFolderBytes = 4096).encode()))
        assertEquals(r, CacheResidue.decode(r.encode()))
        assertNull(CacheResidue.decode("garbage"))
    }

    @Test fun duOutputParsing() {
        assertEquals(12L * 1024, CachePolicy.parseDuKb(ShellResult(0, "12\t/storage/emulated/0/Android/data/a.b/cache\n", "")))
        assertEquals(0L, CachePolicy.parseDuKb(ShellResult(1, "", "du: /storage/emulated/0/Android/data/a.b/cache: No such file or directory")))
        assertNull(CachePolicy.parseDuKb(ShellResult(1, "", "du: Permission denied")))
        assertEquals("/storage/emulated/0/Android/data/com.supercell.clashofclans/cache", CachePolicy.sharedCacheDir(0, "com.supercell.clashofclans"))
    }

    @Test fun fallbackOffersFailedAndUntriedGroupsButNeverEmptyCaches() {
        val volume = UUID.randomUUID()
        fun g(uid: Int, vararg pkgs: String, bytes: Long? = 100) = CacheGroup(uid, volume, pkgs.toList(), pkgs.first(), false, bytes)
        val coc = g(1, "com.supercell.clashofclans")
        val shared = g(2, "a.one", "a.two")
        val empty = g(3, "z.empty", bytes = 0)
        val ok = g(4, "ok.app")
        val offer = CachePolicy.fallback(listOf(coc, shared, empty, ok), failed = listOf("com.supercell.clashofclans"), untried = listOf("a.two", "z.empty"))!!
        assertEquals(listOf(coc), offer.failed)
        assertEquals(listOf(shared), offer.untried)
        assertNull(CachePolicy.fallback(listOf(coc, ok), failed = emptyList(), untried = emptyList()))
        // A group that failed is never listed twice.
        assertEquals(emptyList<CacheGroup>(), CachePolicy.fallback(listOf(coc), listOf("com.supercell.clashofclans"), listOf("com.supercell.clashofclans"))!!.untried)
    }

    @Test fun unchangedSizeAfterTimeoutIsReportedAsLikelyIgnored() {
        // RedMagic OS 11, Clash of Clans force-stopped: identical to the byte 75 s later.
        assertTrue(CachePolicy.likelyIgnored(2_222_620_672, 2_222_620_672))
        assertFalse(CachePolicy.likelyIgnored(2_222_620_672, 2_221_027_328))
        assertFalse(CachePolicy.likelyIgnored(2_222_620_672, null))
        assertFalse(CachePolicy.likelyIgnored(null, 5))
        assertFalse(CachePolicy.likelyIgnored(0, 0))
    }

    @Test fun timedOutClearCountsOnlyWhenTheCacheReallyShrank() {
        assertTrue(CachePolicy.clearedAfterTimeout(2_200_000_000, 0))
        assertTrue(CachePolicy.clearedAfterTimeout(2_200_000_000, 40_000_000))
        assertFalse(CachePolicy.clearedAfterTimeout(2_200_000_000, 2_100_000_000))
        assertFalse(CachePolicy.clearedAfterTimeout(2_200_000_000, null))
        assertTrue(CachePolicy.clearedAfterTimeout(null, 0))
        // Seen on RedMagic OS 11: 282624 bytes of empty cache folders left after the clear.
        assertTrue(CachePolicy.clearedAfterTimeout(null, 282_624))
        assertFalse(CachePolicy.clearedAfterTimeout(null, 5_000_000))
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
