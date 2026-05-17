package dev.truebackup.app.root

import dev.truebackup.app.settings.RootAccessRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Re-validates root access and persists the result for UI observers. */
object RootAccessProbe {

    private val mutex = Mutex()

    suspend fun probeAndPersist(
        rootAccessRepo: RootAccessRepository,
        preflight: RootPreflight = RootPreflight()
    ): RootPreflightResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            RootShellClient.prepareForAccessCheck()
            preflight.verify(forceFresh = true)
        }.also { rootAccessRepo.saveVerification(it) }
    }
}
