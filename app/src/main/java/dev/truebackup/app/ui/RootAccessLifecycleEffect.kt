package dev.truebackup.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.truebackup.app.root.RootAccessProbe
import dev.truebackup.app.settings.RootAccessRepository
import kotlinx.coroutines.launch

/**
 * Re-checks root when the activity resumes (e.g. user revoked access in Magisk and returned).
 */
@Composable
fun RootAccessLifecycleEffect() {
    val context = LocalContext.current
    val rootAccessRepo = remember(context) { RootAccessRepository(context) }
    val setupComplete by rootAccessRepo.setupComplete.collectAsState(initial = false)
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, setupComplete) {
        if (!setupComplete) {
            return@DisposableEffect onDispose {}
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    RootAccessProbe.probeAndPersist(rootAccessRepo)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
