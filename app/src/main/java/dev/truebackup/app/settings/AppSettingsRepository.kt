package dev.truebackup.app.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
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

    val backupBasePath: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[keyBackupBasePath] }

    suspend fun setBackupBasePath(path: String?) {
        context.dataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(keyBackupBasePath)
            } else {
                prefs[keyBackupBasePath] = path
            }
        }
    }
}

