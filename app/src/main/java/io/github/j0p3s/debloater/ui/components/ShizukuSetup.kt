package io.github.j0p3s.debloater.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.shizuku.ShizukuManager
import io.github.j0p3s.debloater.shizuku.ShizukuStatus
import io.github.j0p3s.debloater.ui.theme.OkColor

/**
 * Step-by-step Shizuku setup for beginners. Each step shows a tick when done; the first unfinished
 * step is highlighted and has the buttons that help the user do it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShizukuSetupCard(status: ShizukuStatus, uid: Int, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    if (status == ShizukuStatus.READY) {
        Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = OkColor, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.guide_shizuku_connected), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.guide_shizuku_running_as,
                            stringResource(if (uid == 0) R.string.guide_mode_root else R.string.guide_mode_adb),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        return
    }

    val installed = status != ShizukuStatus.NOT_INSTALLED
    val running = installed && status != ShizukuStatus.NOT_RUNNING
    // Wireless debugging cannot be detected directly; treat it as done once Shizuku runs.
    val current = when {
        !installed -> 1
        !running -> 2
        else -> 4
    }
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.guide_shizuku_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.guide_shizuku_why), style = MaterialTheme.typography.bodyMedium)

            SetupStep(1, stringResource(R.string.guide_step1_title), stringResource(R.string.guide_step1_text), done = installed, active = current == 1) {
                Button(onClick = {
                    open(ctx, "market://details?id=${ShizukuManager.SHIZUKU_PACKAGE}", "https://play.google.com/store/apps/details?id=${ShizukuManager.SHIZUKU_PACKAGE}")
                }) { Text(stringResource(R.string.play_store)) }
                OutlinedButton(onClick = { open(ctx, "https://github.com/RikkaApps/Shizuku/releases", null) }) { Text(stringResource(R.string.github)) }
            }
            SetupStep(2, stringResource(R.string.guide_step2_title), stringResource(R.string.guide_step2_text), done = running, active = current == 2) {
                OutlinedButton(onClick = {
                    if (!startSettings(ctx, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) startSettings(ctx, Settings.ACTION_DEVICE_INFO_SETTINGS)
                }) { Text(stringResource(R.string.guide_open_dev_options)) }
                TextButton(onClick = { startSettings(ctx, Settings.ACTION_DEVICE_INFO_SETTINGS) }) { Text(stringResource(R.string.guide_open_about_phone)) }
            }
            SetupStep(3, stringResource(R.string.guide_step3_title), stringResource(R.string.guide_step3_text), done = running, active = current == 2) {
                Button(onClick = {
                    ctx.packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)?.let { ctx.startActivity(it) }
                }, enabled = installed) { Text(stringResource(R.string.open_shizuku)) }
            }
            SetupStep(4, stringResource(R.string.guide_step4_title), stringResource(R.string.guide_step4_text), done = false, active = current == 4) {
                Button(onClick = { ShizukuManager.requestPermission() }, enabled = running) { Text(stringResource(R.string.guide_allow)) }
            }
            when (status) {
                ShizukuStatus.PERMISSION_DENIED -> Text(stringResource(R.string.shizuku_denied_help), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                ShizukuStatus.OUTDATED -> Text(stringResource(R.string.shizuku_update_help), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                else -> {}
            }
            TextButton(onClick = onRefresh) { Text(stringResource(R.string.guide_check_again)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupStep(
    number: Int,
    title: String,
    text: String,
    done: Boolean,
    active: Boolean,
    actions: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when {
                        done -> OkColor
                        active -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Default.Check, stringResource(R.string.guide_step_done), tint = Color.White, modifier = Modifier.size(18.dp))
            else Text("$number", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (!done) {
                Text(text, style = MaterialTheme.typography.bodySmall)
                if (active) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
        }
    }
}

private fun startSettings(ctx: Context, action: String): Boolean =
    runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

fun open(ctx: Context, uri: String, fallback: String?) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        fallback?.let { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}
