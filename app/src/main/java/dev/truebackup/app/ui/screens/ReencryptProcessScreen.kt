package dev.truebackup.app.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.truebackup.app.R
import dev.truebackup.app.backup.BackupInteropLayout
import dev.truebackup.app.backup.BackupTbk1Tree
import dev.truebackup.app.backup.InteropBackupIndex
import dev.truebackup.app.settings.PasswordChangeRekeySession
import dev.truebackup.app.settings.RegistrationPasswordStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File

private enum class ReencryptRowStatus { QUEUED, IN_PROGRESS, SUCCESS, FAILED }

private data class ReencryptRow(
    val key: String,
    val title: String,
    val status: ReencryptRowStatus = ReencryptRowStatus.QUEUED,
    val errorMessage: String? = null
)

/**
 * Full-screen TBK1 re-encryption after the user changes the registration password from Settings.
 * Expects a one-time [PasswordChangeRekeySession] payload (not passed through navigation).
 */
@Composable
fun ReencryptProcessScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val passwordStore = remember(context) { RegistrationPasswordStore(context) }

    val entries = remember { mutableStateListOf<ReencryptRow>() }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var finished by remember { mutableStateOf(false) }
    var invalidSession by remember { mutableStateOf(false) }
    var savePasswordFailed by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = !finished) { progress ->
        try {
            progress.collect { }
            Toast.makeText(
                context.applicationContext,
                context.getString(R.string.reencrypt_back_blocked_toast),
                Toast.LENGTH_LONG
            ).show()
        } catch (e: CancellationException) {
            throw e
        }
    }

    val overallProgress by animateFloatAsState(
        targetValue = when {
            invalidSession -> 1f
            finished && entries.isEmpty() -> 1f
            entries.isEmpty() || currentIndex < 0 -> 0f
            else -> (currentIndex + 1).coerceAtMost(entries.size).toFloat() / entries.size.toFloat()
        },
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "reencryptOverall"
    )

    LaunchedEffect(Unit) {
        val pending = withContext(Dispatchers.IO) { PasswordChangeRekeySession.take() }
        if (pending == null) {
            invalidSession = true
            finished = true
            return@LaunchedEffect
        }
        val base = pending.backupBasePath
        val oldPw = pending.oldPassword
        val newPw = pending.newPassword
        val zips = withContext(Dispatchers.IO) { BackupTbk1Tree.collectTbk1Zips(base) }
        if (zips.isEmpty()) {
            val ok = withContext(Dispatchers.IO) {
                passwordStore.changePlaintext(oldPw, newPw)
            }
            if (!ok) {
                savePasswordFailed = true
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.password_changed),
                    Toast.LENGTH_SHORT
                ).show()
            }
            finished = true
            return@LaunchedEffect
        }
        val groups = withContext(Dispatchers.IO) {
            buildAppRekeyGroups(context, zips, base)
        }
        val rows = groups.map { g ->
            ReencryptRow(
                key = g.key,
                title = g.title,
                status = ReencryptRowStatus.QUEUED
            )
        }
        withContext(Dispatchers.Main) {
            entries.clear()
            entries.addAll(rows)
        }
        val workDir = context.cacheDir
        for (i in groups.indices) {
            currentIndex = i
            entries[i] = entries[i].copy(status = ReencryptRowStatus.IN_PROGRESS)
            for (zip in groups[i].zips) {
                val ok = withContext(Dispatchers.IO) {
                    BackupTbk1Tree.rekeySingleTbk1Zip(zip, oldPw, newPw, workDir)
                }
                if (!ok) {
                    entries[i] = entries[i].copy(
                        status = ReencryptRowStatus.FAILED,
                        errorMessage = context.getString(R.string.password_rekey_failed)
                    )
                    finished = true
                    return@LaunchedEffect
                }
            }
            entries[i] = entries[i].copy(status = ReencryptRowStatus.SUCCESS)
        }
        val saved = withContext(Dispatchers.IO) {
            passwordStore.changePlaintext(oldPw, newPw)
        }
        if (!saved) {
            savePasswordFailed = true
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.password_changed),
                Toast.LENGTH_SHORT
            ).show()
        }
        finished = true
    }

    val successCount = entries.count { it.status == ReencryptRowStatus.SUCCESS }
    val failedCount = entries.count { it.status == ReencryptRowStatus.FAILED }

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
            Text(
                text = when {
                    invalidSession -> stringResource(R.string.reencrypt_headline_error)
                    finished -> stringResource(R.string.reencrypt_headline_done)
                    else -> stringResource(R.string.reencrypt_headline_working)
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    invalidSession -> stringResource(R.string.reencrypt_session_missing)
                    savePasswordFailed -> stringResource(R.string.reencrypt_password_save_failed)
                    finished && entries.isEmpty() -> stringResource(R.string.reencrypt_no_archives_saved)
                    finished -> stringResource(R.string.reencrypt_summary_fmt, successCount, entries.size)
                    entries.isEmpty() -> ""
                    else -> stringResource(
                        R.string.reencrypt_progress_fmt,
                        (currentIndex + 1).coerceAtLeast(1).coerceAtMost(entries.size),
                        entries.size
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            ReencryptProgressBar(
                progress = overallProgress,
                finished = finished,
                errorTint = invalidSession || savePasswordFailed || failedCount > 0
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (entries.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = entries,
                        key = { it.key },
                        contentType = { "reencrypt_row" }
                    ) { entry ->
                        ReencryptRowCard(
                            entry = entry,
                            isActive = currentIndex >= 0 &&
                                currentIndex < entries.size &&
                                entries.getOrNull(currentIndex)?.key == entry.key &&
                                entry.status == ReencryptRowStatus.IN_PROGRESS
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            AnimatedVisibility(
                visible = finished,
                enter = expandVertically(animationSpec = tween(400)) + fadeIn(tween(400)),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    if (entries.isNotEmpty() && (successCount > 0 || failedCount > 0)) {
                        Spacer(modifier = Modifier.height(16.dp))
                        ReencryptSummaryCard(successCount = successCount, failedCount = failedCount)
                    }
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

@Composable
private fun ReencryptProgressBar(progress: Float, finished: Boolean, errorTint: Boolean) {
    val color = when {
        errorTint && finished -> MaterialTheme.colorScheme.error
        finished -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.reencrypt_overall_progress_label),
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
private fun ReencryptRowCard(entry: ReencryptRow, isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "re_${entry.key}")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "reSpin"
    )
    val cardColor = when (entry.status) {
        ReencryptRowStatus.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ReencryptRowStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        ReencryptRowStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ReencryptRowStatus.QUEUED -> MaterialTheme.colorScheme.surfaceContainerHigh
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
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                when (entry.status) {
                    ReencryptRowStatus.QUEUED -> Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    ReencryptRowStatus.IN_PROGRESS -> Icon(
                        imageVector = Icons.Filled.HourglassTop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(rotation)
                    )
                    ReencryptRowStatus.SUCCESS -> Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp)
                    )
                    ReencryptRowStatus.FAILED -> Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedContent(
                    targetState = entry.status,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "reStatus_${entry.key}"
                ) { status ->
                    Text(
                        text = when (status) {
                            ReencryptRowStatus.QUEUED -> stringResource(R.string.reencrypt_status_waiting)
                            ReencryptRowStatus.IN_PROGRESS -> stringResource(R.string.reencrypt_status_running)
                            ReencryptRowStatus.SUCCESS -> stringResource(R.string.reencrypt_status_done)
                            ReencryptRowStatus.FAILED ->
                                entry.errorMessage?.take(80) ?: stringResource(R.string.password_rekey_failed)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (status) {
                            ReencryptRowStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
                            ReencryptRowStatus.FAILED -> MaterialTheme.colorScheme.error
                            ReencryptRowStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (isActive && entry.status == ReencryptRowStatus.IN_PROGRESS) {
                val pulse by rememberInfiniteTransition(label = "rePulse_${entry.key}").animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = pulse))
                )
            }
        }
    }
}

@Composable
private fun ReencryptSummaryCard(successCount: Int, failedCount: Int) {
    val hasFailed = failedCount > 0
    val cardColor = if (hasFailed) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
    }
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
            ReencryptSummaryStat(
                icon = Icons.Filled.CheckCircle,
                tint = MaterialTheme.colorScheme.tertiary,
                value = successCount.toString(),
                label = stringResource(R.string.reencrypt_summary_succeeded)
            )
            if (hasFailed) {
                ReencryptSummaryStat(
                    icon = Icons.Filled.Error,
                    tint = MaterialTheme.colorScheme.error,
                    value = failedCount.toString(),
                    label = stringResource(R.string.reencrypt_summary_failed)
                )
            }
        }
    }
}

@Composable
private fun ReencryptSummaryStat(
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

private data class AppRekeyGroup(
    val key: String,
    val title: String,
    val zips: List<File>
)

private fun buildAppRekeyGroups(context: Context, zips: List<File>, backupBasePath: String): List<AppRekeyGroup> {
    val map = linkedMapOf<String, MutableList<File>>()
    for (z in zips) {
        val pkgDir = BackupInteropLayout.packageDirContainingZip(z, backupBasePath)
        val groupKey = pkgDir?.absolutePath ?: z.absolutePath
        map.getOrPut(groupKey) { mutableListOf() }.add(z)
    }
    return map.map { (groupKey, zipList) ->
        val first = zipList.first()
        val pkgDir = BackupInteropLayout.packageDirContainingZip(first, backupBasePath)
        val title = if (pkgDir != null) resolveAppTitle(context, pkgDir) else first.name
        AppRekeyGroup(key = groupKey, title = title, zips = zipList)
    }
}

private fun resolveAppTitle(context: Context, pkgDir: File): String {
    val packageName = pkgDir.name
    val fromBackup = InteropBackupIndex.readBackedUpAppLabel(pkgDir)
    return runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(
            pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        ).toString().takeIf { it.isNotBlank() }
    }.getOrNull() ?: fromBackup
}
