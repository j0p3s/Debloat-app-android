package io.github.j0p3s.debloater

import android.content.pm.ApplicationInfo
import io.github.j0p3s.debloater.data.Action
import io.github.j0p3s.debloater.data.AppInfo
import io.github.j0p3s.debloater.data.AppState
import io.github.j0p3s.debloater.data.SpaceEstimator
import io.github.j0p3s.debloater.data.StorageInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceEstimatorTest {
    private fun app(system: Boolean, state: AppState = AppState.ENABLED) = AppInfo(
        packageName = "com.example", label = "Example", isSystem = system, state = state,
        versionName = "1", versionCode = 1, apkPaths = listOf("/x/base.apk"), apkSize = 40,
        entry = null, protectedReason = null, applicationInfo = ApplicationInfo(),
        appBytes = 50, dataBytes = 200, cacheBytes = 30, sizeExact = true,
    )

    @Test
    fun disablingFreesNothing() {
        assertEquals(0, SpaceEstimator.estimate(app(false), Action.DISABLE, keepData = false))
        assertEquals(0, SpaceEstimator.estimate(app(true), Action.DISABLE, keepData = false))
    }

    @Test
    fun removingWithKeepDataFreesNothing() {
        assertEquals(0, SpaceEstimator.estimate(app(false), Action.REMOVE, keepData = true))
        assertEquals(0, SpaceEstimator.estimate(app(true), Action.REMOVE, keepData = true))
    }

    @Test
    fun removingUserAppFreesAppAndData() {
        assertEquals(250, SpaceEstimator.estimate(app(false), Action.REMOVE, keepData = false))
    }

    @Test
    fun removingSystemAppFreesOnlyData() {
        assertEquals(200, SpaceEstimator.estimate(app(true), Action.REMOVE, keepData = false))
    }

    @Test
    fun alreadyRemovedAppsFreeNothing() {
        assertEquals(0, SpaceEstimator.estimate(app(false, AppState.UNINSTALLED), Action.REMOVE, keepData = false))
    }

    @Test
    fun listEstimateSums() {
        assertEquals(450, SpaceEstimator.estimate(listOf(app(false), app(true)), Action.REMOVE, keepData = false))
    }

    @Test
    fun storageInfoMath() {
        val s = StorageInfo(totalBytes = 1000, freeBytes = 250)
        assertEquals(750, s.usedBytes)
        assertEquals(0.75f, s.usedFraction, 0.0001f)
    }
}
