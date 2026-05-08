package dev.truebackup.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.truebackup.app.data.AppInfo
import dev.truebackup.app.data.BackupRepository
import dev.truebackup.app.data.PackageRepository
import dev.truebackup.app.engine.*
import dev.truebackup.app.ui.screen.OperationStep
import dev.truebackup.app.ui.screen.StepStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppListUiState(
    val apps: List<AppInfo> = emptyList(),
    val backedUpPackages: Set<String> = emptySet(),
    val inProgressPackages: Set<String> = emptySet(),
    val isLoading: Boolean = true
)

data class OperationUiState(
    val isVisible: Boolean = false,
    val operationLabel: String = "",
    val packageName: String = "",
    val steps: List<OperationStep> = emptyList(),
    val logLines: List<String> = emptyList(),
    val overallProgress: Float = 0f,
    val isFinished: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

class AppListViewModel(app: Application) : AndroidViewModel(app) {

    private val pkgRepo = PackageRepository(app)
    private val backupRepo = BackupRepository()
    private val passwordManager = PasswordManager(app)
    private val backupEngine = BackupEngine(app)
    private val restoreEngine = RestoreEngine(app)

    private val _appListState = MutableStateFlow(AppListUiState())
    val appListState: StateFlow<AppListUiState> = _appListState.asStateFlow()

    private val _operationState = MutableStateFlow(OperationUiState())
    val operationState: StateFlow<OperationUiState> = _operationState.asStateFlow()

    init { loadApps() }

    fun loadApps() {
        viewModelScope.launch {
            _appListState.update { it.copy(isLoading = true) }
            val apps = pkgRepo.getInstalledApps()
            val basePath = passwordManager.getBackupBasePath()
            val backups = try { backupRepo.listBackups(basePath) } catch (_: Exception) { emptyList() }
            val backedUp = backups.map { it.packageName }.toSet()
            _appListState.update { it.copy(apps = apps, backedUpPackages = backedUp, isLoading = false) }
        }
    }

    fun backupApp(packageName: String) {
        viewModelScope.launch {
            val password = passwordManager.getPassword()
            val basePath = passwordManager.getBackupBasePath()

            _appListState.update { it.copy(inProgressPackages = it.inProgressPackages + packageName) }
            val backupStepLabels = listOf(
                "Creating directories", "Backing up APK", "Backing up CE data",
                "Backing up DE data", "Backing up external data", "Backing up OBB & media", "Writing config"
            )
            val steps = backupStepLabels.mapIndexed { i, s -> OperationStep(i, s, StepStatus.PENDING) }.toMutableList()

            _operationState.value = OperationUiState(
                isVisible = true, operationLabel = "Backing up $packageName",
                packageName = packageName, steps = steps.toList()
            )

            backupEngine.backup(packageName, basePath, password) { progress ->
                when (progress) {
                    is BackupEngine.Progress.Step -> {
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
                    is BackupEngine.Progress.Log -> {
                        _operationState.update { it.copy(logLines = it.logLines + progress.message) }
                    }
                    is BackupEngine.Progress.Finished -> {
                        val finalSteps = steps.map { it.copy(status = if (progress.success) StepStatus.DONE else StepStatus.FAILED) }
                        _operationState.update { it.copy(
                            steps = finalSteps, isFinished = true,
                            isSuccess = progress.success, errorMessage = progress.error,
                            overallProgress = if (progress.success) 1f else it.overallProgress
                        )}
                    }
                }
            }
            _appListState.update { it.copy(inProgressPackages = it.inProgressPackages - packageName) }
            loadApps()
        }
    }

    fun restoreApp(packageName: String, basePath: String) {
        viewModelScope.launch {
            val password = passwordManager.getPassword()
            val pkgDir = backupRepo.resolveAppsDir(basePath)
                ?.listFiles()?.firstOrNull { it.name == packageName } ?: return@launch

            val restoreStepLabels = listOf(
                "Installing APK", "Restoring CE data", "Restoring DE data",
                "Restoring external data", "Restoring OBB", "Restoring media",
                "Restoring permissions", "Finished"
            )
            val steps = restoreStepLabels.mapIndexed { i, s -> OperationStep(i, s, StepStatus.PENDING) }.toMutableList()

            _operationState.value = OperationUiState(
                isVisible = true, operationLabel = "Restoring $packageName",
                packageName = packageName, steps = steps.toList()
            )

            restoreEngine.restore(packageName, pkgDir, password) { progress ->
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
                        val finalSteps = steps.map { it.copy(status = if (progress.success) StepStatus.DONE else StepStatus.FAILED) }
                        _operationState.update { it.copy(
                            steps = finalSteps, isFinished = true,
                            isSuccess = progress.success, errorMessage = progress.error,
                            overallProgress = if (progress.success) 1f else it.overallProgress
                        )}
                    }
                }
            }
        }
    }

    fun dismissOperation() {
        _operationState.update { it.copy(isVisible = false) }
    }

    fun deleteBackup(packageName: String) {
        viewModelScope.launch {
            val basePath = passwordManager.getBackupBasePath()
            backupRepo.deleteBackup(basePath, packageName)
            loadApps()
        }
    }
}
