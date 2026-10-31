package io.github.j0p3s.debloater.backup

import io.github.j0p3s.debloater.data.Action
import io.github.j0p3s.debloater.data.AppState
import org.json.JSONArray
import org.json.JSONObject

enum class EntryStatus { PENDING, BACKED_UP, DONE, FAILED, SKIPPED, RESTORED, RESTORE_FAILED }

data class BackupEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val versionName: String,
    val versionCode: Long,
    val stateBefore: AppState,
    val action: Action,
    val keepData: Boolean,
    val apkFiles: List<String> = emptyList(),
    val apkBytes: Long = 0,
    val status: EntryStatus = EntryStatus.PENDING,
    val message: String = "",
    /** Measured: free space on /data right after the command minus right before it. */
    val freedBytes: Long = 0,
    /** App + data size of the package when it was changed (0 if unknown). */
    val sizeBytes: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("package", packageName)
        .put("label", label)
        .put("system", isSystem)
        .put("versionName", versionName)
        .put("versionCode", versionCode)
        .put("stateBefore", stateBefore.name)
        .put("action", action.name)
        .put("keepData", keepData)
        .put("apkFiles", JSONArray(apkFiles))
        .put("apkBytes", apkBytes)
        .put("status", status.name)
        .put("message", message)
        .put("freedBytes", freedBytes)
        .put("sizeBytes", sizeBytes)

    companion object {
        fun fromJson(o: JSONObject) = BackupEntry(
            packageName = o.getString("package"),
            label = o.optString("label", o.getString("package")),
            isSystem = o.optBoolean("system"),
            versionName = o.optString("versionName"),
            versionCode = o.optLong("versionCode"),
            stateBefore = runCatching { AppState.valueOf(o.getString("stateBefore")) }.getOrDefault(AppState.ENABLED),
            action = runCatching { Action.valueOf(o.getString("action")) }.getOrDefault(Action.DISABLE),
            keepData = o.optBoolean("keepData", true),
            apkFiles = o.optJSONArray("apkFiles")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty(),
            apkBytes = o.optLong("apkBytes"),
            status = runCatching { EntryStatus.valueOf(o.getString("status")) }.getOrDefault(EntryStatus.PENDING),
            message = o.optString("message"),
            freedBytes = o.optLong("freedBytes"),
            sizeBytes = o.optLong("sizeBytes"),
        )
    }
}

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val sdk: Int,
    val release: String,
    val fingerprint: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("manufacturer", manufacturer).put("model", model).put("sdk", sdk)
        .put("release", release).put("fingerprint", fingerprint)

    companion object {
        fun current() = DeviceInfo(
            android.os.Build.MANUFACTURER, android.os.Build.MODEL, android.os.Build.VERSION.SDK_INT,
            android.os.Build.VERSION.RELEASE, android.os.Build.FINGERPRINT,
        )

        fun fromJson(o: JSONObject?) = DeviceInfo(
            o?.optString("manufacturer").orEmpty(), o?.optString("model").orEmpty(), o?.optInt("sdk") ?: 0,
            o?.optString("release").orEmpty(), o?.optString("fingerprint").orEmpty(),
        )
    }
}

class BackupSession(
    val id: String,
    val createdAt: Long,
    val action: Action,
    val userId: Int,
    val device: DeviceInfo,
    val entries: MutableList<BackupEntry>,
    var finished: Boolean = false,
    /** Free bytes on /data before the run and after it (-1 = unknown). */
    var freeBefore: Long = -1,
    var freeAfter: Long = -1,
    var totalStorage: Long = -1,
    /** Size of the APK backups written by this run. */
    var backupBytes: Long = 0,
    /** Estimate shown before the run. */
    var estimatedBytes: Long = 0,
) {
    /** Space freed on the phone by this run (sum of the measured per-app values). */
    val freedBytes: Long get() = entries.sumOf { it.freedBytes }

    val title: String get() = "${action.verb} · ${entries.size} app(s)"

    fun toJson(): JSONObject = JSONObject()
        .put("format", 1)
        .put("id", id)
        .put("createdAt", createdAt)
        .put("action", action.name)
        .put("userId", userId)
        .put("device", device.toJson())
        .put("finished", finished)
        .put("freeBefore", freeBefore)
        .put("freeAfter", freeAfter)
        .put("totalStorage", totalStorage)
        .put("backupBytes", backupBytes)
        .put("estimatedBytes", estimatedBytes)
        .put("entries", JSONArray().also { a -> entries.forEach { a.put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): BackupSession {
            val arr = o.optJSONArray("entries") ?: JSONArray()
            return BackupSession(
                id = o.getString("id"),
                createdAt = o.optLong("createdAt"),
                action = runCatching { Action.valueOf(o.getString("action")) }.getOrDefault(Action.DISABLE),
                userId = o.optInt("userId"),
                device = DeviceInfo.fromJson(o.optJSONObject("device")),
                entries = MutableList(arr.length()) { BackupEntry.fromJson(arr.getJSONObject(it)) },
                finished = o.optBoolean("finished"),
                freeBefore = o.optLong("freeBefore", -1),
                freeAfter = o.optLong("freeAfter", -1),
                totalStorage = o.optLong("totalStorage", -1),
                backupBytes = o.optLong("backupBytes"),
                estimatedBytes = o.optLong("estimatedBytes"),
            )
        }
    }
}
