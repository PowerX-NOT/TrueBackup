package dev.truebackup.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "truebackup_settings")

class AppSettingsRepository(
    private val context: Context
) {
    private val keyBackupBasePath = stringPreferencesKey("backup_base_path")
    private val keyBackupEncryptionEnabled = booleanPreferencesKey("backup_encryption_enabled")

    val backupBasePath: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[keyBackupBasePath] }

    /** When true, new interop backups TBK1-encrypt part zips using [RegistrationPasswordStore] plaintext. */
    val backupEncryptionEnabled: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[keyBackupEncryptionEnabled] == true }

    suspend fun setBackupBasePath(path: String?) {
        context.dataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(keyBackupBasePath)
            } else {
                prefs[keyBackupBasePath] = path
            }
        }
    }

    suspend fun setBackupEncryptionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            if (enabled) {
                prefs[keyBackupEncryptionEnabled] = true
            } else {
                prefs.remove(keyBackupEncryptionEnabled)
            }
        }
    }
}

