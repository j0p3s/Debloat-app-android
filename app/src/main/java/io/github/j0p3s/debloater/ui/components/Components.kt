package io.github.j0p3s.debloater.ui.components

import android.content.pm.ApplicationInfo
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.data.AppInfo
import io.github.j0p3s.debloater.data.AppState
import io.github.j0p3s.debloater.data.SafetyLevel
import io.github.j0p3s.debloater.data.StorageInfo
import io.github.j0p3s.debloater.data.displayName
import io.github.j0p3s.debloater.ui.theme.levelColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val iconCache = LruCache<String, ImageBitmap>(300)

@Composable
fun AppIcon(info: ApplicationInfo, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState(iconCache.get(info.packageName), info.packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.packageManager.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap()
                }.getOrNull()?.also { iconCache.put(info.packageName, it) }
            }
        }
    }
    val b = bmp
    val m = modifier.size(40.dp)
    if (b != null) Image(b, contentDescription = null, modifier = m)
    else Box(m.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun LevelPill(level: SafetyLevel) = Pill(stringResource(level.titleRes), levelColor(level, isSystemInDarkTheme()))

@Composable
fun StatePill(state: AppState) {
    val c = when (state) {
        AppState.ENABLED -> MaterialTheme.colorScheme.primary
        AppState.DISABLED -> Color(0xFFEF8F00)
        AppState.UNINSTALLED -> MaterialTheme.colorScheme.error
    }
    Pill(stringResource(state.titleRes), c)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AppRow(
    app: AppInfo,
    selected: Boolean,
    selectable: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    showDescription: Boolean = true,
    onInfo: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (selectable) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        } else {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.locked), tint = MaterialTheme.colorScheme.outline)
            }
        }
        AppIcon(app.applicationInfo, Modifier.padding(top = 4.dp).size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.displayName,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                LevelPill(app.level)
                if (app.state != AppState.ENABLED) StatePill(app.state)
                Pill(if (app.isSystem) stringResource(R.string.system) else stringResource(R.string.user), MaterialTheme.colorScheme.outline)
                if (app.vendor != io.github.j0p3s.debloater.data.VendorCatalog.OTHER) Pill(app.vendor, MaterialTheme.colorScheme.tertiary)
                if (app.totalBytes > 0) Pill(sizeLabel(app), MaterialTheme.colorScheme.outline)
            }
            if (showDescription) {
                val desc = app.protectedReason?.let { stringResource(R.string.protected_reason, it) } ?: app.entry?.description
                if (!desc.isNullOrBlank()) {
                    Text(desc, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                app.entry?.warning?.let { WarningText(it) }
                if (!app.entry?.neededBy.isNullOrEmpty()) WarningText(stringResource(R.string.dependency_count, app.entry!!.neededBy.size))
            }
        }
        if (onInfo != null) {
            IconButton(onClick = onInfo) { Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.details)) }
        }
    }
}

@Composable
fun WarningText(text: String) {
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Warning, null, tint = Color(0xFFEF8F00), modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB45F06).let {
            if (isSystemInDarkTheme()) Color(0xFFFFB74D) else it
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        },
        actions = { actions() },
    )
}

/** "1.2 GB" with exact sizes (app + data), or "APK 45 MB" when data size is unknown. */
@Composable
fun sizeLabel(app: AppInfo): String =
    if (app.sizeExact) formatSize(app.totalBytes) else "APK ${formatSize(app.apkSize)}"

/** Used/total bar with free space, for the phone's internal storage. */
@Composable
fun StorageBar(storage: StorageInfo, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { storage.usedFraction },
            modifier = Modifier.fillMaxWidth(),
            color = when {
                storage.usedFraction > 0.9f -> MaterialTheme.colorScheme.error
                storage.usedFraction > 0.75f -> Color(0xFFEF8F00)
                else -> MaterialTheme.colorScheme.primary
            },
        )
        Row {
            Text(
                stringResource(R.string.storage_used, formatSize(storage.usedBytes), formatSize(storage.totalBytes)),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.storage_free, formatSize(storage.freeBytes)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Horizontal row of vendor filter chips. [counts] maps vendor name to number of apps,
 * [selected] null means "All vendors". The detected brand of this phone is listed first.
 */
@Composable
fun VendorChips(
    counts: Map<String, Int>,
    selected: String?,
    deviceVendor: String?,
    onSelect: (String?) -> Unit,
) {
    val ordered = counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.key == deviceVendor }.thenByDescending { it.value })
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            androidx.compose.material3.FilterChip(
                selected = selected == null, onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.all_vendors, counts.values.sum())) },
            )
        }
        items(ordered.size) { i ->
            val (name, n) = ordered[i]
            androidx.compose.material3.FilterChip(
                selected = selected == name,
                onClick = { onSelect(if (selected == name) null else name) },
                label = { Text(if (name == deviceVendor) "📱 $name ($n)" else "$name ($n)") },
            )
        }
    }
}

/** "Sort" button with a menu of [io.github.j0p3s.debloater.data.AppSort] options. */
@Composable
fun SortMenu(sort: io.github.j0p3s.debloater.data.AppSort, onSort: (io.github.j0p3s.debloater.data.AppSort) -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        androidx.compose.material3.TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(sort.titleRes))
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            io.github.j0p3s.debloater.data.AppSort.entries.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(option.titleRes), fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSort(option); open = false },
                    leadingIcon = if (option == sort) ({ Icon(Icons.Default.Check, null) }) else null,
                )
            }
        }
    }
}
