package io.github.j0p3s.debloater.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.R
import io.github.j0p3s.debloater.shizuku.ShizukuStatus
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.components.ShizukuSetupCard

/** First-launch welcome: language, how the app works, and Shizuku setup. */
@Composable
fun OnboardingScreen(vm: MainViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val status by vm.shizukuStatus.collectAsStateWithLifecycle()
    val uid by vm.shizukuUid.collectAsStateWithLifecycle()
    val pages = 3
    val finish = { vm.prefs.setOnboardingDone(true) }
    BackHandler(enabled = page > 0) { page-- }

    Scaffold(
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (page > 0) OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.guide_back), textAlign = TextAlign.Center)
                } else TextButton(onClick = finish, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.guide_skip), textAlign = TextAlign.Center)
                }
                if (page < pages - 1) {
                    Button(onClick = { page++ }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.guide_next), textAlign = TextAlign.Center)
                    }
                } else {
                    Button(onClick = finish, modifier = Modifier.weight(1f)) {
                        Text(stringResource(if (status == ShizukuStatus.READY) R.string.guide_finish else R.string.guide_later), textAlign = TextAlign.Center)
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LinearProgressIndicator(progress = { (page + 1) / pages.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.guide_page, page + 1, pages), style = MaterialTheme.typography.labelMedium)
            when (page) {
                0 -> WelcomePage()
                1 -> HowPage()
                else -> {
                    Text(stringResource(R.string.guide_setup_title), style = MaterialTheme.typography.headlineSmall)
                    ShizukuSetupCard(status, uid, onRefresh = { vm.refreshShizuku() })
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Icon(Icons.Default.AutoFixHigh, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
    Text(stringResource(R.string.guide_welcome_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.guide_welcome_text), style = MaterialTheme.typography.bodyLarge)
    // The same picker as in Settings (AppCompat per-app language, all Android versions).
    io.github.j0p3s.debloater.ui.components.LanguageSetting()
}

@Composable
private fun HowPage() {
    Text(stringResource(R.string.guide_how_title), style = MaterialTheme.typography.headlineSmall)
    HowCard(Icons.Default.Science, stringResource(R.string.guide_how_1_title), stringResource(R.string.guide_how_1_text))
    HowCard(Icons.Default.ToggleOff, stringResource(R.string.guide_how_2_title), stringResource(R.string.guide_how_2_text))
    HowCard(Icons.Default.Restore, stringResource(R.string.guide_how_3_title), stringResource(R.string.guide_how_3_text))
}

@Composable
private fun HowCard(icon: ImageVector, title: String, text: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
