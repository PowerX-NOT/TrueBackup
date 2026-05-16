package dev.truebackup.app

import android.app.Application
import dev.truebackup.app.root.RootShellClient

class TrueBackupApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RootShellClient.init(this)
    }
}
