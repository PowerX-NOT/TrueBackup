package dev.truebackup.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.truebackup.app.data.BackupEntry
import dev.truebackup.app.ui.theme.*
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// GlassCard — glassmorphism surface with gradient border
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = Brush.linearGradient(
        colors = listOf(TbPrimary.copy(alpha = 0.6f), TbTertiary.copy(alpha = 0.3f), TbPrimary.copy(alpha = 0.1f))
    )
    val shape = RoundedCornerShape(20.dp)
    val baseModifier = modifier
        .clip(shape)
        .background(TbSurface.copy(alpha = 0.85f))
        .border(1.dp, borderBrush, shape)

    if (onClick != null) {
        Column(modifier = baseModifier.clickable { onClick() }.padding(16.dp), content = content)
    } else {
        Column(modifier = baseModifier.padding(16.dp), content = content)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AnimatedProgressRing — circular progress with glow
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AnimatedProgressRing(
    progress: Float,          // 0f..1f
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    strokeWidth: Dp = 8.dp,
    trackColor: Color = TbOutlineVariant,
    progressColor: Color = TbPrimary,
    glowColor: Color = TbAccentViolet
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "ring_progress"
    )
    val pulse by rememberInfiniteTransition(label = "ring_pulse").animateFloat(
        initialValue = 0.7f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    Canvas(modifier = modifier.size(size)) {
        val sw = strokeWidth.toPx()
        val r = (size.toPx() - sw) / 2f
        val center = Offset(size.toPx() / 2, size.toPx() / 2)

        // Track
        drawCircle(color = trackColor, radius = r, center = center, style = Stroke(sw))

        // Glow layer
        drawArc(
            color = glowColor.copy(alpha = 0.25f * pulse),
            startAngle = -90f,
            sweepAngle = 360f * animatedProgress,
            useCenter = false,
            style = Stroke(sw * 2.5f, cap = StrokeCap.Round)
        )

        // Progress arc
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * animatedProgress,
            useCenter = false,
            style = Stroke(sw, cap = StrokeCap.Round)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StatusChip
// ─────────────────────────────────────────────────────────────────────────────
enum class BackupStatus { BACKED_UP, NOT_BACKED_UP, IN_PROGRESS, ENCRYPTED }

@Composable
fun StatusChip(status: BackupStatus, modifier: Modifier = Modifier) {
    val (label, color, icon) = when (status) {
        BackupStatus.BACKED_UP     -> Triple("Backed Up",    TbAccentGreen,  Icons.Default.CheckCircle)
        BackupStatus.NOT_BACKED_UP -> Triple("Not Backed Up", TbOnSurfaceVariant, Icons.Default.RadioButtonUnchecked)
        BackupStatus.IN_PROGRESS   -> Triple("In Progress",  TbAccentAmber,  Icons.Default.Sync)
        BackupStatus.ENCRYPTED     -> Triple("Encrypted",    TbAccentCyan,   Icons.Default.Lock)
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GradientButton
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null
) {
    val gradient = Brush.horizontalGradient(
        colors = if (enabled) listOf(TbPrimary, Color(0xFF7B2FBE)) else listOf(TbOutline, TbOutline)
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(gradient)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            icon?.invoke()
            if (icon != null) Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ActivityLogItem — monospace log line
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ActivityLogItem(message: String, timestamp: String? = null, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        if (timestamp != null) {
            Text(
                timestamp, color = TbOnSurfaceVariant.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(56.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            message, color = TbOnSurface.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BackupCard — glassmorphism card for a single backup entry
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun BackupCard(entry: BackupEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Package icon placeholder
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(TbPrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (entry.label ?: entry.packageName).take(1).uppercase(),
                    color = TbOnPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.label ?: entry.packageName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TbOnSurface, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    entry.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TbOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(BackupStatus.BACKED_UP)
                    if (entry.isEncrypted) StatusChip(BackupStatus.ENCRYPTED)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatBytes(entry.totalBytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = TbAccentViolet, fontWeight = FontWeight.SemiBold
                )
                Text(
                    formatDate(entry.backedUpAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = TbOnSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = TbOnSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Utilities
// ─────────────────────────────────────────────────────────────────────────────
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L         -> "%.1f KB".format(bytes / 1_024.0)
    else                    -> "$bytes B"
}

fun formatDate(millis: Long): String {
    if (millis == 0L) return "—"
    val sdf = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
