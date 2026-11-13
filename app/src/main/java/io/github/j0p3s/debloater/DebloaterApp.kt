package io.github.j0p3s.debloater

import android.app.Application
import io.github.j0p3s.debloater.shizuku.ShizukuManager

class DebloaterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        io.github.j0p3s.debloater.debug.DebugLog.init(this)
        ShizukuManager.init(this)
    }
}
