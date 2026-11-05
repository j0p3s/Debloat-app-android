package io.github.j0p3s.debloater.cache

import java.util.Locale

enum class SettingsCacheResult { CONFIRMED, SKIPPED, UNAVAILABLE, UNKNOWN }

/** Conservative, testable selectors. Unknown ROM layouts are skipped, never guessed. */
object SettingsCachePolicy {
    /** Opening App info, Storage and locating the cache control. */
    const val FIND_TIMEOUT_MS = 20_000L
    /** After the single press: Settings disables the button only once Android finished deleting. */
    const val CLEAR_TIMEOUT_MS = 120_000L
    fun deadlineAfterPress(now: Long, current: Long): Long = maxOf(current, now + CLEAR_TIMEOUT_MS)
    fun timedOutResult(cachePressed: Boolean, settingsWindow: Boolean): SettingsCacheResult =
        if(!cachePressed && settingsWindow) SettingsCacheResult.UNAVAILABLE else SettingsCacheResult.UNKNOWN
    const val SETTINGS_PACKAGE = "com.android.settings"
    data class Target(val packageName: String, val label: String, val uniqueLabel: Boolean, val labelAliases: Set<String> = emptySet())
    data class Node(
        val id: String = "", val text: String = "", val description: String = "",
        val parent: Int? = null, val clickable: Boolean = false,
        val enabled: Boolean = true, val visible: Boolean = true,
    )
    data class Page(val packageName: String, val nodes: List<Node>, val complete: Boolean = true)
    data class Labels(val storage: Set<String>, val clearCache: Set<String>, val destructive: Set<String>, val pageTitles: Set<String> = emptySet())
    /** OEM fallback labels, selected by Android Settings language, never by Debloater's UI language. */
    fun fallbackLabels(language: String): Labels {
        val words = when(language) {
            "en" -> listOf("Storage & cache", "Clear cache", "Clear storage", "App info")
            "pt" -> listOf("Armazenamento e cache", "Limpar cache", "Limpar armazenamento", "Info da app")
            "es" -> listOf("Almacenamiento y caché", "Borrar caché", "Borrar almacenamiento", "Información de la aplicación")
            "fr" -> listOf("Espace de stockage et cache", "Vider le cache", "Effacer le stockage", "Infos sur l’appli")
            "de" -> listOf("Speicher und Cache", "Cache leeren", "Speicherinhalt löschen", "App-Info")
            "it" -> listOf("Spazio di archiviazione e cache", "Svuota cache", "Cancella spazio di archiviazione", "Informazioni app")
            "ru" -> listOf("Хранилище и кеш", "Очистить кеш", "Очистить хранилище", "О приложении")
            "zh" -> listOf("存储和缓存", "清除缓存", "清除存储空间", "应用信息")
            "hi" -> listOf("स्टोरेज और कैश", "कैश मेमोरी मिटाएं", "स्टोरेज खाली करें", "ऐप्लिकेशन की जानकारी")
            "ar" -> listOf("مساحة التخزين وذاكرة التخزين المؤقت", "محو ذاكرة التخزين المؤقت", "محو بيانات التخزين", "معلومات التطبيق")
            "id", "in" -> listOf("Penyimpanan & cache", "Hapus cache", "Hapus penyimpanan", "Info aplikasi")
            else -> return Labels(emptySet(), emptySet(), emptySet())
        }
        val storage = setOf(words[0]) + if(language == "en") setOf("Storage and cache") else emptySet()
        return Labels(storage, setOf(words[1]), setOf(words[2]), storage + words[3])
    }

    /** Compact description of visible labelled Settings nodes for the debug log (Settings window only). */
    fun describe(page: Page, limit: Int = 80): String = page.nodes.withIndex()
        .filter { (_, n) -> n.visible && (n.text.isNotBlank() || n.description.isNotBlank() || n.clickable) }
        .take(limit)
        .joinToString("\n") { (i, n) ->
            val label = listOf(n.text, n.description).filter { it.isNotBlank() }.joinToString(" | ").take(80)
            "#$i^${n.parent ?: "-"} ${n.id.substringAfter(":id/").ifBlank { "(no id)" }}" +
                "${if (n.clickable) " click" else ""}${if (!n.enabled) " disabled" else ""} \"$label\""
        }

