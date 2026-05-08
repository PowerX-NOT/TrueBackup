package dev.truebackup.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.truebackup.app.engine.RootShell
import dev.truebackup.app.ui.nav.TrueBackupNavHost
import dev.truebackup.app.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrueBackupTheme {
                var rootChecked by remember { mutableStateOf(false) }
                var isRooted by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    isRooted = RootShell.isRootAvailable()
                    rootChecked = true
                }

                when {
                    !rootChecked -> {
                        // Splash / checking
                        Box(
                            Modifier.fillMaxSize().background(TbBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = TbPrimary)
                                Spacer(Modifier.height(16.dp))
                                Text("Checking root access…",
                                    color = TbOnSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    !isRooted -> {
                        // No-root gate screen
                        NoRootScreen()
                    }

                    else -> {
                        TrueBackupNavHost()
                    }
                }
            }
        }
    }
}

@Composable
private fun NoRootScreen() {
    Box(
        Modifier.fillMaxSize().background(TbBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)
        ) {
            Icon(Icons.Default.Block, null,
                tint = TbError, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(24.dp))
            Text("Root Access Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold, color = TbOnBackground)
            Spacer(Modifier.height(12.dp))
            Text(
                "TrueBackup requires root privileges to read and write protected app data, " +
                "fix file ownership, and restore SELinux labels.\n\n" +
                "Please install Magisk or KernelSU and grant root access to TrueBackup.",
                style = MaterialTheme.typography.bodyMedium,
                color = TbOnSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Surface(
                color = TbErrorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "⚠ Grant root permission in your root manager then relaunch the app.",
                    color = TbError,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}