package io.github.j0p3s.debloater.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import io.github.j0p3s.debloater.shizuku.ShizukuManager

/**
 * Packages that are never touched, even in expert mode. Removing any of these can
 * soft-brick or boot-loop the device, or cut this app off from Shizuku.
 */
object ProtectedPackages {
    private val hardcoded = mapOf(
        "android" to "Android core system",
        "com.android.systemui" to "System UI (status bar, navigation)",
        "com.android.settings" to "Settings app",
        "com.android.phone" to "Phone/telephony service",
        "com.android.shell" to "Shell, which Shizuku needs to work",
        "com.android.providers.settings" to "Settings storage",
        "com.android.providers.telephony" to "Telephony storage",
        "com.android.providers.contacts" to "Contacts storage",
        "com.android.providers.media" to "Media storage",
        "com.android.providers.media.module" to "Media storage (mainline)",
        "com.google.android.providers.media.module" to "Media storage (mainline)",
        "com.android.providers.downloads" to "Download manager",
        "com.android.externalstorage" to "External storage",
        "com.android.packageinstaller" to "Package installer",
        "com.google.android.packageinstaller" to "Package installer",
        "com.miui.global.packageinstaller" to "Package installer (MIUI)",
        "com.android.permissioncontroller" to "Permission controller",
        "com.google.android.permissioncontroller" to "Permission controller",
        "com.android.server.telecom" to "Telecom service",
        "com.android.networkstack" to "Network stack",
        "com.google.android.networkstack" to "Network stack",
        "com.android.networkstack.tethering" to "Tethering",
        "com.google.android.networkstack.tethering" to "Tethering",
        "com.android.bluetooth" to "Bluetooth stack",
        "com.android.se" to "Secure element",
        "com.android.keychain" to "Key chain / credentials",
        "com.android.location.fused" to "Fused location",
        "com.android.inputdevices" to "Input devices",
        "com.android.webview" to "System WebView",
        "com.google.android.webview" to "System WebView",
        "com.android.vpndialogs" to "VPN dialogs",
        "com.android.wifi.resources" to "Wi-Fi resources",
        "com.google.android.wifi.resources" to "Wi-Fi resources",
        "com.android.extservices" to "Extended services",
        "com.google.android.ext.services" to "Extended services",
        "com.android.modulemetadata" to "Mainline module metadata",
        "com.google.android.modulemetadata" to "Mainline module metadata",
        "com.android.documentsui" to "File picker",
        "com.google.android.documentsui" to "File picker",
        "com.miui.securitycenter" to "MIUI security center (removing it can boot-loop)",
        "com.lbe.security.miui" to "MIUI permission manager",
        "com.samsung.android.providers.context" to "Samsung context provider",
        "com.sec.android.app.SecSetupWizard" to "Samsung setup wizard",
        "com.android.emergency.dialer" to "Emergency dialer",
        ShizukuManager.SHIZUKU_PACKAGE to "Shizuku, which this app needs",
    )

    /** Static list plus the current launcher, keyboard and this app itself. */
    fun build(context: Context): Map<String, String> {
        val result = HashMap(hardcoded)
        result[context.packageName] = "This app"
        defaultLauncher(context)?.let { result[it] = "Your current launcher (install and select another one first)" }
        currentKeyboard(context)?.let { result[it] = "Your current keyboard (install and select another one first)" }
        return result
    }

    private fun defaultLauncher(context: Context): String? = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" }
    }.getOrNull()

    private fun currentKeyboard(context: Context): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
    }.getOrNull()
}
