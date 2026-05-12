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
