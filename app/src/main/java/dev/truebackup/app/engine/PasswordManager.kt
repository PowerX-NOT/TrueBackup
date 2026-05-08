package dev.truebackup.app.engine

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Password manager backed by EncryptedSharedPreferences (AES-256-GCM Keystore key).
 * The registration password is the plain-text password used for TBK1 PBKDF2 derivation.
 */
class PasswordManager(context: Context) {

    private val TAG = "PasswordManager"
    private val PREF_FILE = "truebackup_secure_prefs"
    private val KEY_PASSWORD = "registration_password"
    private val KEY_BACKUP_PATH = "backup_base_path"

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREF_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isPasswordSet(): Boolean = prefs.contains(KEY_PASSWORD)

    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)
        ?.takeIf { it.isNotEmpty() }

    fun setPassword(password: String): Boolean {
        if (password.isEmpty()) return false
        prefs.edit().putString(KEY_PASSWORD, password).apply()
        Log.i(TAG, "Registration password set")
        return true
    }

    fun clearPassword(): Boolean {
        prefs.edit().remove(KEY_PASSWORD).apply()
        Log.i(TAG, "Registration password cleared")
        return true
    }

    fun changePassword(oldPassword: String, newPassword: String): Boolean {
        val current = getPassword() ?: return false
        if (current != oldPassword) {
            Log.w(TAG, "changePassword: old password mismatch")
            return false
        }
        return setPassword(newPassword)
    }

    // ---- Backup path preference ----

    fun getBackupBasePath(): String =
        prefs.getString(KEY_BACKUP_PATH, DEFAULT_BACKUP_PATH) ?: DEFAULT_BACKUP_PATH

    fun setBackupBasePath(path: String) {
        prefs.edit().putString(KEY_BACKUP_PATH, path).apply()
    }

    companion object {
        const val DEFAULT_BACKUP_PATH = "/sdcard/TrueBackup"
    }
}
