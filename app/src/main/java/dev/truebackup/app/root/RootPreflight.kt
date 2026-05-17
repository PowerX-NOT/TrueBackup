package dev.truebackup.app.root

import com.topjohnwu.superuser.NoShellException
import com.topjohnwu.superuser.Shell

data class RootPreflightResult(
    val isRootAvailable: Boolean,
    val message: String
)

class RootPreflight {
    /**
     * @param forceFresh When true, always run `id -u` (caller should have called
     * [RootShellClient.prepareForAccessCheck] first). Skips the fast "no su binary" shortcut.
     */
    fun verify(forceFresh: Boolean = false): RootPreflightResult {
        if (!forceFresh && Shell.isAppGrantedRoot() == false) {
            return RootPreflightResult(
                isRootAvailable = false,
                message = "Root is not available on this device."
            )
        }
        return runCatching {
            val result = Shell.cmd("id -u").exec()
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
            val uidLine = merged.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
            val isRoot = result.code == 0 && uidLine == "0"
            if (isRoot) {
                RootPreflightResult(
                    isRootAvailable = true,
                    message = "Root access available."
                )
            } else {
                val detail = when {
                    result.code != 0 -> "exit=${result.code}"
                    uidLine.isEmpty() -> "empty output"
                    else -> "uid=$uidLine"
                }
                val extra = if (merged.isBlank()) detail else "$merged ($detail)"
                RootPreflightResult(
                    isRootAvailable = false,
                    message = "Root is not available (or Magisk denied this app). $extra"
                )
            }
        }.getOrElse { error ->
            RootPreflightResult(
                isRootAvailable = false,
                message = rootCheckFailureMessage(error)
            )
        }
    }
}

private fun rootCheckFailureMessage(error: Throwable): String = when (error) {
    is NoShellException -> {
        val detail = error.message?.trim().orEmpty()
        if (detail.isEmpty()) {
            "Root is not available (or Magisk denied this app)."
        } else {
            "Root is not available (or Magisk denied this app). $detail"
        }
    }
    else -> "Root check failed: ${error.message ?: "unknown error"}"
}
