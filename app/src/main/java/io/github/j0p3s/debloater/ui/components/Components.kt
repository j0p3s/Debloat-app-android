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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.j0p3s.debloater.data.AppInfo
import io.github.j0p3s.debloater.data.AppState
import io.github.j0p3s.debloater.data.SafetyLevel
import io.github.j0p3s.debloater.ops.OperationEngine
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
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun LevelPill(level: SafetyLevel) = Pill(level.title, levelColor(level, isSystemInDarkTheme()))

@Composable
fun StatePill(state: AppState) {
    val c = when (state) {
        AppState.ENABLED -> MaterialTheme.colorScheme.primary
        AppState.DISABLED -> Color(0xFFEF8F00)
        AppState.UNINSTALLED -> MaterialTheme.colorScheme.error
    }
    Pill(state.title, c)
}

@Composable
fun AppRow(
    app: AppInfo,
    selected: Boolean,
    selectable: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    showDescription: Boolean = true,
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
                Icon(Icons.Default.Lock, contentDescription = "Locked", tint = MaterialTheme.colorScheme.outline)
            }
        }
        AppIcon(app.applicationInfo, Modifier.padding(top = 4.dp).size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.entry?.name?.takeIf { app.label == app.packageName } ?: app.label,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                LevelPill(app.level)
                if (app.state != AppState.ENABLED) StatePill(app.state)
                Pill(if (app.isSystem) "System" else "User", MaterialTheme.colorScheme.outline)
                if (app.apkSize > 0) Pill(OperationEngine.fmtSize(app.apkSize), MaterialTheme.colorScheme.outline)
            }
            if (showDescription) {
                val desc = app.protectedReason?.let { "Protected: $it" } ?: app.entry?.description
                if (!desc.isNullOrBlank()) {
                    Text(desc, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                app.entry?.warning?.let { WarningText(it) }
            }
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
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        },
        actions = { actions() },
    )
}
