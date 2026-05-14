package dev.truebackup.app.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.truebackup.app.R
import dev.truebackup.app.settings.AppSettingsRepository
import dev.truebackup.app.ui.rememberPackageChangeCounter
import dev.truebackup.app.settings.RegistrationPasswordStore
import dev.truebackup.app.ui.navigation.BackupProcessArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupScreen(onStartBackup: (BackupProcessArgs) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packageChangeTick = rememberPackageChangeCounter()
    val installedApps = remember(packageChangeTick, context) { loadInstalledApps(context) }
    val repo = remember(context) { AppSettingsRepository(context) }
    val passwordStore = remember(context) { RegistrationPasswordStore(context) }
    val basePath by repo.backupBasePath.collectAsState(initial = null)

    var hasPassword by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasPassword = withContext(Dispatchers.IO) { passwordStore.isConfigured() }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedPackages by remember { mutableStateOf(setOf<String>()) }
    var showSystemApps by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

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

    LaunchedEffect(installedApps) {
        val valid = installedApps.map { it.packageName }.toSet()
        selectedPackages = selectedPackages.intersect(valid)
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
            Text(
                "Back up apps",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
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
                enabled = selectedPackages.isNotEmpty() && !basePath.isNullOrBlank() && hasPassword,
                onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { passwordStore.isConfigured() }
                        if (!ok) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.password_required_for_backup_restore),
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }
                        val target = basePath ?: return@launch
                        val packageList = selectedPackages.map { pkg ->
                            val label = installedApps.find { it.packageName == pkg }?.label ?: pkg
                            pkg to label
                        }
                        onStartBackup(BackupProcessArgs(packages = packageList, basePath = target))
                    }
                }
            ) {
                Text("Back up")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (basePath.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.backup_choose_folder_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else if (!hasPassword) {
            Text(
                text = stringResource(R.string.password_required_hint_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
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
                    val appIcon = remember(app.packageName, packageChangeTick) {
                        runCatching {
                            context.packageManager.getApplicationIcon(app.packageName)
                                .toBitmap(96, 96)
                        }.getOrNull()
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
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall
                                )
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
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

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
