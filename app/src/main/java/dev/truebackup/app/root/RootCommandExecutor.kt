package dev.truebackup.app.root

import com.topjohnwu.superuser.NoShellException
import com.topjohnwu.superuser.Shell

data class RootExecutionResult(
    val exitCode: Int,
    val output: String
)

/**
 * Runs privileged shell commands via [topjohnwu libsu](https://github.com/topjohnwu/libsu)
 * (`Shell.cmd`), using mount-master when the root implementation supports it.
 */
class RootCommandExecutor {
    fun run(command: String): RootExecutionResult {
        return try {
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
            RootExecutionResult(
                exitCode = result.code,
                output = merged
            )
        } catch (e: NoShellException) {
            RootExecutionResult(
                exitCode = 127,
                output = (e.message ?: e.toString()).trim()
            )
        }
    }
}
