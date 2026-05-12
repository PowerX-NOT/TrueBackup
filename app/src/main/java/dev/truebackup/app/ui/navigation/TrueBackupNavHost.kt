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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.truebackup.app.ui.screens.BackupProcessScreen
import dev.truebackup.app.ui.screens.BackupScreen
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
    NavHost(
        navController = navController,
        startDestination = AppDestination.Backup.route,
        modifier = modifier,
        enterTransition = screenEnterTransition,
        exitTransition = screenExitTransition,
        popEnterTransition = screenPopEnterTransition,
        popExitTransition = screenPopExitTransition
    ) {
        composable(AppDestination.Backup.route) {
            BackupScreen(
                onStartBackup = { args ->
                    // Store args on this entry's SavedStateHandle; the
                    // process screen picks them up from the previous entry.
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("backup_process_args", args)
                    navController.navigate(AppDestination.BackupProcess.route)
                }
            )
        }
        composable(AppDestination.Restore.route) {
            RestoreScreen()
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen()
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

            if (args == null) {
                navController.popBackStack()
                return@composable
            }

            BackupProcessScreen(
                packages = args.packages,
                basePath = args.basePath,
                onFinished = { navController.popBackStack() }
            )
        }
    }
}
