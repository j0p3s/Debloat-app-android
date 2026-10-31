package io.github.j0p3s.debloater.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PackageRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val db by lazy { DebloatDatabase.get(context) }

    /** Android user id this app runs as (0 for the main user, 10+ for secondary users/work profiles). */
    val userId: Int = Process.myUid() / 100_000

    private val storageStats: StorageStatsManager? = context.getSystemService(StorageStatsManager::class.java)

    suspend fun loadAll(): List<AppInfo> = withContext(Dispatchers.IO) {
        val protected = ProtectedPackages.build(context)
        val exact = hasUsageAccess()
        installedApplications().mapNotNull { ai -> runCatching { toAppInfo(ai, protected, exact) }.getOrNull() }
            .sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))
    }

    /**
     * Per-app data sizes need the "Usage access" app-op. Debloater grants it to itself through
     * Shizuku (appops set ... GET_USAGE_STATS allow), or the user can grant it in Settings.
     */
    fun hasUsageAccess(): Boolean = runCatching {
        val aom = context.getSystemService(AppOpsManager::class.java)
        aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Free bytes on /data right now (raw filesystem value, used to measure what an action freed). */
    fun freeBytesNow(): Long = runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(0L)

    fun storageInfo(): StorageInfo {
        val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val free = stat?.availableBytes ?: 0L
        // getTotalBytes reports the advertised size (e.g. 128 GB), which matches what users expect.
        val total = runCatching { storageStats!!.getTotalBytes(StorageManager.UUID_DEFAULT) }.getOrNull()
            ?: stat?.totalBytes ?: 0L
        return StorageInfo(total, free)
    }

    /** Re-reads one package. Returns null if it is gone completely (not even kept with data). */
    fun load(packageName: String): AppInfo? {
        val ai = try {
            getAppInfo(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        return toAppInfo(ai, ProtectedPackages.build(context), hasUsageAccess())
    }

    /** Current state without building the whole AppInfo. Null means not on the device at all. */
    fun stateOf(packageName: String): AppState? {
        val ai = try {
            getAppInfo(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        return stateOf(ai)
    }

    private fun stateOf(ai: ApplicationInfo): AppState {
        if (ai.flags and ApplicationInfo.FLAG_INSTALLED == 0) return AppState.UNINSTALLED
        val setting = runCatching { pm.getApplicationEnabledSetting(ai.packageName) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        return when (setting) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> AppState.DISABLED
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> AppState.ENABLED
            else -> if (ai.enabled) AppState.ENABLED else AppState.DISABLED
        }
    }

    private fun toAppInfo(ai: ApplicationInfo, protected: Map<String, String>, exactSizes: Boolean): AppInfo {
        val info: PackageInfo? = runCatching { getPackageInfo(ai.packageName) }.getOrNull()
        val paths = buildList {
            ai.sourceDir?.let { add(it) }
            ai.splitSourceDirs?.let { addAll(it) }
        }.distinct()
        val isSystem = ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val apkSize = paths.sumOf { runCatching { File(it).length() }.getOrDefault(0L) }
        // Removed-for-user packages have no stats for this user; they fall back to the APK size.
        val stats = if (exactSizes) runCatching {
            storageStats!!.queryStatsForPackage(ai.storageUuid, ai.packageName, Process.myUserHandle())
        }.getOrNull() else null
        return AppInfo(
            packageName = ai.packageName,
            label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(ai.packageName),
            isSystem = isSystem,
            state = stateOf(ai),
            versionName = info?.versionName.orEmpty(),
            versionCode = info?.longVersionCode ?: 0L,
            apkPaths = paths,
            apkSize = apkSize,
            entry = db.get(ai.packageName, Build.VERSION.SDK_INT),
            protectedReason = protected[ai.packageName],
            applicationInfo = ai,
            appBytes = stats?.appBytes ?: apkSize,
            dataBytes = stats?.dataBytes ?: 0L,
            cacheBytes = stats?.cacheBytes ?: 0L,
            sizeExact = stats != null,
        )
    }

    private val queryFlags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS

    private fun installedApplications(): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(queryFlags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(queryFlags)
        }

    private fun getAppInfo(pkg: String): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(queryFlags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(pkg, queryFlags)
        }

    private fun getPackageInfo(pkg: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(queryFlags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, queryFlags)
        }

    companion object {
        private val PACKAGE_NAME = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$")

        /** Package names go into shell commands, so only allow valid names. */
        fun isValidPackageName(name: String) = name.length in 1..255 && PACKAGE_NAME.matches(name)
    }
}
