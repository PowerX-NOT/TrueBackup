package dev.truebackup.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.truebackup.app.backup.RootBackupInteropManager
import dev.truebackup.app.backup.RootBackupRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── State model ─────────────────────────────────────────────────────────────

enum class PackageBackupStatus { QUEUED, IN_PROGRESS, SUCCESS, FAILED }

data class PackageBackupEntry(
    val packageName: String,
    val label: String,
    val status: PackageBackupStatus = PackageBackupStatus.QUEUED,
    val errorMessage: String? = null
)

// ── Screen ───────────────────────────────────────────────────────────────────

/**
 * Full-screen backup process overlay.
 *
 * @param packages  Ordered list of (packageName, appLabel) to back up.
 * @param basePath  Root path for the backup archive tree.
 * @param onFinished Called when the user taps "Done" or the back gesture is used after completion.
 */
@Composable
fun BackupProcessScreen(
    packages: List<Pair<String, String>>,
    basePath: String,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { RootBackupInteropManager(context) }

    val entries = remember {
        mutableStateListOf(*packages.map { (pkg, label) ->
            PackageBackupEntry(packageName = pkg, label = label)
        }.toTypedArray())
    }

    var currentIndex by remember { mutableStateOf(-1) }
    var finished by remember { mutableStateOf(false) }

    // Overall progress 0f..1f
    val overallProgress by animateFloatAsState(
        targetValue = if (packages.isEmpty()) 1f
        else (currentIndex + 1).coerceAtMost(packages.size).toFloat() / packages.size.toFloat(),
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "overallProgress"
    )

    // Kick off the backup chain once
    LaunchedEffect(Unit) {
        if (packages.isEmpty()) {
            finished = true
            return@LaunchedEffect
        }
        packages.forEachIndexed { index, (pkg, _) ->
            withContext(Dispatchers.Main) {
                currentIndex = index
                entries[index] = entries[index].copy(status = PackageBackupStatus.IN_PROGRESS)
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    manager.createBackupArchives(
                        RootBackupRequest(packageName = pkg, basePath = basePath)
                    )
                }
            }
            withContext(Dispatchers.Main) {
                entries[index] = if (result.isSuccess) {
                    entries[index].copy(status = PackageBackupStatus.SUCCESS)
                } else {
                    entries[index].copy(
                        status = PackageBackupStatus.FAILED,
                        errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                    )
                }
            }
        }
        finished = true
    }

    val successCount = entries.count { it.status == PackageBackupStatus.SUCCESS }
    val failedCount = entries.count { it.status == PackageBackupStatus.FAILED }

    // Gradient background
    val gradientColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerLowest,
        MaterialTheme.colorScheme.surface
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Top
        ) {

            // ── Header ──────────────────────────────────────────────────────
            Text(
                text = if (finished) "Backup complete" else "Backing up…",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (finished)
                    "$successCount of ${packages.size} apps backed up"
                else
                    "${(currentIndex + 1).coerceAtMost(packages.size)} of ${packages.size} apps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            // ── Overall progress bar ─────────────────────────────────────────
            AnimatedProgressBar(progress = overallProgress, finished = finished)
            Spacer(modifier = Modifier.height(24.dp))

            // ── Per-app list ─────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = entries,
                    key = { it.packageName },
                    contentType = { "backup_row" }
                ) { entry ->
                    AppBackupRow(entry = entry, isActive = currentIndex < packages.size &&
                            packages.getOrNull(currentIndex)?.first == entry.packageName)
                }
            }

            // ── Summary / Done ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = finished,
                enter = expandVertically(animationSpec = tween(400)) + fadeIn(tween(400)),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    SummaryCard(successCount = successCount, failedCount = failedCount)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onFinished,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Done", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

// ── Subcomponents ─────────────────────────────────────────────────────────────

@Composable
private fun AnimatedProgressBar(progress: Float, finished: Boolean) {
    val color = if (finished) MaterialTheme.colorScheme.tertiary
    else MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Overall progress",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun AppBackupRow(entry: PackageBackupEntry, isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner_${entry.packageName}")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinnerRotation"
    )

    val cardColor = when (entry.status) {
        PackageBackupStatus.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        PackageBackupStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        PackageBackupStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        PackageBackupStatus.QUEUED -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon / spinner
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                when (entry.status) {
                    PackageBackupStatus.QUEUED -> Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = "Queued",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    PackageBackupStatus.IN_PROGRESS -> Icon(
                        imageVector = Icons.Filled.HourglassTop,
                        contentDescription = "In progress",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(rotation)
                    )
                    PackageBackupStatus.SUCCESS -> Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp)
                    )
                    PackageBackupStatus.FAILED -> Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedContent(
                    targetState = entry.status,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                    },
                    label = "statusText_${entry.packageName}"
                ) { status ->
                    Text(
                        text = when (status) {
                            PackageBackupStatus.QUEUED -> "Waiting…"
                            PackageBackupStatus.IN_PROGRESS -> "Backing up…"
                            PackageBackupStatus.SUCCESS -> "Done"
                            PackageBackupStatus.FAILED -> entry.errorMessage
                                ?.take(60) ?: "Failed"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (status) {
                            PackageBackupStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
                            PackageBackupStatus.FAILED -> MaterialTheme.colorScheme.error
                            PackageBackupStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Tiny dot indicator for active item
            if (isActive && entry.status == PackageBackupStatus.IN_PROGRESS) {
                val pulse by rememberInfiniteTransition(label = "pulse_${entry.packageName}").animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = pulse)
                        )
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(successCount: Int, failedCount: Int) {
    val hasFailed = failedCount > 0
    val cardColor = if (hasFailed)
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
    else
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryStatItem(
                icon = Icons.Filled.CheckCircle,
                tint = MaterialTheme.colorScheme.tertiary,
                value = successCount.toString(),
                label = "Succeeded"
            )
            if (hasFailed) {
                SummaryStatItem(
                    icon = Icons.Filled.Error,
                    tint = MaterialTheme.colorScheme.error,
                    value = failedCount.toString(),
                    label = "Failed"
                )
            }
        }
    }
}

@Composable
private fun SummaryStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
