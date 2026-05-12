package dev.truebackup.app.root

data class RootExecutionResult(
    val exitCode: Int,
    val output: String
)

class RootCommandExecutor {
    /** Prefer `su -mm` (mount-master) for Magisk namespace; fall back to `su -c`. */
    fun run(command: String): RootExecutionResult {
        val withMm = runSu(listOf("su", "-mm", "-c", command))
        if (withMm.exitCode == 0) return withMm
        val o = withMm.output.lowercase()
        val badMm = o.contains("invalid option") ||
            o.contains("unrecognized option") ||
            o.contains("illegal option") ||
            withMm.exitCode == 127
        return if (badMm) runSu(listOf("su", "-c", command)) else withMm
    }

    private fun runSu(argv: List<String>): RootExecutionResult {
        val process = ProcessBuilder(argv)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val exit = process.waitFor()
        return RootExecutionResult(
            exitCode = exit,
            output = output
        )
    }
}
