package dev.truebackup.app.root

data class RootExecutionResult(
    val exitCode: Int,
    val output: String
)

/** Runs commands in the persistent root daemon ([RootShellClient]), not in the UI process. */
class RootCommandExecutor {
    fun run(command: String): RootExecutionResult = RootShellClient.execute(command)
}
