package dev.truebackup.app.root

import android.content.Context
object RootSessionCoordinator {

    fun ensureSessionAfterSetup(context: Context) {
        RootSessionService.start(context.applicationContext)
    }

    fun stopSession(context: Context) {
        RootSessionService.stop(context.applicationContext)
    }
}
