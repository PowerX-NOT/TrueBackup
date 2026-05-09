package dev.truebackup.app.ui.navigation

sealed class AppDestination(
    val route: String,
    val label: String
) {
    data object Backup : AppDestination("backup", "Backup")
    data object Restore : AppDestination("restore", "Restore")
    data object Settings : AppDestination("settings", "Settings")

    companion object {
        val bottomItems = listOf(Backup, Restore, Settings)
    }
}
