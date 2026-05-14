package dev.truebackup.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.truebackup.app.ui.screens.BackupProcessScreen
import dev.truebackup.app.ui.screens.BackupScreen
import dev.truebackup.app.ui.screens.ReencryptProcessScreen
import dev.truebackup.app.ui.screens.RestoreBackupDetailsScreen
import dev.truebackup.app.ui.screens.RestoreProcessScreen
import dev.truebackup.app.ui.screens.RestoreScreen
import dev.truebackup.app.ui.screens.SettingsScreen

private const val TRANSITION_DURATION = 300

/** Shared enter: fade in + subtle slide up from the bottom. */
private val screenEnterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    fadeIn(animationSpec = tween(TRANSITION_DURATION)) +
        slideInVertically(
            animationSpec = tween(TRANSITION_DURATION),
            initialOffsetY = { fullHeight -> fullHeight / 12 }
        )
}

/** Shared exit: fade out + subtle slide up (leaving screen moves up and disappears). */
private val screenExitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    fadeOut(animationSpec = tween(TRANSITION_DURATION / 2)) +
        slideOutVertically(
            animationSpec = tween(TRANSITION_DURATION),
            targetOffsetY = { fullHeight -> -fullHeight / 20 }
        )
}

/** Pop enter: reverse of exit — screen slides back down into view. */
private val screenPopEnterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    fadeIn(animationSpec = tween(TRANSITION_DURATION)) +
        slideInVertically(
            animationSpec = tween(TRANSITION_DURATION),
            initialOffsetY = { fullHeight -> -fullHeight / 20 }
        )
}

/** Pop exit: fade out + slide down out of view. */
private val screenPopExitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    fadeOut(animationSpec = tween(TRANSITION_DURATION / 2)) +
        slideOutVertically(
            animationSpec = tween(TRANSITION_DURATION),
            targetOffsetY = { fullHeight -> fullHeight / 12 }
        )
}

/** Sheet-style enter for the process screen — slides up from the bottom. */
private val processEnterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    fadeIn(animationSpec = tween(350)) +
        slideInVertically(
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            initialOffsetY = { fullHeight -> fullHeight }
        )
}

/** Sheet-style exit — slides back down. */
private val processExitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    fadeOut(animationSpec = tween(300)) +
        slideOutVertically(
            animationSpec = tween(300),
            targetOffsetY = { fullHeight -> fullHeight }
        )
}

@Composable
fun TrueBackupNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    var restoreListVersion by remember { mutableIntStateOf(0) }

    NavHost(
        navController = navController,
        startDestination = AppDestination.Backup.route,
        modifier = modifier.fillMaxSize(),
        enterTransition = screenEnterTransition,
        exitTransition = screenExitTransition,
        popEnterTransition = screenPopEnterTransition,
        popExitTransition = screenPopExitTransition
    ) {
        composable(AppDestination.Backup.route) { backStackEntry ->
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp)) {
                BackupScreen(
                    onStartBackup = { args ->
                        // Store on this destination's entry so previousBackStackEntry still has it
                        // when BackupProcess is shown (avoid currentBackStackEntry races).
                        backStackEntry.savedStateHandle["backup_process_args"] = args
                        navController.navigate(AppDestination.BackupProcess.route)
                    }
                )
            }
        }
        composable(AppDestination.Restore.route) { backStackEntry ->
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp)) {
                RestoreScreen(
                    listRefreshVersion = restoreListVersion,
                    onStartRestore = { args ->
                        backStackEntry.savedStateHandle["restore_process_args"] = args
                        navController.navigate(AppDestination.RestoreProcess.route)
                    },
                    onOpenBackupDetails = { detailArgs ->
                        backStackEntry.savedStateHandle[RestoreNavKeys.BACKUP_DETAIL_ARGS] = detailArgs
                        navController.navigate(AppDestination.RestoreBackupDetails.route)
                    }
                )
            }
        }
        composable(AppDestination.RestoreBackupDetails.route) {
            val args = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<RestoreBackupDetailNavArgs>(RestoreNavKeys.BACKUP_DETAIL_ARGS)

            LaunchedEffect(args) {
                if (args == null) {
                    if (!navController.popBackToRestoreFromBackupDetails()) {
                        navController.navigateToMainTab(AppDestination.Restore.route)
                    }
                }
            }

            if (args == null) {
                Box(modifier = Modifier.fillMaxSize())
                return@composable
            }

            RestoreBackupDetailsScreen(
                args = args,
                onBack = {
                    if (!navController.popBackToRestoreFromBackupDetails()) {
                        navController.navigateToMainTab(AppDestination.Restore.route)
                    }
                },
                onDeleted = {
                    restoreListVersion++
                    if (!navController.popBackToRestoreFromBackupDetails()) {
                        navController.navigateToMainTab(AppDestination.Restore.route)
                    }
                }
            )
        }
        composable(AppDestination.Settings.route) {
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp)) {
                SettingsScreen(
                    onNavigateToReencrypt = {
                        navController.navigate(AppDestination.ReencryptProcess.route)
                    }
                )
            }
        }
        composable(
            route = AppDestination.BackupProcess.route,
            enterTransition = processEnterTransition,
            exitTransition = processExitTransition,
            popEnterTransition = screenPopEnterTransition,
            popExitTransition = processExitTransition
        ) {
            val args = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<BackupProcessArgs>("backup_process_args")

            LaunchedEffect(args) {
                if (args == null) {
                    navController.popBackToBackupFromProcess()
                }
            }

            if (args == null) {
                Box(modifier = Modifier.fillMaxSize())
                return@composable
            }

            BackupProcessScreen(
                packages = args.packages,
                basePath = args.basePath,
                onFinished = {
                    if (!navController.popBackToBackupFromProcess()) {
                        navController.navigateToMainTab(AppDestination.Backup.route)
                    }
                }
            )
        }
        composable(
            route = AppDestination.RestoreProcess.route,
            enterTransition = processEnterTransition,
            exitTransition = processExitTransition,
            popEnterTransition = screenPopEnterTransition,
            popExitTransition = processExitTransition
        ) {
            val args = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<RestoreProcessArgs>("restore_process_args")

            LaunchedEffect(args) {
                if (args == null) {
                    navController.popBackToRestoreFromProcess()
                }
            }

            if (args == null) {
                Box(modifier = Modifier.fillMaxSize())
                return@composable
            }

            RestoreProcessScreen(
                packages = args.packages,
                onFinished = {
                    if (!navController.popBackToRestoreFromProcess()) {
                        navController.navigateToMainTab(AppDestination.Restore.route)
                    }
                }
            )
        }
        composable(
            route = AppDestination.ReencryptProcess.route,
            enterTransition = processEnterTransition,
            exitTransition = processExitTransition,
            popEnterTransition = screenPopEnterTransition,
            popExitTransition = processExitTransition
        ) {
            ReencryptProcessScreen(
                onFinished = {
                    if (!navController.popBackToSettingsFromReencrypt()) {
                        navController.navigateToMainTab(AppDestination.Settings.route)
                    }
                }
            )
        }
    }
}
