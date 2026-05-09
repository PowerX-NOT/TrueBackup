package dev.truebackup.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.truebackup.app.backup.RootBackupInteropManager
import dev.truebackup.app.backup.RootBackupRequest
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
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var hasRootAccess by remember { mutableStateOf(false) }

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
        } else if (status.needsAllFilesAccess) {
            permissionMessage = null
            val packageUri = Uri.parse("package:${context.packageName}")
            val appSpecificIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                packageUri
            )
            val globalIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            val launchIntent = when {
                appSpecificIntent.resolveActivity(context.packageManager) != null -> appSpecificIntent
                globalIntent.resolveActivity(context.packageManager) != null -> globalIntent
                else -> null
            }
            if (launchIntent != null) {
                runCatching {
                    allFilesLauncher.launch(launchIntent)
                }.onFailure {
                    permissionMessage = "Unable to open all files access settings on this ROM."
                }
            } else {
                permissionMessage = "All files access settings screen is unavailable on this device."
            }
        }
    }

    Scaffold { paddingValues ->
        GradientBackground(modifier = Modifier.fillMaxSize()) {
            if (status.allGranted && hasRootAccess) {
                InAppDashboardScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            } else if (status.allGranted) {
                ReadyScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    onRootVerified = { hasRootAccess = true }
                )
            } else {
                PermissionRequiredScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
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
        modifier = modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HeroTitle(
            title = "Welcome to TrueBackup",
            subtitle = "Complete permission setup before entering root-mode dashboard."
        )
        Spacer(modifier = Modifier.height(16.dp))
        GlassCard {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Permission setup required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
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
                        style = MaterialTheme.typography.titleMedium
                    )
                    missingRuntimePermissions.forEach { permission ->
                        Text(text = "• $permission", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (needsAllFilesAccess) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "All files access (Android 11+) is required. Enable it in settings when prompted.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (!message.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onRequest
        ) {
            Text(text = "Grant required permissions")
        }
    }
}

@Composable
private fun ReadyScreen(
    modifier: Modifier,
    onRootVerified: () -> Unit
) {
    val rootPreflight = remember { RootPreflight() }
    val scope = rememberCoroutineScope()
    var isCheckingRoot by remember { mutableStateOf(false) }
    var rootResult by remember { mutableStateOf<RootPreflightResult?>(null) }

    Column(
        modifier = modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HeroTitle(
            title = "Security preflight",
            subtitle = "Permissions are ready. Validate root execution before entering dashboard."
        )
        Spacer(modifier = Modifier.height(16.dp))
        GlassCard {
            Column(modifier = Modifier.padding(18.dp)) {
                if (isCheckingRoot) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text(
                    text = "Root preflight",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Runs `su -c id -u` and confirms uid is 0.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (isCheckingRoot) return@Button
                isCheckingRoot = true
                scope.launch {
                    rootResult = withContext(Dispatchers.IO) {
                        rootPreflight.verify()
                    }
                    if (rootResult?.isRootAvailable == true) {
                        onRootVerified()
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
            GlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (result.isRootAvailable) "Root check passed" else "Root check failed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
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
            if (result.isRootAvailable) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Opening dashboard...",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun InAppDashboardScreen(modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { RootBackupInteropManager(context) }
    var packageName by remember { mutableStateOf("") }
    var backupBasePath by remember { mutableStateOf<String?>(null) }
    var isPreparing by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolvedPath = resolveBackupPathFromTreeUri(uri)
        if (resolvedPath != null) {
            backupBasePath = resolvedPath
            resultText = "Selected backup folder: $resolvedPath"
        } else {
            resultText = "Selected folder is not a primary shared storage path. Choose a folder under Internal Storage."
        }
    }

    Column(
        modifier = modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        HeroTitle(
            title = "TrueBackup Dashboard",
            subtitle = "Modern root-mode backup workspace with interop-compatible archive output."
        )
        Spacer(modifier = Modifier.height(16.dp))
        GlassCard {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Create interoperable archive set",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
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
                    value = backupBasePath ?: "No folder selected",
                    onValueChange = {},
                    label = { Text("Backup base path") },
                    readOnly = true,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { folderPicker.launch(null) }
                ) {
                    Text("Choose backup folder")
                }
                Spacer(modifier = Modifier.height(14.dp))
                if (isPreparing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (isPreparing) return@Button
                        val targetPath = backupBasePath
                        if (packageName.isBlank() || targetPath.isNullOrBlank()) {
                            resultText = "Package name and backup folder are required."
                            return@Button
                        }
                        isPreparing = true
                        resultText = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    manager.createBackupArchives(
                                        RootBackupRequest(
                                            packageName = packageName,
                                            basePath = targetPath
                                        )
                                    )
                                }
                            }
                            isPreparing = false
                            resultText = result.fold(
                                onSuccess = {
                                    "Created: ${it.packageDir.absolutePath}\nConfig: ${it.configFile.absolutePath}\n" +
                                        "Parts: apk=${it.partFlags.apk}, user=${it.partFlags.userCe}, " +
                                        "user_de=${it.partFlags.userDe}, data=${it.partFlags.extData}, " +
                                        "obb=${it.partFlags.obb}, media=${it.partFlags.media}"
                                },
                                onFailure = {
                                    "Failed to create backup skeleton: ${it.message}"
                                }
                            )
                        }
                    }
                ) {
                    Text(text = if (isPreparing) "Preparing..." else "Create backup archives")
                }
            }
        }
        if (!resultText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            GlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Latest operation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = resultText ?: "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun resolveBackupPathFromTreeUri(uri: Uri): String? {
    val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val split = treeDocId.split(':', limit = 2)
    if (split.isEmpty()) return null
    val volume = split[0]
    val relative = if (split.size > 1) split[1] else ""
    if (!volume.equals("primary", ignoreCase = true)) return null
    val base = Environment.getExternalStorageDirectory().absolutePath
    return if (relative.isBlank()) base else "$base/$relative"
}

@Composable
private fun GradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
                    )
                )
            )
    ) {
        content()
    }
}

@Composable
private fun HeroTitle(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
        )
    ) {
        content()
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
