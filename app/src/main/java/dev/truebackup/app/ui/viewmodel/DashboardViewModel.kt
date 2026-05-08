package dev.truebackup.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.truebackup.app.data.BackupEntry
import dev.truebackup.app.data.BackupRepository
import dev.truebackup.app.engine.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isRooted: Boolean = false,
    val activeOperation: String? = null,
    val progress: Float = 0f,
    val recentBackups: List<BackupEntry> = emptyList(),
    val totalBackups: Int = 0,
    val totalSizeBytes: Long = 0L,
    val lastBackupAt: Long = 0L,
    val activityLog: List<String> = emptyList()
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val passwordManager = PasswordManager(app)
    private val backupRepo = BackupRepository()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val rooted = RootShell.isRootAvailable()
            val basePath = passwordManager.getBackupBasePath()
            val backups = try { backupRepo.listBackups(basePath) } catch (_: Exception) { emptyList() }
            _uiState.update {
                it.copy(
                    isRooted = rooted,
                    recentBackups = backups.take(10),
                    totalBackups = backups.size,
                    totalSizeBytes = backups.sumOf { b -> b.totalBytes },
                    lastBackupAt = backups.maxOfOrNull { b -> b.backedUpAt } ?: 0L
                )
            }
        }
    }

    fun appendLog(line: String) {
        _uiState.update { it.copy(activityLog = (it.activityLog + line).takeLast(100)) }
    }

    fun setOperation(label: String?, progress: Float) {
        _uiState.update { it.copy(activeOperation = label, progress = progress) }
    }
}
