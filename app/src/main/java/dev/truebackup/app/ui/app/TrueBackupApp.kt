package dev.truebackup.app.ui.app

import androidx.compose.foundation.layout.padding
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

@Composable
fun TrueBackupApp() {
    val navController = rememberNavController()
    val backStack = navController.currentBackStackEntryAsState().value
    val currentRoute = backStack?.destination?.route
    val items = AppDestination.bottomItems

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                items.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                            }
                        },
                        icon = { Text(destination.label.take(1)) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        TrueBackupNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
