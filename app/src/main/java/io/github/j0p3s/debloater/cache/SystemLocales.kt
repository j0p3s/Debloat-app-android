package io.github.j0p3s.debloater.cache

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList

/**
 * The phone's language, which is what Settings displays. With a per-app language (Android 13+),
 * Resources.getSystem() inside this process reports Debloater's own language instead, so Settings
 * labels were looked up in the wrong language (e.g. Portuguese on an English phone).
 */
object SystemLocales {
    fun get(context: Context): LocaleList =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            runCatching { context.getSystemService(LocaleManager::class.java).systemLocales }.getOrNull()
                ?.takeIf { !it.isEmpty } ?: Resources.getSystem().configuration.locales
        else Resources.getSystem().configuration.locales
}
