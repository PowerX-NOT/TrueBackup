package dev.truebackup.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.RestorePage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Backup : AppDestination(
        route = "backup",
        label = "Backup",
        selectedIcon = Icons.Filled.Backup,
        unselectedIcon = Icons.Outlined.Backup
    )

    data object Restore : AppDestination(
        route = "restore",
        label = "Restore",
        selectedIcon = Icons.Filled.RestorePage,
        unselectedIcon = Icons.Outlined.RestorePage
    )

    data object Settings : AppDestination(
        route = "settings",
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    companion object {
        val bottomItems = listOf(Backup, Restore, Settings)
    }
}
