package io.github.j0p3s.debloater.cache

import java.util.Locale

enum class SettingsCacheResult { CONFIRMED, SKIPPED, UNAVAILABLE, UNKNOWN }

/** Conservative, testable selectors. Unknown ROM layouts are skipped, never guessed. */
object SettingsCachePolicy {
    fun timedOutResult(cachePressed: Boolean, settingsWindow: Boolean): SettingsCacheResult =
        if(!cachePressed && settingsWindow) SettingsCacheResult.UNAVAILABLE else SettingsCacheResult.UNKNOWN
    const val SETTINGS_PACKAGE = "com.android.settings"
    data class Target(val packageName: String, val label: String, val uniqueLabel: Boolean)
    data class Node(
        val id: String = "", val text: String = "", val description: String = "",
        val parent: Int? = null, val clickable: Boolean = false,
        val enabled: Boolean = true, val visible: Boolean = true,
    )
    data class Page(val packageName: String, val nodes: List<Node>, val complete: Boolean = true)
    data class Labels(val storage: Set<String>, val clearCache: Set<String>, val destructive: Set<String>)

    fun normalize(value: String): String = value.replace(Regex("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]"), "")
        .replace('\u00a0', ' ').trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    private val headerIds = setOf("entity_header_title", "app_name", "application_name")
    private val packageIds = setOf("package_name", "app_package_name")
    private val clearIds = setOf("button2", "action2", "clear_cache", "clear_cache_button", "clear_cache_btn")
    private fun settingsId(node: Node, names: Set<String>) = names.any { node.id == "$SETTINGS_PACKAGE:id/$it" }
    private fun matches(node: Node, labels: Set<String>): Boolean = labels.any { label ->
        normalize(label).isNotEmpty() && (normalize(node.text) == normalize(label) || normalize(node.description) == normalize(label))
    }

    fun isTarget(page: Page, target: Target): Boolean {
        if (!page.complete || page.packageName != SETTINGS_PACKAGE) return false
        // No automatic dialog confirmations, including ambiguous OEM "Clear data" sheets.
        if (page.nodes.any { it.visible && it.id in setOf("android:id/alertTitle", "android:id/parentPanel") }) return false
        val packages = page.nodes.filter { it.visible && settingsId(it, packageIds) }
        if (packages.isNotEmpty()) return packages.all { it.text == target.packageName }
        return target.uniqueLabel && page.nodes.any {
            it.visible && settingsId(it, headerIds) && normalize(it.text) == normalize(target.label)
        }
    }

    private fun clickParent(nodes: List<Node>, index: Int, accept: (Node) -> Boolean): Int? {
        var cursor: Int? = index
        repeat(4) {
            val current = cursor ?: return null
            val node = nodes.getOrNull(current) ?: return null
            if (!node.visible) return null
            if (node.clickable && accept(node)) return current
            cursor = node.parent
        }
        return null
    }

    fun clearButton(page: Page, target: Target, labels: Labels): Int? {
        if (!isTarget(page, target)) return null
        val candidates = page.nodes.indices.filter { i ->
            val node = page.nodes[i]
            node.visible && matches(node, labels.clearCache) && !matches(node, labels.destructive)
        }.mapNotNull { i ->
            clickParent(page.nodes, i) { settingsId(it, clearIds) && !matches(it, labels.destructive) }
        }.distinct()
        return candidates.singleOrNull()
    }

    fun storageRow(page: Page, target: Target, labels: Labels): Int? {
        if (!isTarget(page, target)) return null
        return page.nodes.indices.filter { i ->
            val node = page.nodes[i]
            node.visible && node.id in setOf("android:id/title", "$SETTINGS_PACKAGE:id/title", "") && matches(node, labels.storage)
        }.mapNotNull { i -> clickParent(page.nodes, i) { it.enabled } }.distinct().singleOrNull()
    }
}
