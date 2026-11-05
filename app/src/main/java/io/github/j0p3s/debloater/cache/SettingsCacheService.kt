package io.github.j0p3s.debloater.cache

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.PixelFormat
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.github.j0p3s.debloater.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

enum class SettingsCacheResult { CONFIRMED, SKIPPED, UNKNOWN }

/** Opt-in, foreground Settings UI assistance; never runs from accessibility events alone. */
class SettingsCacheService : AccessibilityService() {
    companion object {
        private var instance: SettingsCacheService? = null
        private val connected = MutableStateFlow(false)
        val available = connected.asStateFlow()

        fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        suspend fun clear(target: SettingsCachePolicy.Target, completed: Int, total: Int): SettingsCacheResult =
            instance?.clearOne(target, completed, total) ?: SettingsCacheResult.SKIPPED
    }

    private var activeJob: Job? = null
    private var stopView: LinearLayout? = null

    override fun onServiceConnected() { instance = this; connected.value = true }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { activeJob?.cancel() }
    override fun onUnbind(intent: Intent?): Boolean { disconnect(); return super.onUnbind(intent) }
    override fun onDestroy() { disconnect(); super.onDestroy() }
    private fun disconnect() {
        activeJob?.cancel()
        removeStopControl()
        if (instance === this) { instance = null; connected.value = false }
    }

    @Suppress("DiscouragedApi") // Read the installed Settings app's own labels in the system locale.
    private fun labels(): SettingsCachePolicy.Labels {
        val settings = createPackageContext(SettingsCachePolicy.SETTINGS_PACKAGE, 0)
        val config = Configuration(settings.resources.configuration).apply { setLocales(Resources.getSystem().configuration.locales) }
        val resources = settings.createConfigurationContext(config).resources
        fun strings(vararg names: String): Set<String> = names.mapNotNull { name ->
            val id = resources.getIdentifier(name, "string", SettingsCachePolicy.SETTINGS_PACKAGE)
            if (id == 0) null else runCatching { resources.getString(id) }.getOrNull()
        }.toSet()
        return SettingsCachePolicy.Labels(
            strings("storage_settings_for_app", "storage_settings", "storage_use", "storage_label"),
            strings("clear_cache_btn_text", "clear_cache", "clear_cache_label"),
            strings("clear_user_data_text", "clear_data_btn_text", "manage_space_text"),
        )
    }

    private data class Snapshot(val page: SettingsCachePolicy.Page, val nodes: List<AccessibilityNodeInfo>) {
        @Suppress("DEPRECATION") fun close() { nodes.forEach { it.recycle() } }
    }

    private fun snapshot(): Snapshot? {
        val root = rootInActiveWindow ?: return null
        if(root.packageName?.toString() != SettingsCachePolicy.SETTINGS_PACKAGE) {
            // Do not traverse or collect the contents of any other app's window.
            return Snapshot(SettingsCachePolicy.Page(root.packageName?.toString().orEmpty(), emptyList()), listOf(root))
        }
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        val descriptors = mutableListOf<SettingsCachePolicy.Node>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int?>>()
        queue.add(root to null)
        try {
            while (queue.isNotEmpty() && nodes.size < 600) {
                val (node, parent) = queue.removeFirst()
                val index = nodes.size
                nodes += node
                descriptors += SettingsCachePolicy.Node(node.viewIdResourceName.orEmpty(), node.text?.toString().orEmpty(),
                    node.contentDescription?.toString().orEmpty(), parent, node.isClickable, node.isEnabled, node.isVisibleToUser)
                repeat(node.childCount) { child -> node.getChild(child)?.let { queue.add(it to index) } }
            }
            return Snapshot(SettingsCachePolicy.Page(root.packageName?.toString().orEmpty(), descriptors, queue.isEmpty()), nodes)
        } catch (error: Exception) {
            @Suppress("DEPRECATION") nodes.forEach { it.recycle() }
            throw error
        } finally {
            @Suppress("DEPRECATION") queue.forEach { it.first.recycle() }
        }
    }

