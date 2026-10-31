package io.github.j0p3s.debloater.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _expertMode = MutableStateFlow(sp.getBoolean(KEY_EXPERT, false))
    /** Unlocks UNSAFE entries and shows system apps that are not in the debloat list. */
    val expertMode: StateFlow<Boolean> = _expertMode.asStateFlow()

    private val _keepData = MutableStateFlow(sp.getBoolean(KEY_KEEP_DATA, true))
    /** Default for "keep app data" when removing user apps (system apps always keep data). */
    val keepData: StateFlow<Boolean> = _keepData.asStateFlow()

    private val _sort = MutableStateFlow(AppSort.fromName(sp.getString(KEY_SORT, null)))
    /** Sort order of the app lists, shared by all screens and remembered between launches. */
    val sort: StateFlow<AppSort> = _sort.asStateFlow()

    fun setSort(v: AppSort) {
        sp.edit { putString(KEY_SORT, v.name) }; _sort.value = v
    }

    private val _autoUpdateLists = MutableStateFlow(sp.getBoolean(KEY_AUTO_UPDATE, false))
    /** Download newer debloat lists automatically, at most once a week, when the app opens. */
    val autoUpdateLists: StateFlow<Boolean> = _autoUpdateLists.asStateFlow()

    fun setAutoUpdateLists(v: Boolean) {
        sp.edit { putBoolean(KEY_AUTO_UPDATE, v) }; _autoUpdateLists.value = v
    }

    fun setExpertMode(v: Boolean) {
        sp.edit { putBoolean(KEY_EXPERT, v) }; _expertMode.value = v
    }

    fun setKeepData(v: Boolean) {
        sp.edit { putBoolean(KEY_KEEP_DATA, v) }; _keepData.value = v
    }

    private companion object {
        const val KEY_EXPERT = "expert_mode"
        const val KEY_KEEP_DATA = "keep_data"
        const val KEY_SORT = "sort"
        const val KEY_AUTO_UPDATE = "auto_update_lists"
    }
}
