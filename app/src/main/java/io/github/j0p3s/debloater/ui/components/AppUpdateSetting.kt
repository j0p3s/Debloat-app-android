package io.github.j0p3s.debloater.ui.components

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.j0p3s.debloater.BuildConfig
import io.github.j0p3s.debloater.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object AppReleasePolicy {
    fun version(value: String): List<Int>? = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(value)
        ?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: return null }
    fun newer(remote: String, local: String): Boolean {
        val a = version(remote) ?: return false
        val b = version(local) ?: return false
        return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    }
    fun asset(tag: String) = "https://github.com/j0p3s/Debloat-app-android/releases/download/$tag/Debloater-$tag.apk"
}

@Composable
fun AppUpdateSetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { context.getSystemService(DownloadManager::class.java) }
    val prefs = remember { context.getSharedPreferences("app-updates", Context.MODE_PRIVATE) }
    var downloadId by remember { mutableLongStateOf(prefs.getLong("download", -1)) }
    var checking by remember { mutableStateOf(false) }
    var remote by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableIntStateOf(0) }
    var percent by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(downloadId) {
        if(downloadId < 0) return@LaunchedEffect
        while(true) {
            val result = withContext(Dispatchers.IO) {
                manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                    if(!cursor.moveToFirst()) null else {
                        val state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        state to if(total > 0) ((bytes * 100 / total).coerceIn(0,100)).toInt() else 0
                    }
                }
            }
            status = result?.first ?: DownloadManager.STATUS_FAILED
            percent = result?.second ?: 0
            if(status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) break
            delay(1000)
        }
    }
    val active = downloadId >= 0 && status !in setOf(DownloadManager.STATUS_SUCCESSFUL, DownloadManager.STATUS_FAILED)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ota_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ota_help))
            Text(BuildConfig.VERSION_NAME)
            OutlinedButton(enabled = !checking && !active, onClick = {
                checking = true; failed = false; remote = null
                scope.launch {
                    try {
                        remote = withContext(Dispatchers.IO) {
                            val connection = URL("https://api.github.com/repos/j0p3s/Debloat-app-android/releases/latest").openConnection() as HttpURLConnection
                            try {
                                connection.connectTimeout = 15000; connection.readTimeout = 15000
                                connection.setRequestProperty("Accept", "application/vnd.github+json")
                                connection.setRequestProperty("User-Agent", "Debloater/${BuildConfig.VERSION_NAME}")
                                check(connection.responseCode == 200)
                                val data = connection.inputStream.use { input ->
                                    val output = java.io.ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while(output.size() <= 1_000_000) {
                                        val count = input.read(buffer)
                                        if(count < 0) break
                                        output.write(buffer, 0, count)
                                    }
                                    output.toByteArray()
                                }
                                check(data.size <= 1_000_000)
                                val json = JSONObject(String(data, Charsets.UTF_8))
                                check(!json.getBoolean("draft") && !json.getBoolean("prerelease"))
                                val tag = json.getString("tag_name")
                                check(tag.startsWith("v") && AppReleasePolicy.version(tag) != null)
                                val assets = json.getJSONArray("assets")
                                check((0 until assets.length()).any { i -> assets.getJSONObject(i).let {
                                    it.getString("browser_download_url") == AppReleasePolicy.asset(tag) && it.getLong("size") in 1..200_000_000
                                } })
                                tag
                            } finally { connection.disconnect() }
                        }
                    } catch(error: kotlinx.coroutines.CancellationException) { throw error }
                    catch(_: Exception) { failed = true }
                    finally { checking = false }
                }
            }) { Text(stringResource(R.string.ota_check)) }
            if(checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(failed || status == DownloadManager.STATUS_FAILED) Text(stringResource(R.string.ota_error))
            remote?.let { tag ->
                if(AppReleasePolicy.newer(tag, BuildConfig.VERSION_NAME)) {
                    Text(tag)
                    Button(enabled = !active, onClick = {
                        try {
                            val request = DownloadManager.Request(Uri.parse(AppReleasePolicy.asset(tag)))
                                .setTitle("Debloater $tag").setMimeType("application/vnd.android.package-archive")
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "Debloater-$tag-${System.currentTimeMillis()}.apk")
                            status = DownloadManager.STATUS_PENDING; percent = 0
                            downloadId = manager.enqueue(request)
                            prefs.edit().putLong("download", downloadId).apply()
                        } catch(_: Exception) { failed = true }
                    }) { Text(stringResource(R.string.ota_download)) }
                } else Text(stringResource(R.string.ota_current))
            }
            if(active) {
                Text("$percent %")
                TextButton(onClick = { manager.remove(downloadId); prefs.edit().remove("download").apply(); downloadId = -1; status = 0 }) { Text(stringResource(R.string.cancel)) }
            }
            if(status == DownloadManager.STATUS_SUCCESSFUL) Text(stringResource(R.string.ota_done))
        }
    }
}
