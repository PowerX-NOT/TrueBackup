package dev.truebackup.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Result of a root shell command execution. */
data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val success: Boolean get() = exitCode == 0
    val output: String get() = stdout.trim()
}

/**
 * Coroutine-based root shell executor.
 *
 * All privileged operations use [exec] which runs commands via `su -c "..."`.
 * Root availability is cached after the first check.
 */
object RootShell {

    @Volatile
    private var rootChecked = false

    @Volatile
    private var rootAvailable = false

    /**
     * Returns true if root (`su`) is available on this device.
     * Result is cached after the first invocation.
     */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (rootChecked) return@withContext rootAvailable
        val result = runRaw("id")
        rootAvailable = result.exitCode == 0 && result.stdout.contains("uid=0")
        rootChecked = true
        rootAvailable
    }

    /**
     * Executes [cmd] via `su -c "<cmd>"` and returns stdout/stderr/exitCode.
     *
     * Shell-special characters in [cmd] are the caller's responsibility to escape.
     */
    suspend fun exec(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        runRaw(cmd)
    }

    /**
     * Executes multiple commands in a single `su` session, separated by newlines.
     * More efficient than calling [exec] repeatedly.
     */
    suspend fun execMulti(vararg cmds: String): ShellResult = withContext(Dispatchers.IO) {
        runRaw(cmds.joinToString("\n"))
    }

    /**
     * Quotes [arg] so it is safe to embed in a shell command string.
     * Single-quotes the entire string and escapes embedded single quotes.
     */
    fun quote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun runRaw(cmd: String): ShellResult {
        return try {
            val process = ProcessBuilder("su")
                .redirectErrorStream(false)
                .start()

            process.outputStream.bufferedWriter().use { writer ->
                writer.write(cmd)
                writer.newLine()
                writer.write("exit\n")
                writer.flush()
            }

            val finished = process.waitFor(120, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ShellResult(-1, "", "Timed out after 120s: $cmd")
            }

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            ShellResult(process.exitValue(), stdout, stderr)
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: "Unknown error")
        }
    }
}
