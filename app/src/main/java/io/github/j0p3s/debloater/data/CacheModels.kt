package io.github.j0p3s.debloater.data

import androidx.annotation.StringRes
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.shizuku.ShellResult
import java.text.Collator
import java.util.UUID

data class CacheGroup(
    val uid: Int,
    val volume: UUID,
    val packages: List<String>,
    val label: String,
    val isSystem: Boolean,
    val bytes: Long?,
    val error: String? = null,
) {
    val key: String get() = "$uid:$volume"
}

enum class CacheSort(@param:StringRes @get:StringRes val titleRes: Int) {
    LARGEST(R.string.cache_largest), SMALLEST(R.string.cache_smallest),
    NAME_ASC(R.string.sort_name_asc), NAME_DESC(R.string.sort_name_desc),
}

enum class CacheScope(@param:StringRes @get:StringRes val titleRes: Int) {
    USER(R.string.user_apps), SYSTEM(R.string.system_apps), BOTH(R.string.cache_both);

    fun includes(group: CacheGroup): Boolean = when (this) {
        USER -> !group.isSystem
        SYSTEM -> group.isSystem
        BOTH -> true
    }
}

enum class CacheClearMethod(@param:StringRes @get:StringRes val titleRes: Int) {
    SHIZUKU(R.string.cache_shizuku_method), SETTINGS(R.string.cache_settings_method);
}

/**
 * Cache Android still reported after a confirmed clear. Typically files Android counts as cache that are not in a
 * cache folder (e.g. moved out after download), which Clear cache cannot remove; or an app that refilled at once.
 * [sharedFolderBytes]: measured content of Android/data/<pkg>/cache via Shizuku, null if not measured.
 */
data class CacheResidue(val bytes: Long, val at: Long, val sharedFolderBytes: Long? = null) {
    fun encode(): String = "$bytes|$at|${sharedFolderBytes ?: ""}"
    companion object {
        fun decode(value: String): CacheResidue? = value.split('|').takeIf { it.size == 3 }?.let { p ->
            val bytes = p[0].toLongOrNull() ?: return null
            val at = p[1].toLongOrNull() ?: return null
            CacheResidue(bytes, at, p[2].toLongOrNull())
        }
    }
}

/** Groups Shizuku failed to clear, and groups not tried because Android ignored Shizuku. */
data class CacheFallback(val failed: List<CacheGroup>, val untried: List<CacheGroup>)

object CachePolicy {
    /** Residues smaller than this are ignored: empty folders and quick refills are normal. */
    const val RESIDUE_MIN_BYTES = 8L shl 20

    /** After a confirmed clear: most of the cache is still reported, so mark it as a residue. */
    fun residueAfterClear(before: Long?, after: Long?): Long? =
        after?.takeIf { before != null && before > 0 && it >= RESIDUE_MIN_BYTES && it >= before / 2 }

    /** The residue still explains the current size (it has not grown with new, clearable cache). */
    fun residueApplies(residue: CacheResidue?, now: Long?): Boolean =
        residue != null && now != null && now >= RESIDUE_MIN_BYTES && now <= residue.bytes + maxOf(1L shl 20, residue.bytes / 20)

    /** Shell path of an app's cache folder on primary shared storage. */
    fun sharedCacheDir(userId: Int, packageName: String): String {
        require(userId >= 0)
        require(PackageRepository.isValidPackageName(packageName))
        return "/storage/emulated/$userId/Android/data/$packageName/cache"
    }

    /** KiB from `du -sk` → bytes; a missing folder (no output) counts as empty. */
    fun parseDuKb(result: ShellResult): Long? {
        val first = result.stdout.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        if (first == null) return if (result.stderr.contains("No such file")) 0L else null
        return first.split(Regex("\\s+")).firstOrNull()?.toLongOrNull()?.times(1024)
    }

    /** After a timed-out shell clear, an unchanged size for this long ends the wait early. */
    const val IGNORED_AFTER_MS = 20_000L

    /** Maps failed/untried packages back to their (non-empty) cache groups; null when there is nothing to offer. */
    fun fallback(targets: List<CacheGroup>, failed: Collection<String>, untried: Collection<String>): CacheFallback? {
        val failedGroups = targets.filter { g -> g.bytes != 0L && g.packages.any { it in failed } }
        val untriedGroups = targets.filter { g -> g.bytes != 0L && g !in failedGroups && g.packages.any { it in untried } }
        return CacheFallback(failedGroups, untriedGroups).takeIf { failedGroups.isNotEmpty() || untriedGroups.isNotEmpty() }
    }

    /** After a timed-out cache-only clear, how long the real cache size is re-checked before the batch stops. */
    const val TIMEOUT_GRACE_MS = 60_000L

    /**
     * A running app may refill a little cache at once, and empty cache folders still occupy blocks.
     * Counts as cleared: nothing left, at most 5 % of the old size, or a residue of at most 1 MiB.
     */
    const val RESIDUE_BYTES = 1L shl 20
    /** Exactly the same non-zero size after a timed-out shell clear: Android most likely ignored the request. */
    fun likelyIgnored(before: Long?, now: Long?): Boolean = before != null && before > 0 && now == before

    fun clearedAfterTimeout(before: Long?, now: Long?): Boolean =
        now != null && (now == 0L || now <= RESIDUE_BYTES || (before != null && before > 0 && now <= before / 20))

    fun inScope(groups: List<CacheGroup>, scope: CacheScope): List<CacheGroup> = groups.filter(scope::includes)

    /** Clearing uses the same category as the list, including when selections exist in other categories. */
    fun selectedInScope(groups: List<CacheGroup>, keys: Set<String>, scope: CacheScope): List<CacheGroup> =
        inScope(groups, scope).filter { it.key in keys }

    /** Only the clear command's help line may establish support. Never try a destructive probe. */
    fun supportsCacheOnly(help: ShellResult): Boolean = help.isSuccess &&
        help.output.lineSequence().any { Regex("^\\s*clear\\s+.*--cache-only(?:\\]|\\s|$)").containsMatchIn(it) }

    fun clearCommand(userId: Int, packageName: String): String {
        require(userId >= 0)
        require(PackageRepository.isValidPackageName(packageName))
        return "pm clear --user $userId --cache-only $packageName"
    }

    fun succeeded(result: ShellResult): Boolean = result.isSuccess &&
        result.stdout.lineSequence().any { it.trim() == "Success" }

    fun sorted(groups: List<CacheGroup>, sort: CacheSort): List<CacheGroup> {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val names = Comparator<CacheGroup> { a, b -> collator.compare(a.label, b.label) }.thenBy { it.key }
        val order = when(sort) {
            CacheSort.NAME_ASC -> names
            CacheSort.NAME_DESC -> names.reversed()
            CacheSort.LARGEST -> compareBy<CacheGroup> { it.bytes == null }.thenByDescending { it.bytes ?: 0 }.then(names)
            CacheSort.SMALLEST -> compareBy<CacheGroup> { it.bytes == null }.thenBy { it.bytes ?: 0 }.then(names)
        }
        return groups.sortedWith(order)
    }
}
