package dev.truebackup.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.truebackup.app.root.RootPreflightResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.rootAccessDataStore by preferencesDataStore(name = "truebackup_root_access")

class RootAccessRepository(
    private val context: Context
) {
    private val keySetupComplete = booleanPreferencesKey("root_setup_complete")
    private val keyLastAvailable = booleanPreferencesKey("root_last_available")
    private val keyLastMessage = stringPreferencesKey("root_last_message")
    private val keyLastOutput = stringPreferencesKey("root_last_output")

    val setupComplete: Flow<Boolean> =
        context.rootAccessDataStore.data.map { it[keySetupComplete] == true }

    val cachedResult: Flow<RootPreflightResult?> =
        context.rootAccessDataStore.data.map { prefs ->
            if (prefs[keySetupComplete] != true) return@map null
            RootPreflightResult(
                isRootAvailable = prefs[keyLastAvailable] == true,
                message = prefs[keyLastMessage].orEmpty(),
                output = prefs[keyLastOutput].orEmpty()
            )
        }

    suspend fun saveVerification(result: RootPreflightResult) {
        context.rootAccessDataStore.edit { prefs ->
            prefs[keySetupComplete] = true
            prefs[keyLastAvailable] = result.isRootAvailable
            prefs[keyLastMessage] = result.message
            prefs[keyLastOutput] = result.output
        }
    }
}
