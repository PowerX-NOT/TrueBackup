package dev.truebackup.app.ui.screens

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import dev.truebackup.app.backup.RootBackupInteropManager
import dev.truebackup.app.backup.RootBackupRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { RootBackupInteropManager(context) }

    var packageName by remember { mutableStateOf("") }
    var basePath by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val selected = uri?.let(::resolveBackupPathFromTreeUri)
        if (selected != null) {
            basePath = selected
            output = "Selected folder: $selected"
        } else if (uri != null) {
            output = "Pick a folder from internal shared storage."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Backup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Create interop-compatible archive sets.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = packageName,
                    onValueChange = { packageName = it.trim() },
                    label = { Text("Package name") },
                    placeholder = { Text("e.g. com.android.settings") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = basePath ?: "No folder selected",
                    onValueChange = {},
                    label = { Text("Backup folder") },
                    readOnly = true,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(modifier = Modifier.fillMaxWidth(), onClick = { folderPicker.launch(null) }) {
                    Text("Choose backup folder")
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val target = basePath
                        if (busy) return@Button
                        if (packageName.isBlank() || target.isNullOrBlank()) {
                            output = "Package name and backup folder are required."
                            return@Button
                        }
                        busy = true
                        output = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    manager.createBackupArchives(
                                        RootBackupRequest(packageName = packageName, basePath = target)
                                    )
                                }
                            }
                            busy = false
                            output = result.fold(
                                onSuccess = {
                                    "Created: ${it.packageDir.absolutePath}\nConfig: ${it.configFile.absolutePath}\n" +
                                        "Parts: apk=${it.partFlags.apk}, user=${it.partFlags.userCe}, " +
                                        "user_de=${it.partFlags.userDe}, data=${it.partFlags.extData}, " +
                                        "obb=${it.partFlags.obb}, media=${it.partFlags.media}"
                                },
                                onFailure = { "Backup failed: ${it.message}" }
                            )
                        }
                    }
                ) {
                    Text(if (busy) "Preparing..." else "Create backup archives")
                }
            }
        }

        if (!output.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = output.orEmpty(),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun resolveBackupPathFromTreeUri(uri: Uri): String? {
    val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val split = id.split(':', limit = 2)
    val volume = split.firstOrNull() ?: return null
    val relative = if (split.size > 1) split[1] else ""
    if (!volume.equals("primary", ignoreCase = true)) return null
    val base = Environment.getExternalStorageDirectory().absolutePath
    return if (relative.isBlank()) base else "$base/$relative"
}
