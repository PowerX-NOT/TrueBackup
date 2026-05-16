package dev.truebackup.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.truebackup.app.ui.PermissionGateApp
import dev.truebackup.app.ui.theme.TrueBackupTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrueBackupTheme {
                PermissionGateApp()
            }
        }
    }
}
