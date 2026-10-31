package io.github.j0p3s.debloater

import android.content.pm.ApplicationInfo
import io.github.j0p3s.debloater.data.AppInfo
import io.github.j0p3s.debloater.data.AppSort
import io.github.j0p3s.debloater.data.AppState
import io.github.j0p3s.debloater.data.sortedBy
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSortTest {
    private fun app(label: String, pkg: String, appBytes: Long, dataBytes: Long) = AppInfo(
        packageName = pkg, label = label, isSystem = false, state = AppState.ENABLED,
        versionName = "1", versionCode = 1, apkPaths = emptyList(), apkSize = appBytes,
        entry = null, protectedReason = null, applicationInfo = ApplicationInfo(),
        appBytes = appBytes, dataBytes = dataBytes, sizeExact = true,
    )

    private val apps = listOf(
        app("banana", "p.b", appBytes = 10, dataBytes = 500),   // total 510
        app("Apple", "p.a", appBytes = 300, dataBytes = 0),     // total 300
        app("Éclair", "p.e", appBytes = 50, dataBytes = 50),    // total 100
        app("cherry", "p.c", appBytes = 1000, dataBytes = 5),   // total 1005
    )

    private fun order(sort: AppSort) = apps.sortedBy(sort).map { it.label }

    @Test
    fun alphabeticalIgnoresCaseAndAccents() {
        assertEquals(listOf("Apple", "banana", "cherry", "Éclair"), order(AppSort.NAME_ASC))
        assertEquals(listOf("Éclair", "cherry", "banana", "Apple"), order(AppSort.NAME_DESC))
    }

    @Test
    fun bySize() {
        assertEquals(listOf("cherry", "banana", "Apple", "Éclair"), order(AppSort.SIZE_DESC))
        assertEquals(listOf("Éclair", "Apple", "banana", "cherry"), order(AppSort.SIZE_ASC))
    }

    @Test
    fun byData() {
        assertEquals(listOf("banana", "Éclair", "cherry", "Apple"), order(AppSort.DATA_DESC))
        assertEquals(listOf("Apple", "cherry", "Éclair", "banana"), order(AppSort.DATA_ASC))
    }

    @Test
    fun tiesAreBrokenByName() {
        val tied = listOf(app("b", "p.2", 10, 0), app("a", "p.1", 10, 0))
        assertEquals(listOf("a", "b"), tied.sortedBy(AppSort.SIZE_DESC).map { it.label })
    }

    @Test
    fun unknownSavedValueFallsBackToName() {
        assertEquals(AppSort.NAME_ASC, AppSort.fromName("garbage"))
        assertEquals(AppSort.SIZE_ASC, AppSort.fromName("SIZE_ASC"))
    }
}
