package io.github.j0p3s.debloater.data

import android.content.pm.ApplicationInfo

/**
 * How safe it is to remove a package.
 *
 * - [RECOMMENDED]: bloat, ads, telemetry or duplicates. Recommended for almost everyone.
 * - [SAFE]: removing it has no effect on the system, but you might still use the app.
 * - [EXTREME]: aggressive debloat. Can break features; every entry has a warning.
 * - [UNSAFE]: critical. Removing it can break the phone or cause a boot loop. Locked unless expert mode is on.
 * - [UNKNOWN]: not in the debloat list.
 */
enum class SafetyLevel(val title: String) {
    RECOMMENDED("Recommended"),
    SAFE("Safe"),
    EXTREME("Extreme"),
    UNSAFE("Unsafe"),
    UNKNOWN("Unlisted"),
}

data class SdkOverride(val fromSdk: Int, val toSdk: Int?, val level: SafetyLevel, val warning: String?)

data class DebloatEntry(
    val packageName: String,
    val name: String,
    val vendor: String,
    val level: SafetyLevel,
    val description: String,
    val warning: String?,
    val overrides: List<SdkOverride> = emptyList(),
) {
    /** Applies Android-version-specific overrides (e.g. a package that became a system module). */
    fun forSdk(sdk: Int): DebloatEntry {
        val o = overrides.firstOrNull { sdk >= it.fromSdk && (it.toSdk == null || sdk <= it.toSdk) } ?: return this
        return copy(level = o.level, warning = o.warning ?: warning)
    }
}

enum class AppState(val title: String) {
    ENABLED("Enabled"),
    DISABLED("Disabled"),
    UNINSTALLED("Removed"),
}

data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val state: AppState,
    val versionName: String,
    val versionCode: Long,
    val apkPaths: List<String>,
    val apkSize: Long,
    val entry: DebloatEntry?,
    val protectedReason: String?,
    val applicationInfo: ApplicationInfo,
    /** APK, compiled code and native libraries (from StorageStatsManager), or the APK size as a fallback. */
    val appBytes: Long = apkSize,
    /** All app data for this user, cache included. 0 when unknown. */
    val dataBytes: Long = 0,
    /** Cache part of [dataBytes]. */
    val cacheBytes: Long = 0,
    /** True when sizes come from StorageStatsManager (needs Usage access), false = APK size only. */
    val sizeExact: Boolean = false,
    /** Phone maker or publisher, e.g. "Samsung" or "Google" ([VendorCatalog]). */
    val vendor: String = VendorCatalog.OTHER,
) {
    val totalBytes: Long get() = appBytes + dataBytes
    val isProtected get() = protectedReason != null
    val level: SafetyLevel
        get() = when {
            isProtected -> SafetyLevel.UNSAFE
            entry != null -> entry.level
            else -> SafetyLevel.UNKNOWN
        }
}

/** What to do with a package. */
enum class Action(val verb: String, val pastTense: String) {
    DISABLE("Disable", "disabled"),
    REMOVE("Remove", "removed"),
    ENABLE("Enable", "enabled"),
    RESTORE("Restore", "restored"),
}

/** Internal storage (/data) of the phone. */
data class StorageInfo(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes <= 0) 0f else usedBytes / totalBytes.toFloat()
}

/**
 * Estimates how much storage an action frees.
 *
 * - Disable frees nothing: the app and its data stay on the phone.
 * - Remove with "keep data" (pm uninstall -k) keeps the data and, for user apps, the APK,
 *   so it frees (almost) nothing. That is what makes instant restores possible.
 * - Remove without keeping data deletes the data. User apps are deleted completely
 *   (APK + data). System apps keep their APK on the read-only system partition.
 */
object SpaceEstimator {
    fun estimate(app: AppInfo, action: Action, keepData: Boolean): Long = when {
        action != Action.REMOVE -> 0L
        app.state == AppState.UNINSTALLED -> 0L
        keepData -> 0L
        app.isSystem -> app.dataBytes
        else -> app.totalBytes
    }

    fun estimate(apps: List<AppInfo>, action: Action, keepData: Boolean): Long = apps.sumOf { estimate(it, action, keepData) }
}
