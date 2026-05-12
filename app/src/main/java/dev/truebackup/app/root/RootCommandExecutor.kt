package dev.truebackup.app.root

data class RootExecutionResult(
    val exitCode: Int,
    val output: String
)

class RootCommandExecutor {
    /**
     * Magisk: `su -c` can run in an isolated mount namespace so `/data/user/0/<pkg>` is invisible
     * even though an interactive root shell sees it. `-mm` = mount-master (global namespace).
     * Falls back to plain `su -c` if `-mm` is not supported.
     */
    fun run(command: String): RootExecutionResult {
        val withMm = runSu(listOf("su", "-mm", "-c", command))
        if (withMm.exitCode == 0) return withMm
        val o = withMm.output.lowercase()
        val mmRejected =
            o.contains("invalid option") ||
                o.contains("unrecognized option") ||
                o.contains("illegal option") ||
                withMm.exitCode == 127
        if (mmRejected || withMm.exitCode == 127) {
            return runSu(listOf("su", "-c", command))
        }
        return withMm
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
