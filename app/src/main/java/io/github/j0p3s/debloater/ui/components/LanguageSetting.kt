package io.github.j0p3s.debloater.ui.components

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import io.github.j0p3s.debloater.R

@Composable
fun LanguageSetting() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected = AppCompatDelegate.getApplicationLocales()[0]?.let { locale ->
        when (locale.language) {
            "zh" -> "zh-CN"
            "in", "id" -> "id"
            else -> locale.language
        }
    }.orEmpty()
    // Autonyms stay readable even if the current app language is unfamiliar.
    val languages = linkedMapOf(
        "" to stringResource(R.string.language_system),
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
        "pt" to "Português",
        "it" to "Italiano",
        "ru" to "Русский",
        "zh-CN" to "简体中文",
        "hi" to "हिन्दी",
        "ar" to "العربية",
        "id" to "Bahasa Indonesia",
    )
    Card {
        ListItem(
            headlineContent = { Text(stringResource(R.string.language_title)) },
            supportingContent = { Text(languages[selected] ?: stringResource(R.string.language_system)) },
            leadingContent = { Icon(Icons.Default.Translate, null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { Icon(Icons.Default.ExpandMore, null) },
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = true },
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(stringResource(R.string.language_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    languages.forEach { (tag, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = { RadioButton(selected == tag, onClick = null) },
                            modifier = Modifier.selectable(
                                selected = selected == tag,
                                role = Role.RadioButton,
                                onClick = {
                                    expanded = false
                                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                                },
                            ),
                        )
                    }
                    Text(stringResource(R.string.source_language), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            },
            confirmButton = { TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}
