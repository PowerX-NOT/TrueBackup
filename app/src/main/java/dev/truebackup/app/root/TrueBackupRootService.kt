package dev.truebackup.app.root

import android.content.Intent
import android.os.IBinder
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService

/**
 * libsu daemon-mode root process. Keeps one [Shell] session alive across app process death
 * so backup/restore after swipe-away does not spawn a new `su` in the UI process.
 */
class TrueBackupRootService : RootService() {

    override fun onBind(intent: Intent): IBinder = RootCommandBinder()

    private class RootCommandBinder : IRootCommandService.Stub() {
        override fun execute(command: String): ShellResultParcelable {
            val result = Shell.cmd(command).exec()
            val merged = buildString {
                for (line in result.out) {
                    if (isNotEmpty()) append('\n')
                    append(line)
                }
                for (line in result.err) {
                    if (isNotEmpty()) append('\n')
                    append(line)
                }
            }.trim()
            return ShellResultParcelable(code = result.code, output = merged)
        }
    }
}
