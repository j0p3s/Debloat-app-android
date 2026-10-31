package io.github.j0p3s.debloater.ui.components

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
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
    val selected = AppCompatDelegate.getApplicationLocales()[0]?.language.orEmpty()
    // Autonyms stay readable even if the current app language is unfamiliar.
    val languages = linkedMapOf(
        "" to stringResource(R.string.language_system),
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
    )
    Card {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                Text(stringResource(R.string.language_title))
                Text(languages[selected] ?: stringResource(R.string.language_system))
            }
        }
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
                }
            },
            confirmButton = { TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}
