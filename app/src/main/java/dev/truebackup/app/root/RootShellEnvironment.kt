package dev.truebackup.app.root

import com.topjohnwu.superuser.Shell

/** libsu builder used by the root daemon process ([TrueBackupRootService]). */
object RootShellEnvironment {

    fun configure(debuggable: Boolean) {
        Shell.enableVerboseLogging = debuggable
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(120)
        )
    }
}
