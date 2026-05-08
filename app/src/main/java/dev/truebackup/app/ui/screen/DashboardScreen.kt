package dev.truebackup.app.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.truebackup.app.data.BackupEntry
import dev.truebackup.app.ui.components.*
import dev.truebackup.app.ui.theme.*
import dev.truebackup.app.ui.viewmodel.DashboardUiState

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onNewBackupClick: () -> Unit,
    onBackupClick: (BackupEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize().background(TbBackground)) {
        // Animated background gradient blobs
        BackgroundBlobs()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            // ── Header ──────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "TrueBackup",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TbOnBackground
                    )
                    Text(
                        "Root-privileged backup",
                        style = MaterialTheme.typography.bodySmall,
                        color = TbOnSurfaceVariant
                    )
                }
                // Root status indicator
                RootStatusBadge(isRooted = uiState.isRooted)
            }

            Spacer(Modifier.height(24.dp))

            // ── Active operation card ───────────────────────────────────────
            if (uiState.activeOperation != null) {
                ActiveOperationCard(
                    operation = uiState.activeOperation,
                    progress = uiState.progress
                )
                Spacer(Modifier.height(16.dp))
            }

            // ── Quick stats ─────────────────────────────────────────────────
            QuickStatsRow(
                totalBackups = uiState.totalBackups,
                totalSize = uiState.totalSizeBytes,
                lastBackupAt = uiState.lastBackupAt
            )

            Spacer(Modifier.height(24.dp))

            // ── Recent backups ───────────────────────────────────────────────
            if (uiState.recentBackups.isNotEmpty()) {
                SectionHeader("Recent Backups")
                uiState.recentBackups.take(5).forEach { entry ->
                    BackupCard(
                        entry = entry,
                        onClick = { onBackupClick(entry) },
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Activity log ─────────────────────────────────────────────────
            if (uiState.activityLog.isNotEmpty()) {
                SectionHeader("Activity Log")
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    uiState.activityLog.takeLast(12).reversed().forEach { line ->
                        ActivityLogItem(message = line)
                    }
                }
            }

            // Bottom FAB clearance
            Spacer(Modifier.height(100.dp))
        }

        // ── FAB ─────────────────────────────────────────────────────────────
        FloatingActionButton(
            onClick = onNewBackupClick,
            containerColor = TbPrimary,
            contentColor = TbOnPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Add, "New Backup")
                Spacer(Modifier.width(8.dp))
                Text("New Backup", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BackgroundBlobs() {
    val infiniteTransition = rememberInfiniteTransition(label = "blobs")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "blob_offset"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width * 0.8f
        val cy = size.height * 0.2f + offset * 60f
        drawCircle(color = Color(0xFF9B78FF).copy(alpha = 0.07f), radius = 280.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, cy))
        drawCircle(color = Color(0xFF7DDFFF).copy(alpha = 0.05f), radius = 200.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.6f - offset * 40f))
    }
}

@Composable
private fun RootStatusBadge(isRooted: Boolean) {
    Surface(
        color = if (isRooted) TbAccentGreen.copy(alpha = 0.15f) else TbError.copy(alpha = 0.15f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (isRooted) TbAccentGreen.copy(alpha = 0.4f) else TbError.copy(alpha = 0.4f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                if (isRooted) Icons.Default.AdminPanelSettings else Icons.Default.Block,
                null,
                tint = if (isRooted) TbAccentGreen else TbError,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (isRooted) "Rooted" else "No Root",
                color = if (isRooted) TbAccentGreen else TbError,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ActiveOperationCard(operation: String, progress: Float) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedProgressRing(progress = progress, size = 64.dp, strokeWidth = 6.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Operation In Progress", style = MaterialTheme.typography.labelSmall, color = TbOnSurfaceVariant)
                Text(operation, style = MaterialTheme.typography.titleSmall, color = TbOnSurface, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                    color = TbPrimary,
                    trackColor = TbOutlineVariant
                )
            }
        }
    }
}

@Composable
private fun QuickStatsRow(totalBackups: Int, totalSize: Long, lastBackupAt: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(modifier = Modifier.weight(1f), label = "Backups", value = "$totalBackups", icon = Icons.Default.FolderZip)
        StatCard(modifier = Modifier.weight(1f), label = "Total Size", value = formatBytes(totalSize), icon = Icons.Default.Storage)
        StatCard(modifier = Modifier.weight(1f), label = "Last Run", value = if (lastBackupAt == 0L) "—" else formatDate(lastBackupAt), icon = Icons.Default.Schedule)
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    GlassCard(modifier = modifier) {
        Icon(icon, null, tint = TbAccentViolet, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = TbOnSurface, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TbOnSurfaceVariant)
    }
}