    fun normalize(value: String): String = value.replace(Regex("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]"), "")
        .replace('\u00a0', ' ').trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    private val headerIds = setOf("entity_header_title", "app_name", "application_name")
    private val packageIds = setOf("package_name", "app_package_name")
    private val clearIds = setOf("button2", "action2", "clear_cache", "clear_cache_button", "clear_cache_btn")
    private fun settingsId(node: Node, names: Set<String>) = names.any { node.id == "$SETTINGS_PACKAGE:id/$it" }
    private fun matches(node: Node, labels: Set<String>): Boolean = labels.any { label ->
        normalize(label).isNotEmpty() && (normalize(node.text).replace(" ", "") == normalize(label).replace(" ", "") ||
            normalize(node.description).replace(" ", "") == normalize(label).replace(" ", ""))
    }

    fun isTarget(page: Page, target: Target, labels: Labels? = null): Boolean {
        if (!page.complete || page.packageName != SETTINGS_PACKAGE) return false
        // No automatic dialog confirmations, including ambiguous OEM "Clear data" sheets.
        if (page.nodes.any { it.visible && it.id in setOf("android:id/alertTitle", "android:id/parentPanel") }) return false
        val packages = page.nodes.filter { it.visible && settingsId(it, packageIds) }
        if (packages.isNotEmpty()) return packages.all { it.text == target.packageName }
        val names = (target.labelAliases + target.label).map(::normalize).toSet()
        val header = target.uniqueLabel && page.nodes.any {
            it.visible && settingsId(it, headerIds) && normalize(it.text) in names
        }
        if(header) return true
        // OEM Settings may omit header IDs. Require both a known Settings page title
        // and the exact, unique installed app label before permitting any action.
        return target.uniqueLabel && labels != null && page.nodes.any { it.visible && matches(it, labels.pageTitles) } &&
            page.nodes.any { it.visible && normalize(it.text) in names }
    }

    private fun clickParent(nodes: List<Node>, index: Int, allowDisabled: Boolean = false, accept: (Node) -> Boolean): Int? {
        var cursor: Int? = index
        repeat(4) {
            val current = cursor ?: return null
            val node = nodes.getOrNull(current) ?: return null
            if (!node.visible) return null
            if ((node.clickable || (allowDisabled && !node.enabled)) && accept(node)) return current
            cursor = node.parent
        }
        return null
    }

    fun clearButton(page: Page, target: Target, labels: Labels): Int? {
        if (!isTarget(page, target, labels)) return null
        val candidates = page.nodes.indices.filter { i ->
            val node = page.nodes[i]
            node.visible && matches(node, labels.clearCache) && !matches(node, labels.destructive)
        }.mapNotNull { i ->
            clickParent(page.nodes, i, allowDisabled = true) { candidate ->
                // A directly labelled clickable cache control is safe even with an OEM ID.
                (settingsId(candidate, clearIds) || matches(candidate, labels.clearCache)) && !matches(candidate, labels.destructive)
            }
        }.distinct()
        return candidates.singleOrNull()
    }

    fun controlEnabled(nodes: List<Node>, index: Int): Boolean {
        var cursor: Int? = index
        val visited = mutableSetOf<Int>()
        while(cursor != null) {
            if(!visited.add(cursor)) return false
            val node = nodes.getOrNull(cursor) ?: return false
            if(!node.enabled) return false
            cursor = node.parent
        }
        return true
    }

    fun storageRow(page: Page, target: Target, labels: Labels): Int? {
        if (!isTarget(page, target, labels)) return null
        return page.nodes.indices.filter { i ->
            val node = page.nodes[i]
            node.visible && node.id in setOf("android:id/title", "$SETTINGS_PACKAGE:id/title", "") && matches(node, labels.storage)
        }.mapNotNull { i -> clickParent(page.nodes, i) { it.enabled } }.distinct().singleOrNull()
    }
}
