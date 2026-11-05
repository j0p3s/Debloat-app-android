package io.github.j0p3s.debloater.ui.components

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Android supplies localized units and decimal separators for the selected app language. */
@Composable
fun formatSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)
