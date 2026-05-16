package dev.truebackup.app

import android.app.Application
import android.content.pm.ApplicationInfo
import dev.truebackup.app.root.RootShellClient
import dev.truebackup.app.root.RootShellEnvironment

class TrueBackupApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        RootShellEnvironment.configure(debuggable)
        RootShellClient.init(this)
    }
}
