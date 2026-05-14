package dev.truebackup.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dev.truebackup.app.root.RootShellCoordinator
import dev.truebackup.app.ui.PermissionGateApp
import dev.truebackup.app.ui.theme.TrueBackupTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var stoppedSinceLastOnStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrueBackupTheme {
                PermissionGateApp()
            }
        }
    }

    override fun onStop() {
        stoppedSinceLastOnStart = true
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedSinceLastOnStart) {
            stoppedSinceLastOnStart = false
            lifecycleScope.launch {
                RootShellCoordinator.resetShellAfterFullBackgroundCycle()
            }
        }
    }
}