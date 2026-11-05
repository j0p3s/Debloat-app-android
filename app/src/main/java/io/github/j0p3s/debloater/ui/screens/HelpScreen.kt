package io.github.j0p3s.debloater.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.BackTopBar

private val FAQ = listOf(
    R.string.guide_help_q_what to R.string.guide_help_a_what,
    R.string.guide_help_q_safe to R.string.guide_help_a_safe,
    R.string.guide_help_q_disable_remove to R.string.guide_help_a_disable_remove,
    R.string.guide_help_q_test to R.string.guide_help_a_test,
    R.string.guide_help_q_undo to R.string.guide_help_a_undo,
    R.string.guide_help_q_shizuku to R.string.guide_help_a_shizuku,
    R.string.guide_help_q_reboot to R.string.guide_help_a_reboot,
    R.string.guide_help_q_tiers to R.string.guide_help_a_tiers,
    R.string.guide_help_q_space to R.string.guide_help_a_space,
    R.string.guide_help_q_problem to R.string.guide_help_a_problem,
    R.string.guide_help_q_privacy to R.string.guide_help_a_privacy,
)

/** Plain-language answers to the questions beginners ask most. */
@Composable
fun HelpScreen(vm: MainViewModel) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.guide_help_title), onBack = { vm.back() }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FAQ.forEachIndexed { i, (q, a) -> FaqItem(stringResource(q), stringResource(a), initiallyOpen = i == 0) }
            OutlinedButton(onClick = { vm.prefs.setOnboardingDone(false) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.guide_help_show_welcome))
            }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String, initiallyOpen: Boolean) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Card(Modifier.fillMaxWidth().clickable { open = !open }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (open) Text(answer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
