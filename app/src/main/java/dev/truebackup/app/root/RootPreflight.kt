package dev.truebackup.app.root

data class RootPreflightResult(
    val isRootAvailable: Boolean,
    val message: String,
    val output: String
)

class RootPreflight(
    private val executor: RootCommandExecutor = RootCommandExecutor()
) {
    fun verify(): RootPreflightResult {
        return runCatching {
            val result = executor.run("id -u")
            val isRoot = result.exitCode == 0 && result.output == "0"
            if (isRoot) {
                RootPreflightResult(
                    isRootAvailable = true,
                    message = "Root access available.",
                    output = result.output
                )
            } else {
                RootPreflightResult(
                    isRootAvailable = false,
                    message = "Root shell responded, but uid is not 0.",
                    output = if (result.output.isBlank()) "exit=${result.exitCode}" else result.output
                )
            }
        }.getOrElse { error ->
            RootPreflightResult(
                isRootAvailable = false,
                message = "Root check failed: ${error.message ?: "unknown error"}",
                output = ""
            )
        }
    }
}
