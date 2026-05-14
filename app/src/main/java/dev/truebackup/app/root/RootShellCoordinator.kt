package dev.truebackup.app.root

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Tracks root-policy-sensitive UI refresh and tears down libsu's cached main shell when the app
 * returns from a full stop so Magisk grant/deny changes are observed on the next [Shell.cmd].
 */
object RootShellCoordinator {

    private val _policyGeneration = MutableStateFlow(0L)
    val policyGeneration: StateFlow<Long> = _policyGeneration.asStateFlow()

    /**
     * Closes the cached main shell (if any). Safe when no shell exists. Prefer [Dispatchers.IO].
     */
    fun closeCachedMainShellBlocking() {
        runCatching {
            val shell = Shell.getCachedShell() ?: return
            shell.waitAndClose(3, TimeUnit.SECONDS)
        }
    }

    /**
     * Call after [android.app.Activity.onStart] following [android.app.Activity.onStop] so the
     * next root probe does not reuse a stale `su` session. Bumps [policyGeneration] on success.
     */
    suspend fun resetShellAfterFullBackgroundCycle() {
        withContext(Dispatchers.IO) {
            closeCachedMainShellBlocking()
        }
        _policyGeneration.update { it + 1 }
    }
}
