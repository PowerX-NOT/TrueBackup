package dev.truebackup.app.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.truebackup.app.ui.navigation.AppDestination
import dev.truebackup.app.ui.navigation.TrueBackupNavHost
import dev.truebackup.app.ui.navigation.navigateToMainTab

@Composable
fun TrueBackupApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStack = navController.currentBackStackEntryAsState().value
    val currentRoute = backStack?.destination?.route
    val items = AppDestination.bottomItems

    // Hide bottom bar on full-screen process screens
    val showBottomBar = currentRoute != AppDestination.BackupProcess.route &&
        currentRoute != AppDestination.RestoreProcess.route &&
        currentRoute != AppDestination.ReencryptProcess.route &&
        currentRoute != AppDestination.RestoreBackupDetails.route

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    items.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigateToMainTab(destination.route)
                            },
                            icon = {
                                val isSelected = currentRoute == destination.route
                                Icon(
                                    imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        TrueBackupNavHost(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
