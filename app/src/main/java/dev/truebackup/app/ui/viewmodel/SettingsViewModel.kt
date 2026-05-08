package dev.truebackup.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.truebackup.app.engine.PasswordManager
import dev.truebackup.app.engine.RootShell
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isPasswordSet: Boolean = false,
    val backupPath: String = PasswordManager.DEFAULT_BACKUP_PATH,
    val isRooted: Boolean = false,
    val statusMessage: String? = null
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val passwordManager = PasswordManager(app)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val rooted = RootShell.isRootAvailable()
            _uiState.update {
                it.copy(
                    isPasswordSet = passwordManager.isPasswordSet(),
                    backupPath = passwordManager.getBackupBasePath(),
                    isRooted = rooted
                )
            }
        }
    }

    fun setPassword(newPassword: String) {
        val ok = passwordManager.setPassword(newPassword)
        _uiState.update { it.copy(isPasswordSet = ok, statusMessage = if (ok) "Password set" else "Failed to set password") }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        val ok = passwordManager.changePassword(oldPassword, newPassword)
        _uiState.update { it.copy(statusMessage = if (ok) "Password changed" else "Old password incorrect") }
    }

    fun clearPassword() {
        passwordManager.clearPassword()
        _uiState.update { it.copy(isPasswordSet = false, statusMessage = "Password cleared") }
    }

    fun setBackupPath(path: String) {
        passwordManager.setBackupBasePath(path)
        _uiState.update { it.copy(backupPath = path, statusMessage = "Backup path updated") }
    }
}
