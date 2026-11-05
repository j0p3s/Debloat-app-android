package io.github.j0p3s.debloater.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.j0p3s.debloater.R

/** Actions wrap at narrow widths and large font sizes, including longer translations. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectionBar(
    count: Int,
    size: String,
    onTest: () -> Unit,
    onDisable: () -> Unit,
    onRemove: () -> Unit,
    onRestore: (() -> Unit)? = null,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.selected_count, count) + " · " + size,
                style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTest) { Text(stringResource(R.string.test_first)) }
                OutlinedButton(onClick = onDisable) { Text(stringResource(R.string.disable)) }
                if (onRestore != null) {
                    OutlinedButton(onClick = onRestore) { Text(stringResource(R.string.restore)) }
                }
                OutlinedButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.remove)) }
            }
        }
    }
}
