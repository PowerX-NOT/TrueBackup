package dev.truebackup.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.truebackup.app.ui.screens.BackupScreen
import dev.truebackup.app.ui.screens.RestoreScreen
import dev.truebackup.app.ui.screens.SettingsScreen

@Composable
fun TrueBackupNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.Backup.route,
        modifier = modifier
    ) {
        composable(AppDestination.Backup.route) {
            BackupScreen()
        }
        composable(AppDestination.Restore.route) {
            RestoreScreen()
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen()
        }
    }
}
