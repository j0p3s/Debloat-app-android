package io.github.j0p3s.debloater.data

import java.text.Collator

/** Sort orders offered on every app list. */
enum class AppSort(val title: String) {
    NAME_ASC("Name A → Z"),
    NAME_DESC("Name Z → A"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first"),
    DATA_DESC("Most data first"),
    DATA_ASC("Least data first");

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
