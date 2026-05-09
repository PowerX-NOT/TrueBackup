package dev.truebackup.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.truebackup.app.root.RootPreflight
import dev.truebackup.app.root.RootPreflightResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PermissionStatus(
    val missingRuntimePermissions: List<String>,
    val needsAllFilesAccess: Boolean
) {
    val allGranted: Boolean
        get() = missingRuntimePermissions.isEmpty() && !needsAllFilesAccess
}

@Composable
fun PermissionGateApp() {
    val context = LocalContext.current
    var status by remember { mutableStateOf(currentPermissionStatus(context)) }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        status = currentPermissionStatus(context)
    }

    val allFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        status = currentPermissionStatus(context)
    }

    fun requestMissingPermissions() {
        if (status.missingRuntimePermissions.isNotEmpty()) {
            runtimePermissionLauncher.launch(status.missingRuntimePermissions.toTypedArray())
        } else if (status.needsAllFilesAccess) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            allFilesLauncher.launch(intent)
        }
    }

    Scaffold { paddingValues ->
        if (status.allGranted) {
            ReadyScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            PermissionRequiredScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                missingRuntimePermissions = status.missingRuntimePermissions,
                needsAllFilesAccess = status.needsAllFilesAccess,
                onRequest = ::requestMissingPermissions
            )
        }
    }
}

@Composable
private fun PermissionRequiredScreen(
    modifier: Modifier,
    missingRuntimePermissions: List<String>,
    needsAllFilesAccess: Boolean,
    onRequest: () -> Unit
) {
    Column(
        modifier = modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(modifier = Modifier.padding(4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Permission setup required",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "TrueBackup needs storage-level access before entering the app.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (missingRuntimePermissions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Missing runtime permissions:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    missingRuntimePermissions.forEach { permission ->
                        Text(text = "- $permission", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (needsAllFilesAccess) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "All files access (Android 11+) is required.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequest) {
            Text(text = "Grant required permissions")
        }
    }
}

@Composable
private fun ReadyScreen(modifier: Modifier) {
    val rootPreflight = remember { RootPreflight() }
    val scope = rememberCoroutineScope()
    var isCheckingRoot by remember { mutableStateOf(false) }
    var rootResult by remember { mutableStateOf<RootPreflightResult?>(null) }

    Column(
        modifier = modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permissions granted",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Run root preflight before entering backup dashboard.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (isCheckingRoot) return@Button
                isCheckingRoot = true
                scope.launch {
                    rootResult = withContext(Dispatchers.IO) {
                        rootPreflight.verify()
                    }
                    isCheckingRoot = false
                }
            }
        ) {
            Text(
                text = if (isCheckingRoot) {
                    "Checking root..."
                } else {
                    "Run root preflight"
                }
            )
        }
        rootResult?.let { result ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodyMedium
            )
            if (result.output.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Output: ${result.output}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun currentPermissionStatus(context: Context): PermissionStatus {
    val needed = mutableListOf<String>()
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        needed += Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        needed += Manifest.permission.WRITE_EXTERNAL_STORAGE
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        needed += Manifest.permission.POST_NOTIFICATIONS
    }
    val allFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
    return PermissionStatus(
        missingRuntimePermissions = needed,
        needsAllFilesAccess = allFiles
    )
}
