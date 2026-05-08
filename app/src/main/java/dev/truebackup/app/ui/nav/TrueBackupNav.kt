package dev.truebackup.app.ui.nav

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.truebackup.app.ui.screen.*
import dev.truebackup.app.ui.theme.*
import dev.truebackup.app.ui.viewmodel.*

sealed class TbDestination(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : TbDestination("dashboard", "Home",     Icons.Default.Home)
    object Apps      : TbDestination("apps",      "Apps",     Icons.Default.Apps)
    object Restore   : TbDestination("restore",   "Restore",  Icons.Default.Restore)
    object Settings  : TbDestination("settings",  "Settings", Icons.Default.Settings)
}

private val destinations = listOf(
    TbDestination.Dashboard, TbDestination.Apps, TbDestination.Restore, TbDestination.Settings
)

@Composable
fun TrueBackupNavHost() {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStack?.destination

    val dashVm: DashboardViewModel = viewModel()
    val appListVm: AppListViewModel = viewModel()
    val restoreVm: RestoreViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()

    val dashState by dashVm.uiState.collectAsState()
    val appListState by appListVm.appListState.collectAsState()
    val appOpState by appListVm.operationState.collectAsState()
    val restoreUiState by restoreVm.uiState.collectAsState()
    val restoreOpState by restoreVm.operationState.collectAsState()
    val settingsState by settingsVm.uiState.collectAsState()

    Scaffold(
        containerColor = TbBackground,
        bottomBar = {
            NavigationBar(
                containerColor = TbSurface,
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                destinations.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true  // NavOptionsBuilder.restoreState
                            }
                        },
                        icon = {
                            Icon(dest.icon, dest.label,
                                tint = if (selected) TbPrimary else TbOnSurfaceVariant)
                        },
                        label = {
                            Text(dest.label,
                                color = if (selected) TbPrimary else TbOnSurfaceVariant,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelSmall)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = TbPrimaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TbDestination.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TbDestination.Dashboard.route) {
                DashboardScreen(
                    uiState = dashState,
                    onNewBackupClick = { navController.navigate(TbDestination.Apps.route) },
                    onBackupClick = { entry ->
                        navController.navigate(TbDestination.Restore.route)
                    }
                )
            }

            composable(TbDestination.Apps.route) {
                Box(Modifier.fillMaxSize()) {
                    AppListScreen(
                        apps = appListState.apps,
                        backedUpPackages = appListState.backedUpPackages,
                        inProgressPackages = appListState.inProgressPackages,
                        onBackupApp = { appListVm.backupApp(it) },
                        onRestoreApp = { appListVm.restoreApp(it, settingsState.backupPath) },
                        onDeleteBackup = { appListVm.deleteBackup(it) }
                    )

                    // Overlay progress screen
                    AnimatedVisibility(
                        visible = appOpState.isVisible,
                        enter = fadeIn() + slideInVertically { it },
                        exit  = fadeOut() + slideOutVertically { it }
                    ) {
                        BackupProgressScreen(
                            operationLabel   = appOpState.operationLabel,
                            packageName      = appOpState.packageName,
                            steps            = appOpState.steps,
                            logLines         = appOpState.logLines,
                            overallProgress  = appOpState.overallProgress,
                            isFinished       = appOpState.isFinished,
                            isSuccess        = appOpState.isSuccess,
                            errorMessage     = appOpState.errorMessage,
                            onDismiss        = {
                                appListVm.dismissOperation()
                                dashVm.refresh()
                            }
                        )
                    }
                }
            }

            composable(TbDestination.Restore.route) {
                Box(Modifier.fillMaxSize()) {
                    RestoreScreen(
                        backupPath   = restoreUiState.backupPath,
                        backups      = restoreUiState.backups,
                        isLoading    = restoreUiState.isLoading,
                        onPathChange = { /* TODO: SAF picker */ },
                        onRestoreClick = { restoreVm.restoreEntry(it) },
                        onDeleteClick  = { restoreVm.deleteEntry(it) }
                    )
                    AnimatedVisibility(
                        visible = restoreOpState.isVisible,
                        enter = fadeIn() + slideInVertically { it },
                        exit  = fadeOut() + slideOutVertically { it }
                    ) {
                        BackupProgressScreen(
                            operationLabel  = restoreOpState.operationLabel,
                            packageName     = restoreOpState.packageName,
                            steps           = restoreOpState.steps,
                            logLines        = restoreOpState.logLines,
                            overallProgress = restoreOpState.overallProgress,
                            isFinished      = restoreOpState.isFinished,
                            isSuccess       = restoreOpState.isSuccess,
                            errorMessage    = restoreOpState.errorMessage,
                            onDismiss       = { restoreVm.dismissOperation() }
                        )
                    }
                }
            }

            composable(TbDestination.Settings.route) {
                SettingsScreen(
                    isPasswordSet     = settingsState.isPasswordSet,
                    backupPath        = settingsState.backupPath,
                    isRooted          = settingsState.isRooted,
                    onSetPassword     = { settingsVm.setPassword(it) },
                    onChangePassword  = { old, new -> settingsVm.changePassword(old, new) },
                    onClearPassword   = { settingsVm.clearPassword() },
                    onBackupPathChange = { settingsVm.setBackupPath(it) }
                )
            }
        }
    }
}
