package dev.truebackup.app.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

        // ── Root status card ─────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Root status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                AnimatedVisibility(
                    visible = isCheckingRoot,
                    enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                ) {
                    Column {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (isCheckingRoot) return@Button
                        isCheckingRoot = true
                        rootResult = null
                        scope.launch {
                            rootResult = withContext(Dispatchers.IO) { preflight.verify() }
                            isCheckingRoot = false
                        }
                    }
                ) {
                    Text(if (isCheckingRoot) "Checking..." else "Run root preflight")
                }

                AnimatedVisibility(
                    visible = rootResult != null,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                ) {
                    rootResult?.let { result ->
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(result.message, style = MaterialTheme.typography.bodyMedium)
                            if (result.output.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Output: ${result.output}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // ── Backup destination card ───────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Backup destination",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                FolderPathDisplay(path = backupBasePath)

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { folderPicker.launch(null) }
                ) {
                    Text(if (backupBasePath.isNullOrBlank()) "Choose backup folder" else "Change folder")
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

/**
 * Styled read-only path display with a folder icon.
 * Shows the last path segment in bold and the parent path muted above it.
 */
@Composable
private fun FolderPathDisplay(path: String?) {
    val scheme = MaterialTheme.colorScheme
    val hasPath = !path.isNullOrBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (hasPath) Icons.Outlined.Folder else Icons.Outlined.FolderOff,
            contentDescription = null,
            tint = if (hasPath) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        if (hasPath && path != null) {
            val segments = path.trimEnd('/').split('/')
            val folderName = segments.last()
            val parentPath = segments.dropLast(1).joinToString("/").ifBlank { "/" }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = parentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = "No folder selected",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
