package dev.truebackup.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.truebackup.app.root.RootPreflight
import dev.truebackup.app.root.RootPreflightResult
import dev.truebackup.app.settings.AppSettingsRepository
import dev.truebackup.app.ui.util.resolvePrimaryStoragePathFromTreeUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { AppSettingsRepository(context) }
    val preflight = remember { RootPreflight() }
    val backupBasePath by repo.backupBasePath.collectAsState(initial = null)

    var verifyRootAtStartup by remember { mutableStateOf(true) }
    var enableEncryption by remember { mutableStateOf(false) }
    var isCheckingRoot by remember { mutableStateOf(false) }
    var rootResult by remember { mutableStateOf<RootPreflightResult?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val selected = resolvePrimaryStoragePathFromTreeUri(uri)
        scope.launch {
            repo.setBackupBasePath(selected)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Runtime behavior and backup preferences.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Root status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                if (isCheckingRoot) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (isCheckingRoot) return@Button
                        isCheckingRoot = true
                        scope.launch {
                            rootResult = withContext(Dispatchers.IO) { preflight.verify() }
                            isCheckingRoot = false
                        }
                    }
                ) {
                    Text(if (isCheckingRoot) "Checking..." else "Run root preflight")
                }
                rootResult?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(it.message, style = MaterialTheme.typography.bodyMedium)
                    if (it.output.isNotBlank()) {
                        Text("Output: ${it.output}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Backup destination", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = backupBasePath ?: "No folder selected",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Backup folder") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { folderPicker.launch(null) }
                ) {
                    Text("Choose backup folder")
                }
                Spacer(modifier = Modifier.height(16.dp))

                SettingRow(
                    title = "Verify root on startup",
                    checked = verifyRootAtStartup,
                    onCheckedChange = { verifyRootAtStartup = it }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingRow(
                    title = "Enable archive encryption",
                    checked = enableEncryption,
                    onCheckedChange = { enableEncryption = it }
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
