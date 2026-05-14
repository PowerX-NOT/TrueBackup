package dev.truebackup.app

import android.app.Application
import android.content.pm.ApplicationInfo
import com.topjohnwu.superuser.Shell

class TrueBackupApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Shell.enableVerboseLogging = debuggable
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(120)
        )
        // Pre-warm the root shell in the background so the splash screen doesn't block
        // waiting for the first su handshake.
        Thread {
            Shell.getCachedShell()
        }.start()
    }
}
