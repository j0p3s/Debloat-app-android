package io.github.j0p3s.debloater

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.j0p3s.debloater.ui.DebloaterRoot
import io.github.j0p3s.debloater.ui.MainViewModel
import io.github.j0p3s.debloater.ui.theme.DebloaterTheme

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshShizuku()
        })
        setContent {
            DebloaterTheme { DebloaterRoot(vm) }
        }
    }
}
