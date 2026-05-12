package dev.truebackup.app.ui.navigation

import androidx.navigation.NavHostController

/**
 * Same options as the bottom [NavigationBar]: clears overlays above the graph start
 * (e.g. backup process) and shows [route] with state restore so content lays out again.
 */
fun NavHostController.navigateToMainTab(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
    }
}

/**
 * Closes [AppDestination.BackupProcess] and returns to the Backup tab (the screen that
 * was underneath). Prefer this over [navigateToMainTab] for "Done" so the back stack
 * actually pops instead of re-navigating to start.
 */
fun NavHostController.popBackToBackupFromProcess(): Boolean {
    return popBackStack(
        route = AppDestination.Backup.route,
        inclusive = false,
        saveState = false
    )
}

/**
 * Closes [AppDestination.RestoreProcess] and returns to the Restore tab.
 */
fun NavHostController.popBackToRestoreFromProcess(): Boolean {
    return popBackStack(
        route = AppDestination.Restore.route,
        inclusive = false,
        saveState = false
    )
}

/** Closes [AppDestination.ReencryptProcess] and returns to Settings. */
fun NavHostController.popBackToSettingsFromReencrypt(): Boolean {
    return popBackStack(
        route = AppDestination.Settings.route,
        inclusive = false,
        saveState = false
    )
}
