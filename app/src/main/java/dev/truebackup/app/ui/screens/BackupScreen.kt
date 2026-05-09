package dev.truebackup.app.ui.screens

import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import dev.truebackup.app.backup.RootBackupInteropManager
import dev.truebackup.app.backup.RootBackupRequest
import dev.truebackup.app.settings.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { RootBackupInteropManager(context) }
    val installedApps = remember(context) { loadInstalledApps(context) }
    val repo = remember(context) { AppSettingsRepository(context) }
    val basePath by repo.backupBasePath.collectAsState(initial = null)

    var searchQuery by remember { mutableStateOf("") }
    var selectedPackages by remember { mutableStateOf(setOf<String>()) }
    var showSystemApps by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf<String?>(null) }
    val filteredApps = remember(installedApps, searchQuery, showSystemApps) {
        val baseList = if (showSystemApps) installedApps else installedApps.filter { !it.isSystem }
        if (searchQuery.isBlank()) {
            baseList
        } else {
            val query = searchQuery.trim().lowercase()
            baseList.filter {
                it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Back up apps", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (showSystemApps) "Hide system apps" else "Show system apps") },
                        onClick = {
                            showSystemApps = !showSystemApps
                            menuExpanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        PillSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Selected: ${selectedPackages.size}",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                enabled = !busy && selectedPackages.isNotEmpty() && !basePath.isNullOrBlank(),
                onClick = {
                    val target = basePath
                    if (busy) return@Button
                    if (selectedPackages.isEmpty() || target.isNullOrBlank()) {
                        output = "Select at least one app and set backup folder in Settings."
                        return@Button
                    }
                    busy = true
                    output = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            val success = mutableListOf<String>()
                            val failed = mutableListOf<String>()
                            selectedPackages.forEach { pkg ->
                                runCatching {
                                    manager.createBackupArchives(
                                        RootBackupRequest(packageName = pkg, basePath = target)
                                    )
                                }.onSuccess {
                                    success += pkg
                                }.onFailure {
                                    failed += "$pkg (${it.message ?: "error"})"
                                }
                            }
                            success to failed
                        }
                        busy = false
                        output = buildString {
                            append("Backup complete.\n")
                            append("Success: ${result.first.size}\n")
                            append("Failed: ${result.second.size}")
                            if (result.second.isNotEmpty()) {
                                append("\n")
                                append(result.second.joinToString(separator = "\n"))
                            }
                        }
                    }
                }
            ) {
                Text(if (busy) "Backing up..." else "Back up")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val checked = selectedPackages.contains(app.packageName)
                    val appIcon = remember(app.packageName) {
                        runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96) }
                            .getOrNull()
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (appIcon != null) {
                                Image(
                                    bitmap = appIcon.asImageBitmap(),
                                    contentDescription = app.label,
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(36.dp)
                                )
                            } else {
                                Card {
                                    Box(
                                        modifier = Modifier
                                            .width(36.dp)
                                            .height(36.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = app.label.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.titleMedium)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { enabled ->
                                    selectedPackages = if (enabled) {
                                        selectedPackages + app.packageName
                                    } else {
                                        selectedPackages - app.packageName
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
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
    val packageName: String,
    val isSystem: Boolean
)

private fun loadInstalledApps(context: android.content.Context): List<InstalledApp> {
    val pm = context.packageManager
    return pm.getInstalledPackages(PackageManager.GET_META_DATA)
        .asSequence()
        .filter { it.packageName != context.packageName }
        .mapNotNull { pkg ->
            val ai = pkg.applicationInfo ?: return@mapNotNull null
            InstalledApp(
                label = pm.getApplicationLabel(ai).toString(),
                packageName = pkg.packageName,
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }
        .toList()
        .sortedWith(
            compareBy<InstalledApp> { it.label.lowercase() }.thenBy { it.packageName }
        )
}

// Backup folder selection lives in Settings.

@Composable
private fun PillSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search for name or package..."
) {
    val scheme = MaterialTheme.colorScheme
    val barColor = scheme.surfaceContainerHigh
    val muted = scheme.onSurfaceVariant
    val inputColor = scheme.onSurface

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.heightIn(min = 52.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = inputColor),
        cursorBrush = SolidColor(scheme.primary),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = barColor, shape = RoundedCornerShape(percent = 50))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = muted
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = muted
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}
