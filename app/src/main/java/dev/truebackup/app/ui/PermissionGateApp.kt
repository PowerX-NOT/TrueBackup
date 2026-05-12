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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.truebackup.app.ui.app.TrueBackupApp

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
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        status = currentPermissionStatus(context)
    }

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
            return
        }
        if (!status.needsAllFilesAccess) return

        permissionMessage = null
        val appSpecificIntent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        val globalIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        val launchIntent = when {
            appSpecificIntent.resolveActivity(context.packageManager) != null -> appSpecificIntent
            globalIntent.resolveActivity(context.packageManager) != null -> globalIntent
            else -> null
        }
        if (launchIntent != null) {
            runCatching { allFilesLauncher.launch(launchIntent) }
                .onFailure { permissionMessage = "Unable to open all files access settings on this ROM." }
        } else {
            permissionMessage = "All files access settings screen is unavailable on this device."
        }
    }

    Scaffold { innerPadding ->
        AnimatedContent(
            targetState = status.allGranted,
            transitionSpec = {
                // Entering the app: fade in + subtle scale up from 92%
                (fadeIn(animationSpec = tween(400)) +
                    scaleIn(
                        animationSpec = tween(400),
                        initialScale = 0.92f
                    )) togetherWith
                    // Permission screen fades out
                    fadeOut(animationSpec = tween(200))
            },
            label = "PermissionToAppTransition"
        ) { allGranted ->
            if (allGranted) {
                TrueBackupApp(modifier = Modifier.fillMaxSize())
            } else {
                PermissionRequiredScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(20.dp),
                    missingRuntimePermissions = status.missingRuntimePermissions,
                    needsAllFilesAccess = status.needsAllFilesAccess,
                    onRequest = ::requestMissingPermissions,
                    message = permissionMessage
                )
            }
        }
    }
}

@Composable
private fun PermissionRequiredScreen(
    modifier: Modifier,
    missingRuntimePermissions: List<String>,
    needsAllFilesAccess: Boolean,
    onRequest: () -> Unit,
    message: String?
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to TrueBackup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Grant required permissions before entering the app.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (missingRuntimePermissions.isNotEmpty()) {
                    Text("Missing runtime permissions", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    missingRuntimePermissions.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (needsAllFilesAccess) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "All files access (Android 11+) is required.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (!message.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = onRequest) {
            Text("Grant required permissions")
        }
    }
}

private fun currentPermissionStatus(context: Context): PermissionStatus {
    val needed = mutableListOf<String>()
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        needed += Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        needed += Manifest.permission.WRITE_EXTERNAL_STORAGE
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        needed += Manifest.permission.POST_NOTIFICATIONS
    }
    val allFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
    return PermissionStatus(
        missingRuntimePermissions = needed,
        needsAllFilesAccess = allFiles
    )
}
