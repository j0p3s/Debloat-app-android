package io.github.j0p3s.debloater.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.j0p3s.debloater.R

const val SUPPORT_URL = "https://buymeacoffee.com/j0p3s"

fun openSupportPage(ctx: Context) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL)))
    } catch (_: ActivityNotFoundException) {
    }
}

/** Shown after an install or update: asks for support (with a "don't show again" option) and links to Buy Me a Coffee. */
@Composable
fun SplashScreen(onContinue: (dontShowAgain: Boolean) -> Unit) {
    val ctx = LocalContext.current
    var dontShow by rememberSaveable { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(32.dp))
            Icon(Icons.Filled.Coffee, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.splash_support_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.splash_support_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { openSupportPage(ctx) },
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            ) { Text(stringResource(R.string.splash_coffee)) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().clickable { dontShow = !dontShow }.padding(top = 8.dp),
            ) {
                Checkbox(checked = dontShow, onCheckedChange = { dontShow = it })
                Text(stringResource(R.string.splash_dont_show), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(
                onClick = { onContinue(dontShow) },
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(top = 8.dp),
            ) { Text(stringResource(R.string.splash_continue)) }
        }
    }
}
