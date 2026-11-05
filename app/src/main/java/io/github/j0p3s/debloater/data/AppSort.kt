package io.github.j0p3s.debloater.data

import androidx.annotation.StringRes
import io.github.j0p3s.debloater.R
import java.text.Collator

/** Sort orders offered on every app list. */
enum class AppSort(val title: String, @param:StringRes @get:StringRes val titleRes: Int) {
    NAME_ASC("Name A → Z", R.string.sort_name_asc),
    NAME_DESC("Name Z → A", R.string.sort_name_desc),
    SIZE_DESC("Largest first", R.string.sort_size_desc),
    SIZE_ASC("Smallest first", R.string.sort_size_asc),
    DATA_DESC("Most data first", R.string.sort_data_desc),
    DATA_ASC("Least data first", R.string.sort_data_asc);

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: NAME_ASC
    }
}

/** The name shown in lists: the app label, or the list name when the app has no label. */
val AppInfo.displayName: String
    get() = entry?.name?.takeIf { label == packageName } ?: label

/**
 * Sorts apps. Names use the device language's alphabetical order (so "é" sorts with "e" and case
 * is ignored). Ties are broken by name, then package name, so the order is always stable.
 */
fun List<AppInfo>.sortedBy(sort: AppSort): List<AppInfo> {
    val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
    val byName = Comparator<AppInfo> { a, b -> collator.compare(a.displayName, b.displayName) }
        .thenBy { it.packageName }
    val cmp: Comparator<AppInfo> = when (sort) {
        AppSort.NAME_ASC -> byName
        AppSort.NAME_DESC -> byName.reversed()
        AppSort.SIZE_DESC -> compareByDescending<AppInfo> { it.totalBytes }.then(byName)
        AppSort.SIZE_ASC -> compareBy<AppInfo> { it.totalBytes }.then(byName)
        AppSort.DATA_DESC -> compareByDescending<AppInfo> { it.dataBytes }.then(byName)
        AppSort.DATA_ASC -> compareBy<AppInfo> { it.dataBytes }.then(byName)
    }
    return sortedWith(cmp)
}
