package dev.truebackup.app.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.truebackup.app.data.AppInfo
import dev.truebackup.app.ui.components.*
import dev.truebackup.app.ui.theme.*

enum class AppListTab { ALL, BACKED_UP, NOT_BACKED_UP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    apps: List<AppInfo>,
    backedUpPackages: Set<String>,
    inProgressPackages: Set<String>,
    onBackupApp: (String) -> Unit,
    onRestoreApp: (String) -> Unit,
    onDeleteBackup: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(AppListTab.ALL) }

    val filtered = remember(apps, query, selectedTab, backedUpPackages) {
        apps.filter { app ->
            val matchQuery = query.isEmpty() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            val matchTab = when (selectedTab) {
                AppListTab.ALL          -> true
                AppListTab.BACKED_UP    -> backedUpPackages.contains(app.packageName)
                AppListTab.NOT_BACKED_UP -> !backedUpPackages.contains(app.packageName)
            }
            matchQuery && matchTab
        }
    }

    Column(modifier = modifier.fillMaxSize().background(TbBackground).statusBarsPadding()) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Apps", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold, color = TbOnBackground,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(12.dp))

        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps…", color = TbOnSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TbOnSurfaceVariant) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Default.Clear, null, tint = TbOnSurfaceVariant)
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TbPrimary,
                unfocusedBorderColor = TbOutline,
                focusedContainerColor = TbSurface,
                unfocusedContainerColor = TbSurface,
                focusedTextColor = TbOnSurface,
                unfocusedTextColor = TbOnSurface
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Tab row
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = TbBackground,
            contentColor = TbPrimary,
            indicator = { tabs ->
                if (selectedTab.ordinal < tabs.size) {
                    val tabPos = tabs[selectedTab.ordinal]
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier
                            .wrapContentSize(Alignment.BottomStart)
                            .offset(x = tabPos.left)
                            .width(tabPos.width),
                        color = TbPrimary
                    )
                }
            },
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Tab(selected = selectedTab == AppListTab.ALL,
                onClick = { selectedTab = AppListTab.ALL },
                text = { Text("All (${apps.size})", style = MaterialTheme.typography.labelLarge) })
            Tab(selected = selectedTab == AppListTab.BACKED_UP,
                onClick = { selectedTab = AppListTab.BACKED_UP },
                text = { Text("Backed Up (${backedUpPackages.size})", style = MaterialTheme.typography.labelLarge) })
            Tab(selected = selectedTab == AppListTab.NOT_BACKED_UP,
                onClick = { selectedTab = AppListTab.NOT_BACKED_UP },
                text = { Text("Not Backed Up", style = MaterialTheme.typography.labelLarge) })
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.packageName }) { app ->
                AppListItem(
                    app = app,
                    isBackedUp = backedUpPackages.contains(app.packageName),
                    isInProgress = inProgressPackages.contains(app.packageName),
                    onBackup = { onBackupApp(app.packageName) },
                    onRestore = { onRestoreApp(app.packageName) },
                    onDelete = { onDeleteBackup(app.packageName) }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AppListItem(
    app: AppInfo,
    isBackedUp: Boolean,
    isInProgress: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { menuExpanded = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // App icon
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(app.getIcon(ctx))
                    .crossfade(true)
                    .build(),
                contentDescription = app.label,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleSmall,
                    color = TbOnSurface, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                    color = TbOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                when {
                    isInProgress -> StatusChip(BackupStatus.IN_PROGRESS)
                    isBackedUp   -> StatusChip(BackupStatus.BACKED_UP)
                    else         -> StatusChip(BackupStatus.NOT_BACKED_UP)
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, null, tint = TbOnSurfaceVariant)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(TbSurfaceVariant)
                ) {
                    DropdownMenuItem(
                        text = { Text("Backup Now", color = TbOnSurface) },
                        leadingIcon = { Icon(Icons.Default.Backup, null, tint = TbPrimary) },
                        onClick = { menuExpanded = false; onBackup() }
                    )
                    if (isBackedUp) {
                        DropdownMenuItem(
                            text = { Text("Restore", color = TbOnSurface) },
                            leadingIcon = { Icon(Icons.Default.Restore, null, tint = TbAccentCyan) },
                            onClick = { menuExpanded = false; onRestore() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Backup", color = TbError) },
                            leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = TbError) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}
