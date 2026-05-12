package dev.truebackup.app.settings

/**
 * Holds [PasswordRekeyPending] in memory while navigating from Settings to the re-encrypt process screen.
 * Passwords must not be placed in [androidx.navigation.NavBackStackEntry] saved state.
 */
data class PasswordRekeyPending(
    val backupBasePath: String,
    val oldPassword: String,
    val newPassword: String,
)

object PasswordChangeRekeySession {

    private val lock = Any()
    private var pending: PasswordRekeyPending? = null

    fun prepare(backupBasePath: String, oldPassword: String, newPassword: String) {
        synchronized(lock) {
            pending = PasswordRekeyPending(
                backupBasePath = backupBasePath.trim().trimEnd('/'),
                oldPassword = oldPassword,
                newPassword = newPassword
            )
        }
    }

    /** Returns the pending payload and clears it so it is single-use. */
    fun take(): PasswordRekeyPending? = synchronized(lock) {
        val p = pending
        pending = null
        p
    }

    fun clear() {
        synchronized(lock) { pending = null }
    }
}
