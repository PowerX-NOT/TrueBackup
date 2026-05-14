package dev.truebackup.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.truebackup.app.R
import dev.truebackup.app.backup.InteropBackedUpPackage
import dev.truebackup.app.backup.InteropBackupIndex
import dev.truebackup.app.settings.AppSettingsRepository
import dev.truebackup.app.settings.RegistrationPasswordStore
import dev.truebackup.app.ui.rememberPackageChangeCounter
import dev.truebackup.app.ui.navigation.RestoreBackupDetailNavArgs
import dev.truebackup.app.ui.navigation.RestoreProcessArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RestoreScreen(
    listRefreshVersion: Int = 0,
    onStartRestore: (RestoreProcessArgs) -> Unit,
    onOpenBackupDetails: (RestoreBackupDetailNavArgs) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packageChangeTick = rememberPackageChangeCounter()
    val repo = remember(context) { AppSettingsRepository(context) }
    val passwordStore = remember(context) { RegistrationPasswordStore(context) }
    val basePath by repo.backupBasePath.collectAsState(initial = null)

    var hasPassword by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasPassword = withContext(Dispatchers.IO) { passwordStore.isConfigured() }
    }

    var backedUp by remember { mutableStateOf<List<InteropBackedUpPackage>>(emptyList()) }
    LaunchedEffect(basePath, packageChangeTick, listRefreshVersion) {
        val bp = basePath?.trim()?.takeIf { it.isNotEmpty() }
        backedUp = if (bp != null) {
            withContext(Dispatchers.IO) {
                runCatching { InteropBackupIndex.listBackedUpPackages(bp) }.getOrElse { emptyList() }
            }
        } else {
            emptyList()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    /** One folder path per row — avoids duplicate LazyColumn keys when the same package appears twice. */
    var selectedBackupPaths by remember { mutableStateOf(setOf<String>()) }

    val filtered = remember(backedUp, searchQuery) {
        if (searchQuery.isBlank()) backedUp
        else {
            val q = searchQuery.trim().lowercase()
            backedUp.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
    }

    LaunchedEffect(backedUp) {
        val paths = backedUp.map { it.packageDir.absolutePath }.toSet()
        selectedBackupPaths = selectedBackupPaths.intersect(paths)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            "Restore apps",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        PillSearchBarRestore(
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
                text = "Selected: ${selectedBackupPaths.size}",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                enabled = selectedBackupPaths.isNotEmpty() && !basePath.isNullOrBlank() && hasPassword,
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
                        val selected = backedUp.filter { selectedBackupPaths.contains(it.packageDir.absolutePath) }
                        onStartRestore(RestoreProcessArgs(packages = selected))
                    }
                }
            ) {
                Text("Restore")
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
                stringResource(R.string.password_required_hint_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else if (backedUp.isEmpty()) {
            Text(
                "No backups found (expected backup/apps/<package>/package_restore_config.json).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filtered,
                    key = { it.packageDir.absolutePath },
                    contentType = { "restore_pick_row" }
                ) { pkg ->
                    val rowKey = pkg.packageDir.absolutePath
                    val checked = selectedBackupPaths.contains(rowKey)
                    RestorePickRow(
                        item = pkg,
                        checked = checked,
                        onCheckedChange = { enabled ->
                            selectedBackupPaths = if (enabled) {
                                selectedBackupPaths + rowKey
                            } else {
                                selectedBackupPaths - rowKey
                            }
                        },
                        onOpenDetails = {
                            val bp = basePath?.trim()?.takeIf { it.isNotEmpty() } ?: return@RestorePickRow
                            onOpenBackupDetails(
                                RestoreBackupDetailNavArgs(
                                    packageDirAbsolutePath = pkg.packageDir.absolutePath,
                                    backupBasePath = bp,
                                )
                            )
                        },
                        packageChangeTick = packageChangeTick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun RestorePickRow(
    item: InteropBackedUpPackage,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenDetails: () -> Unit,
    packageChangeTick: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appIcon = remember(item.packageName, packageChangeTick) {
        runCatching {
            context.packageManager.getApplicationIcon(item.packageName).toBitmap(96, 96)
        }.getOrNull()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenDetails),
                verticalAlignment = Alignment.CenterVertically
            ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon.asImageBitmap(),
                    contentDescription = item.label,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.label.ifBlank { "?" }.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    item.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun PillSearchBarRestore(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search backups…"
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
