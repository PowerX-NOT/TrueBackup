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
            val uidLine = result.output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
            val isRoot = result.exitCode == 0 && uidLine == "0"
            if (isRoot) {
                RootPreflightResult(
                    isRootAvailable = true,
                    message = "Root access available.",
                    output = uidLine.ifEmpty { result.output }
                )
            } else {
                val detail = when {
                    result.exitCode != 0 -> "exit=${result.exitCode}"
                    uidLine.isEmpty() -> "empty output"
                    else -> "uid=$uidLine"
                }
                RootPreflightResult(
                    isRootAvailable = false,
                    message = "Root is not available (or Magisk denied this app).",
                    output = if (result.output.isBlank()) detail else "${result.output.trim()} ($detail)"
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