    private fun showStopControl(label: String, completed: Int, total: Int) {
        val manager = getSystemService(WindowManager::class.java)
        val dp = resources.displayMetrics.density
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
            setBackgroundColor(0xff20352c.toInt())
            addView(TextView(context).apply {
                text = getString(R.string.cache_settings_running, label) + "\n" +
                    getString(R.string.cache_settings_remaining, completed, total, total - completed)
                setTextColor(0xffffffff.toInt()); maxLines = 4
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(context).apply {
                text = getString(R.string.cancel)
                setOnClickListener { activeJob?.cancel() }
            })
        }
        // A small, non-focusable accessibility overlay keeps Stop reachable while Settings is open.
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP }
        manager.addView(bar, params)
        stopView = bar
    }

    private fun removeStopControl() {
        stopView?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        stopView = null
    }

    private suspend fun clearOne(target: SettingsCachePolicy.Target, completed: Int, total: Int): SettingsCacheResult {
        check(activeJob == null) { "Settings cache assistant is already busy" }
        if (target.packageName in setOf(packageName, SettingsCachePolicy.SETTINGS_PACKAGE)) return SettingsCacheResult.SKIPPED
        activeJob = currentCoroutineContext()[Job]
        try {
            val labels = labels()
            if (labels.clearCache.isEmpty() || labels.storage.isEmpty()) return SettingsCacheResult.UNKNOWN
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${target.packageName}"))
                .setPackage(SettingsCachePolicy.SETTINGS_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val activity = packageManager.resolveActivity(intent, 0)?.activityInfo ?: return SettingsCacheResult.UNKNOWN
            if (activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) return SettingsCacheResult.UNKNOWN
            showStopControl(target.label, completed, total) // If Stop cannot be shown, do not start automation.
            startActivity(intent)
            var navigated = false
            var clicked = false
            var disabledPolls = 0
            var scrolls = 0
            var stableTargetPolls = 0
            return withTimeoutOrNull(20_000) {
                // Let the explicit package URI replace the previously visible Settings page.
                delay(600)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (instance !== this@SettingsCacheService ||
                        getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
                        !getSystemService(PowerManager::class.java).isInteractive) return@withTimeoutOrNull SettingsCacheResult.UNKNOWN
                    val snapshot = snapshot()
                    if (snapshot != null) try {
                        val page = snapshot.page
                        if (!SettingsCachePolicy.isTarget(page, target)) {
                            stableTargetPolls = 0
                            // After entering this target, any different screen stops the run.
                            if (navigated || clicked) return@withTimeoutOrNull SettingsCacheResult.UNKNOWN
                        } else {
                            stableTargetPolls++
                            if (stableTargetPolls >= 2) {
                                val clear = SettingsCachePolicy.clearButton(page, target, labels)
                                if (clear != null) {
                                    val button = page.nodes[clear]
                                    if (clicked) {
                                        disabledPolls = if (!button.enabled) disabledPolls + 1 else 0
                                        if (disabledPolls >= 3) return@withTimeoutOrNull SettingsCacheResult.CONFIRMED
                                    } else if (!button.enabled) {
                                        return@withTimeoutOrNull SettingsCacheResult.SKIPPED
                                    } else {
                                        // Exactly one press; no dialog confirmation, gestures or blind key navigation.
                                        clicked = snapshot.nodes[clear].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                        if (!clicked) return@withTimeoutOrNull SettingsCacheResult.UNKNOWN
                                    }
                                } else if (!navigated && !clicked) {
                                    val storage = SettingsCachePolicy.storageRow(page, target, labels)
                                    if (storage != null) {
                                        if (!snapshot.nodes[storage].performAction(AccessibilityNodeInfo.ACTION_CLICK))
                                            return@withTimeoutOrNull SettingsCacheResult.UNKNOWN
                                        navigated = true
                                        stableTargetPolls = 0
                                    } else if (scrolls < 3 && stableTargetPolls % 4 == 0) {
                                        snapshot.nodes.firstOrNull { it.isScrollable && it.isVisibleToUser }
                                            ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                                        scrolls++
                                    }
                                }
                            }
                        }
                    } finally { snapshot.close() }
                    delay(300)
                }
                @Suppress("UNREACHABLE_CODE") SettingsCacheResult.UNKNOWN
            } ?: SettingsCacheResult.UNKNOWN
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return SettingsCacheResult.UNKNOWN
        } finally {
            removeStopControl()
            activeJob = null
        }
    }
}
