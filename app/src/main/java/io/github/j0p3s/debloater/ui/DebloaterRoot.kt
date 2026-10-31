package io.github.j0p3s.debloater.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.j0p3s.debloater.ui.components.ConfirmOperationDialog
import io.github.j0p3s.debloater.ui.screens.AppManagerScreen
import io.github.j0p3s.debloater.ui.screens.BackupsScreen
import io.github.j0p3s.debloater.ui.screens.DebloatScreen
import io.github.j0p3s.debloater.ui.screens.HomeScreen
import io.github.j0p3s.debloater.ui.screens.OperationScreen
import io.github.j0p3s.debloater.ui.screens.SettingsScreen
import android.widget.Toast

@Composable
fun DebloaterRoot(vm: MainViewModel) {
    val stack by vm.backStack.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    BackHandler {
        if (!vm.back()) (ctx as? Activity)?.finish()
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_LONG).show()
            vm.consumeMessage()
        }
    }

    when (stack.last()) {
        Screen.HOME -> HomeScreen(vm)
        Screen.DEBLOAT -> DebloatScreen(vm)
        Screen.APP_MANAGER -> AppManagerScreen(vm)
        Screen.OPERATION -> OperationScreen(vm)
        Screen.BACKUPS -> BackupsScreen(vm)
        Screen.SETTINGS -> SettingsScreen(vm)
    }

    pending?.let { p ->
        val keepDefault by vm.prefs.keepData.collectAsStateWithLifecycle()
        ConfirmOperationDialog(
            pending = p,
            keepDataDefault = keepDefault,
            onDismiss = vm::dismissPending,
            onConfirm = { keepData, dryRun, action -> vm.confirmPending(keepData, dryRun, action) },
        )
    }
}
