package dev.truebackup.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.truebackup.app.data.BackupEntry
import dev.truebackup.app.data.BackupRepository
import dev.truebackup.app.engine.PasswordManager
import dev.truebackup.app.engine.RestoreEngine
import dev.truebackup.app.ui.screen.OperationStep
import dev.truebackup.app.ui.screen.StepStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RestoreUiState(
    val backupPath: String = PasswordManager.DEFAULT_BACKUP_PATH,
    val backups: List<BackupEntry> = emptyList(),
    val isLoading: Boolean = false
)

class RestoreViewModel(app: Application) : AndroidViewModel(app) {

    private val backupRepo = BackupRepository()
    private val passwordManager = PasswordManager(app)
    private val restoreEngine = RestoreEngine(app)

    private val _uiState = MutableStateFlow(RestoreUiState(backupPath = passwordManager.getBackupBasePath()))
    val uiState: StateFlow<RestoreUiState> = _uiState.asStateFlow()

    private val _operationState = MutableStateFlow(OperationUiState())
    val operationState: StateFlow<OperationUiState> = _operationState.asStateFlow()

    init { loadBackups() }

    fun loadBackups() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val path = _uiState.value.backupPath
            val list = try { backupRepo.listBackups(path) } catch (_: Exception) { emptyList() }
            _uiState.update { it.copy(backups = list, isLoading = false) }
        }
    }

    fun setPath(path: String) {
        passwordManager.setBackupBasePath(path)
        _uiState.update { it.copy(backupPath = path) }
        loadBackups()
    }

    fun restoreEntry(entry: BackupEntry) {
        viewModelScope.launch {
            val password = passwordManager.getPassword()
            val stepLabels = listOf(
                "Installing APK", "Restoring CE data", "Restoring DE data",
                "Restoring external data", "Restoring OBB", "Restoring media",
                "Restoring permissions", "Finished"
            )
            val steps = stepLabels.mapIndexed { i, s -> OperationStep(i, s, StepStatus.PENDING) }.toMutableList()
            _operationState.value = OperationUiState(
                isVisible = true, operationLabel = "Restoring ${entry.packageName}",
                packageName = entry.packageName, steps = steps.toList()
            )
            restoreEngine.restore(entry.packageName, entry.pkgDir, password) { progress ->
                when (progress) {
                    is RestoreEngine.Progress.Step -> {
                        val idx = progress.stepIndex - 1
                        val updated = steps.mapIndexed { i, s -> when {
                            i < idx -> s.copy(status = StepStatus.DONE)
                            i == idx -> s.copy(status = StepStatus.IN_PROGRESS)
                            else -> s
                        }}
                        steps.clear(); steps.addAll(updated)
                        _operationState.update { it.copy(
                            steps = steps.toList(),
                            overallProgress = progress.stepIndex.toFloat() / progress.totalSteps
                        )}
                    }
                    is RestoreEngine.Progress.Log ->
                        _operationState.update { it.copy(logLines = it.logLines + progress.message) }
                    is RestoreEngine.Progress.Finished -> {
                        val done = steps.map { it.copy(status = if (progress.success) StepStatus.DONE else StepStatus.FAILED) }
                        _operationState.update { it.copy(steps = done, isFinished = true,
                            isSuccess = progress.success, errorMessage = progress.error,
                            overallProgress = if (progress.success) 1f else it.overallProgress) }
                    }
                }
            }
        }
    }

    fun deleteEntry(entry: BackupEntry) {
        viewModelScope.launch {
            backupRepo.deleteBackup(_uiState.value.backupPath, entry.packageName)
            loadBackups()
        }
    }

    fun dismissOperation() { _operationState.update { it.copy(isVisible = false) } }
}
