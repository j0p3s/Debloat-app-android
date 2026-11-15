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

    private val _onboardingDone = MutableStateFlow(sp.getBoolean(KEY_ONBOARDING, false))
    /** The first-launch welcome (language, how it works, Shizuku setup) has been shown. */
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    fun setOnboardingDone(v: Boolean) {
        sp.edit { putBoolean(KEY_ONBOARDING, v) }; _onboardingDone.value = v
    }

    private val _splashHidden = MutableStateFlow(sp.getBoolean(KEY_SPLASH_HIDDEN, false))
    /** The user ticked "Don't show this screen again": the support splash is never shown. */
    val splashHidden: StateFlow<Boolean> = _splashHidden.asStateFlow()

    fun setSplashHidden(v: Boolean) {
        sp.edit { putBoolean(KEY_SPLASH_HIDDEN, v) }; _splashHidden.value = v
    }

    /** versionCode for which the support splash was last shown (0 = never), so it appears once per update. */
    var splashSeenVersion: Int
        get() = sp.getInt(KEY_SPLASH_VERSION, 0)
        set(v) = sp.edit { putInt(KEY_SPLASH_VERSION, v) }

    fun setExpertMode(v: Boolean) {
        sp.edit { putBoolean(KEY_EXPERT, v) }; _expertMode.value = v
    }

    private val _debugFolder = MutableStateFlow(sp.getString(KEY_DEBUG_FOLDER, null))
    /** Document tree URI chosen for saving debug reports, with a persisted read/write grant. */
    val debugFolder: StateFlow<String?> = _debugFolder.asStateFlow()

    fun setDebugFolder(v: String?) {
        sp.edit { putString(KEY_DEBUG_FOLDER, v) }; _debugFolder.value = v
    }

    fun setKeepData(v: Boolean) {
        sp.edit { putBoolean(KEY_KEEP_DATA, v) }; _keepData.value = v
    }

    private companion object {
        const val KEY_EXPERT = "expert_mode"
        const val KEY_KEEP_DATA = "keep_data"
        const val KEY_SORT = "sort"
        const val KEY_AUTO_UPDATE = "auto_update_lists"
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_DEBUG_FOLDER = "debug_folder"
        const val KEY_SPLASH_HIDDEN = "splash_hidden"
        const val KEY_SPLASH_VERSION = "splash_seen_version"
    }
}
