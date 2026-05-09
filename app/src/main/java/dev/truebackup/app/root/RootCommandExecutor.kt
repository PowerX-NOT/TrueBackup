package dev.truebackup.app.root

data class RootExecutionResult(
    val exitCode: Int,
    val output: String
)

class RootCommandExecutor {
    fun run(command: String): RootExecutionResult {
        val process = ProcessBuilder("su", "-c", command)
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
