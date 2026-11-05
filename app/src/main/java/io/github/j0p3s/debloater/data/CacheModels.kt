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

object CachePolicy {
    /** After a timed-out cache-only clear, how long the real cache size is re-checked before the batch stops. */
    const val TIMEOUT_GRACE_MS = 60_000L

    /**
     * A running app may refill a little cache at once, and empty cache folders still occupy blocks.
     * Counts as cleared: nothing left, at most 5 % of the old size, or a residue of at most 1 MiB.
     */
    const val RESIDUE_BYTES = 1L shl 20
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
