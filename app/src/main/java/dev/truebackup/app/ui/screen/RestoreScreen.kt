package dev.truebackup.app.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.truebackup.app.data.BackupEntry
import dev.truebackup.app.ui.components.*
import dev.truebackup.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreScreen(
    backupPath: String,
    backups: List<BackupEntry>,
    isLoading: Boolean,
    onPathChange: () -> Unit,
    onRestoreClick: (BackupEntry) -> Unit,
    onDeleteClick: (BackupEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmEntry by remember { mutableStateOf<BackupEntry?>(null) }

    Column(modifier = modifier.fillMaxSize().background(TbBackground).statusBarsPadding()) {
        Spacer(Modifier.height(16.dp))

        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Restore", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold, color = TbOnBackground)
                Text("Browse & restore backups", style = MaterialTheme.typography.bodySmall,
                    color = TbOnSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))

        // Backup path card
        GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), onClick = onPathChange) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, null, tint = TbAccentViolet, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Backup Location", style = MaterialTheme.typography.labelSmall, color = TbOnSurfaceVariant)
                    Text(backupPath, style = MaterialTheme.typography.bodySmall, color = TbOnSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.ChevronRight, null, tint = TbOnSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TbPrimary)
            }
        } else if (backups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SearchOff, null, tint = TbOnSurfaceVariant, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No backups found", style = MaterialTheme.typography.bodyLarge, color = TbOnSurfaceVariant)
                    Text("Check the backup location above", style = MaterialTheme.typography.bodySmall, color = TbOnSurfaceVariant)
                }
            }
        } else {
            SectionHeader("${backups.size} backup${if (backups.size != 1) "s" else ""} found",
                modifier = Modifier.padding(horizontal = 20.dp))

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(backups, key = { it.packageName }) { entry ->
                    RestoreEntryCard(
                        entry = entry,
                        onRestore = { confirmEntry = entry },
                        onDelete = { onDeleteClick(entry) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // Confirm restore dialog
    confirmEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmEntry = null },
            containerColor = TbSurfaceVariant,
            title = { Text("Confirm Restore", color = TbOnSurface, fontWeight = FontWeight.SemiBold) },
            text = {
                Text("This will overwrite existing data for ${entry.label ?: entry.packageName}. Continue?",
                    color = TbOnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(
                    onClick = { onRestoreClick(entry); confirmEntry = null },
                    colors = ButtonDefaults.buttonColors(containerColor = TbPrimary)
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEntry = null }) {
                    Text("Cancel", color = TbOnSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun RestoreEntryCard(entry: BackupEntry, onRestore: () -> Unit, onDelete: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(TbPrimaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text((entry.label ?: entry.packageName).take(1).uppercase(),
                    color = TbOnPrimaryContainer, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.label ?: entry.packageName, style = MaterialTheme.typography.titleSmall,
                    color = TbOnSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.packageName, style = MaterialTheme.typography.bodySmall,
                    color = TbOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(formatBytes(entry.totalBytes), style = MaterialTheme.typography.labelSmall, color = TbAccentViolet)
                    Text("•", color = TbOnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text(formatDate(entry.backedUpAt), style = MaterialTheme.typography.labelSmall, color = TbOnSurfaceVariant)
                    if (entry.isEncrypted) {
                        Text("•", color = TbOnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        Icon(Icons.Default.Lock, null, tint = TbAccentCyan, modifier = Modifier.size(12.dp))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Default.Restore, null, tint = TbAccentCyan)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteForever, null, tint = TbError)
                }
            }
        }
    }
}
