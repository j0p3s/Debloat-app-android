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
) {
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
