package io.github.j0p3s.debloater.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.data.Action
import io.github.j0p3s.debloater.ops.LineKind
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.BackTopBar
import io.github.j0p3s.debloater.ui.theme.ErrorColor
import io.github.j0p3s.debloater.ui.theme.OkColor
import io.github.j0p3s.debloater.ui.theme.WarnColor

@Composable
fun OperationScreen(vm: MainViewModel) {
    val op by vm.operation.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val s = op ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(s.lines.size) {
        if (s.lines.isNotEmpty()) listState.animateScrollToItem(s.lines.size - 1)
    }

    Scaffold(
        topBar = {
            BackTopBar(s.title, onBack = { vm.back() }) {
                if (s.finished) {
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, vm.logText())
                        ctx.startActivity(Intent.createChooser(send, "Share log"))
                    }) { Icon(Icons.Default.Share, "Share log") }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.dryRun) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text("TEST MODE: the process is simulated and checked, and nothing on your device is changed.", Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
                }
            }
            LinearProgressIndicator(
                progress = { if (s.total == 0) 1f else s.processed / s.total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (s.finished) "Done: ${s.processed}/${s.total}" else "Working on ${s.current ?: "…"} (${s.processed}/${s.total})",
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("✔ ${s.ok}", color = OkColor)
                Text("⏭ ${s.skipped}")
                Text("⚠ ${s.warnings}", color = WarnColor)
                Text("✖ ${s.failed}", color = ErrorColor)
            }

            Card(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    itemsIndexed(s.lines) { _, line ->
                        val color = when (line.kind) {
                            LineKind.OK -> OkColor
                            LineKind.WARN -> WarnColor
                            LineKind.ERROR -> ErrorColor
                            LineKind.COMMAND -> MaterialTheme.colorScheme.primary
                            LineKind.OUTPUT -> MaterialTheme.colorScheme.onSurfaceVariant
                            LineKind.STEP -> MaterialTheme.colorScheme.onSurface
                            LineKind.INFO -> MaterialTheme.colorScheme.onSurface
                        }
                        val prefix = when (line.kind) {
                            LineKind.OK -> "✔ "; LineKind.WARN -> "⚠ "; LineKind.ERROR -> "✖ "
                            LineKind.COMMAND -> ""; LineKind.OUTPUT -> "  │ "; LineKind.STEP -> "▶ "; LineKind.INFO -> "• "
                        }
                        Text(
                            prefix + line.text,
                            color = color,
                            fontFamily = if (line.kind == LineKind.COMMAND || line.kind == LineKind.OUTPUT) FontFamily.Monospace else FontFamily.Default,
                            fontWeight = if (line.kind == LineKind.STEP) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = if (line.kind == LineKind.STEP) 8.dp else 1.dp),
                        )
                    }
                }
            }

            if (!s.finished) {
                OutlinedButton(onClick = vm::cancelOperation, modifier = Modifier.fillMaxWidth()) { Text("Cancel after the current app") }
            } else {
                val passed = s.failed == 0
                Card(colors = CardDefaults.cardColors(containerColor = (if (passed) OkColor else ErrorColor).copy(alpha = 0.15f))) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            when {
                                s.dryRun && passed -> "Test passed: no errors. Nothing was changed."
                                s.dryRun -> "Test found ${s.failed} problem(s). Nothing was changed."
                                passed -> "Completed successfully"
                                else -> "Completed with ${s.failed} error(s)"
                            },
                            fontWeight = FontWeight.Bold,
                            color = if (passed) OkColor else ErrorColor,
                        )
                        if (!s.dryRun && s.sessionId != null) {
                            Text("Backup saved as ${s.sessionId}. You can restore it in Backups & restore.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { vm.back() }, modifier = Modifier.weight(1f)) { Text("Close") }
                    if (s.dryRun && s.ok > 0) {
                        val label = when (s.action) {
                            Action.RESTORE -> "Restore now"
                            else -> "Continue…"
                        }
                        Button(
                            onClick = vm::runTestedForReal,
                            modifier = Modifier.weight(1f),
                            colors = if (s.failed > 0) ButtonDefaults.buttonColors(containerColor = Color(0xFFEF8F00)) else ButtonDefaults.buttonColors(),
                        ) { Text(label) }
                    }
                }
            }
        }
    }
}
