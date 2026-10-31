package io.github.j0p3s.debloater.data

import android.content.pm.ApplicationInfo

/**
 * How safe it is to remove a package (ordered from least to most risky).
 *
 * - [RECOMMENDED]: bloat, ads, telemetry, or apps you can reinstall from a store. Recommended for almost everyone.
 * - [SAFE]: removing it has no effect on the system, but you might still use the app.
 * - [EXTREME]: aggressive debloat ("Advanced" in UAD-NG). Can break features; every entry has a warning.
 * - [EXPERT]: can break important features ("Expert" in UAD-NG). Locked unless expert mode is on.
 * - [UNSAFE]: critical. Removing it can break the phone or cause a boot loop. Locked unless expert mode is on.
 * - [UNKNOWN]: not in any debloat list.
 */
enum class SafetyLevel(val title: String) {
    RECOMMENDED("Recommended"),
    SAFE("Safe"),
    EXTREME("Extreme"),
    EXPERT("Expert"),
    UNSAFE("Unsafe"),
    UNKNOWN("Unlisted");

    /** Needs expert mode to be selected (for UNKNOWN only when it is a system app). */
    val needsExpertMode: Boolean get() = this == EXPERT || this == UNSAFE || this == UNKNOWN

    /** Shown with warnings and needs explicit acknowledgement before a real run. */
    val isRisky: Boolean get() = this == EXTREME || this == EXPERT || this == UNSAFE || this == UNKNOWN

    companion object {
        /** The more cautious of two levels. UNKNOWN never wins over a real rating. */
        fun stricter(a: SafetyLevel, b: SafetyLevel): SafetyLevel = when {
            a == UNKNOWN -> b
            b == UNKNOWN -> a
            else -> if (a.ordinal >= b.ordinal) a else b
        }
    }
}

/** A replacement app suggested by the Android Debloat List. [source] letters: f=F-Droid, g=Play, a=Amazon, s=Galaxy Store. */
data class Alternative(val packageName: String, val label: String, val source: String, val repo: String?, val reason: String?) {
    val sources: String
        get() = source.mapNotNull {
            when (it) { 'f' -> "F-Droid"; 'g' -> "Google Play"; 'a' -> "Amazon"; 's' -> "Galaxy Store"; else -> null }
        }.joinToString()
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
    /** Longer explanation, usually from UAD-NG. */
    val details: String? = null,
    /** Packages that need this one (removing it can break them). */
    val neededBy: List<String> = emptyList(),
    /** Packages this one needs. */
    val dependencies: List<String> = emptyList(),
    /** Lists that rate this package, e.g. "Debloater", "UAD-NG", "System App Remover". */
    val sources: List<String> = emptyList(),
    /** Known to fail with pm uninstall on some ROMs; disabling usually works. */
    val oftenCantUninstall: Boolean = false,
    /** Rating in the Android Debloat List (delete, replace, caution, unsafe), null if not listed there. */
    val adlRemoval: String? = null,
    /** Links from ADL: store pages, investigation reports… */
    val webLinks: List<String> = emptyList(),
    /** Open-source replacement apps suggested by ADL. */
    val alternatives: List<Alternative> = emptyList(),
) {
    val inAdl: Boolean get() = adlRemoval != null

    /** Page about this package on the Android Debloat List website. */
    val adlUrl: String get() = "https://adl.muntashir.dev/bloatware/$packageName.html"
    /** Applies Android-version-specific overrides (e.g. a package that became a system module). */
    fun forSdk(sdk: Int): DebloatEntry {
        val o = overrides.firstOrNull { sdk >= it.fromSdk && (it.toSdk == null || sdk <= it.toSdk) } ?: return this
        // A version rule can only make the rating stricter than what other lists say.
        return copy(level = SafetyLevel.stricter(level, o.level), warning = o.warning ?: warning)
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
