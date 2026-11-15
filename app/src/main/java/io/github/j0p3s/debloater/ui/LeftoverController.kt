package io.github.j0p3s.debloater.ui

import androidx.compose.runtime.*
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class LeftoverRow(val id: String, val key: String, val path: String, val bytes: Long)
data class LeftoverRequest(val action: String, val rows: List<LeftoverRow>)

class LeftoverController(private val userId: Int, private val scope: CoroutineScope, private val canRun: () -> Boolean) {
    var candidates by mutableStateOf(emptyList<LeftoverRow>()); private set
    var recovery by mutableStateOf(emptyList<LeftoverRow>()); private set
    var selected by mutableStateOf(emptySet<String>()); private set
    var busy by mutableStateOf(false); private set
    var stopping by mutableStateOf(false); private set
    var progress by mutableStateOf(0 to 0); private set
    var query by mutableStateOf("")
    var largestFirst by mutableStateOf(true)
    var notice by mutableStateOf<UiMessage?>(null); private set
    var pending by mutableStateOf<LeftoverRequest?>(null)
    val warnings = mutableStateListOf<String>()
    val log = io.github.j0p3s.debloater.debug.DebugLog.Mirror("leftovers")

    fun visible(): List<LeftoverRow> = candidates.filter { it.key.contains(query, true) }.let { rows ->
        if(largestFirst) rows.sortedByDescending { it.bytes } else rows.sortedBy { it.key.lowercase() }
    }
    fun toggle(key: String) { if(!busy) selected = if(key in selected) selected - key else selected + key }
    fun selectShown() { if(!busy) selected = selected + visible().map { it.key } }
    fun deselect() { if(!busy) selected = emptySet() }
    fun stop() { stopping = true } // An in-flight typed file operation completes before stopping.
    private fun ready() = !busy && canRun() && ShizukuManager.isReady()
    private fun rows(array: JSONArray, recover: Boolean): List<LeftoverRow> = (0 until array.length()).map { index ->
        val row = array.getJSONObject(index)
        val key = row.getString("key")
        LeftoverRow(if(recover) row.getString("id") else key, key, row.getString("path"), row.getLong("bytes"))
    }
    private suspend fun loadRecovery() {
        val result = ShizukuManager.leftovers("recovery", userId)
        check(result.isSuccess) { result.output }
        recovery = rows(JSONArray(result.stdout), true)
    }

    fun scan() {
        if(!ready()) return
        busy = true; stopping = false; notice = null; warnings.clear(); progress = 0 to 1
        scope.launch {
            try {
                val result = ShizukuManager.leftovers("scan", userId)
                log += "SCAN primary storage, Android user $userId\n${if(result.isSuccess) "OK" else result.output}"
                check(result.isSuccess) { result.output }
                val report = JSONObject(result.stdout)
                candidates = rows(report.getJSONArray("candidates"), false)
                selected = selected.intersect(candidates.map { it.key }.toSet())
                val messages = report.getJSONArray("warnings")
                warnings.addAll((0 until messages.length()).map { messages.getString(it) })
                progress = 1 to 1
                if(!stopping) loadRecovery()
                notice = UiMessage(if(stopping) R.string.cache_stopped else R.string.leftovers_scan_done, if(stopping) emptyList() else listOf(candidates.size))
            } catch(error: kotlinx.coroutines.CancellationException) { throw error }
            catch(error: Exception) {
                candidates = emptyList(); selected = emptySet()
                notice = UiMessage(R.string.cache_error, listOf(error.message.orEmpty()))
                log += "FAIL scan: ${error.message}"
            } finally { busy = false }
        }
    }

    fun refreshRecovery() {
        if(!ready()) return
        busy = true; stopping = false; notice = null
        scope.launch {
            try { loadRecovery() }
            catch(error: kotlinx.coroutines.CancellationException) { throw error }
            catch(error: Exception) { notice = UiMessage(R.string.cache_error, listOf(error.message.orEmpty())) }
            finally { busy = false }
        }
    }

    fun request(action: String, row: LeftoverRow? = null) {
        if(!ready()) return
        val targets = when(action) {
            "quarantine" -> candidates.filter { it.key in selected }
            "restore", "purge" -> recovery.filter { it.id == row?.id }
            else -> emptyList()
        }
        if(targets.isNotEmpty()) pending = LeftoverRequest(action, targets)
    }

    fun confirm() {
        val request = pending ?: return
        pending = null
        if(!ready()) return
        busy = true; stopping = false; notice = null; progress = 0 to request.rows.size
        scope.launch {
            var ok = 0
            var failed = 0
            try {
                for(row in request.rows) {
                    if(stopping) break
                    val argument = if(request.action == "quarantine") row.key else row.id
                    val result = ShizukuManager.leftovers(request.action, userId, argument)
                    log += "${if(result.isSuccess) "OK" else "FAIL"} ${request.action}: ${row.path}\n${result.output}"
                    if(result.isSuccess) {
                        ok++
                        if(request.action == "quarantine") {
                            candidates = candidates.filterNot { it.key == row.key }
                            selected = selected - row.key
                        }
                    } else failed++
                    progress = ok + failed to request.rows.size
                    if(!result.isSuccess) break // Re-review after permissions, inventory or file-state failures.
                }
                loadRecovery() // Recovery records survive process death and partial operations.
                notice = UiMessage(R.string.leftovers_action_done, listOf(ok, failed))
            } catch(error: kotlinx.coroutines.CancellationException) { throw error }
            catch(error: Exception) { notice = UiMessage(R.string.cache_error, listOf(error.message.orEmpty())); log += "FAIL: ${error.message}" }
            finally { busy = false }
        }
    }
}
