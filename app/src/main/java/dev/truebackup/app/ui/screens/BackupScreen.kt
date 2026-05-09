package dev.truebackup.app.ui.screens

import android.net.Uri
import android.content.pm.PackageManager
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { RootBackupInteropManager(context) }
    val installedApps = remember(context) { loadInstalledApps(context) }

    var packageName by remember { mutableStateOf("") }
    var selectedAppLabel by remember { mutableStateOf("") }
    var appMenuExpanded by remember { mutableStateOf(false) }
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
                ExposedDropdownMenuBox(
                    expanded = appMenuExpanded,
                    onExpandedChange = { appMenuExpanded = !appMenuExpanded }
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        value = selectedAppLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Installed app") },
                        placeholder = { Text("Select app for backup") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = appMenuExpanded)
                        },
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = appMenuExpanded,
                        onDismissRequest = { appMenuExpanded = false }
                    ) {
                        installedApps.forEach { app ->
                            DropdownMenuItem(
                                text = { Text("${app.label} (${app.packageName})") },
                                onClick = {
                                    selectedAppLabel = app.label
                                    packageName = app.packageName
                                    appMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                if (packageName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Package: $packageName", style = MaterialTheme.typography.bodySmall)
                }
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

private data class InstalledApp(
    val label: String,
    val packageName: String
)

private fun loadInstalledApps(context: android.content.Context): List<InstalledApp> {
    val pm = context.packageManager
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .map {
            InstalledApp(
                label = pm.getApplicationLabel(it).toString(),
                packageName = it.packageName
            )
        }
        .sortedWith(
            compareBy<InstalledApp> { it.label.lowercase() }.thenBy { it.packageName }
        )
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
