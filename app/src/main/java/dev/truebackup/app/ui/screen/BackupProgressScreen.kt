package dev.truebackup.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.truebackup.app.ui.components.AnimatedProgressRing
import dev.truebackup.app.ui.components.GlassCard
import dev.truebackup.app.ui.theme.*

data class OperationStep(
    val index: Int,
    val description: String,
    val status: StepStatus
)

enum class StepStatus { PENDING, IN_PROGRESS, DONE, FAILED }

@Composable
fun BackupProgressScreen(
    operationLabel: String,      // "Backing up com.example.app"
    packageName: String,
    steps: List<OperationStep>,
    logLines: List<String>,
    overallProgress: Float,      // 0..1
    isFinished: Boolean,
    isSuccess: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logListState = rememberLazyListState()

    // Auto-scroll log to bottom
    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) logListState.animateScrollToItem(logLines.size - 1)
    }

    Box(modifier = modifier.fillMaxSize().background(TbBackground).statusBarsPadding()) {
        // Pulsing background glow
        PulsingBackgroundGlow(isFinished = isFinished, isSuccess = isSuccess)

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            // ── Progress ring + label ──
            Box(contentAlignment = Alignment.Center) {
                AnimatedProgressRing(
                    progress = overallProgress,
                    size = 120.dp,
                    strokeWidth = 10.dp,
                    progressColor = when {
                        isFinished && !isSuccess -> TbError
                        isFinished && isSuccess  -> TbAccentGreen
                        else                     -> TbPrimary
                    }
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(overallProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TbOnBackground
                    )
                    if (isFinished) {
                        Icon(
                            if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            null,
                            tint = if (isSuccess) TbAccentGreen else TbError,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(operationLabel, style = MaterialTheme.typography.titleSmall,
                color = TbOnSurface, fontWeight = FontWeight.SemiBold)
            Text(packageName, style = MaterialTheme.typography.bodySmall, color = TbOnSurfaceVariant)

            Spacer(Modifier.height(24.dp))

            // ── Steps list ──────────────────────────────────────────────────
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                steps.forEach { step ->
                    StepRow(step = step)
                    if (step.index < steps.size - 1) {
                        HorizontalDivider(color = TbOutlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Live log ────────────────────────────────────────────────────
            GlassCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Text("Activity Log", style = MaterialTheme.typography.labelSmall,
                    color = TbOnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(state = logListState, modifier = Modifier.fillMaxSize()) {
                    items(logLines) { line ->
                        Text(
                            "▸ $line",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = TbOnSurface.copy(alpha = 0.85f),
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Error or done ────────────────────────────────────────────────
            if (errorMessage != null) {
                Surface(color = TbErrorContainer, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Text("⚠ $errorMessage", color = TbError,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.height(8.dp))
            }

            if (isFinished) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isSuccess) TbPrimary else TbError),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (isSuccess) "Done" else "Close", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepRow(step: OperationStep) {
    val spin by rememberInfiniteTransition(label = "spin_${step.index}").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "spin"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when (step.status) {
                StepStatus.DONE       -> Icon(Icons.Default.CheckCircle, null, tint = TbAccentGreen, modifier = Modifier.size(24.dp))
                StepStatus.FAILED     -> Icon(Icons.Default.Cancel, null, tint = TbError, modifier = Modifier.size(24.dp))
                StepStatus.IN_PROGRESS -> CircularProgressIndicator(modifier = Modifier.size(20.dp),
                    color = TbPrimary, strokeWidth = 2.5.dp)
                StepStatus.PENDING    -> Icon(Icons.Default.RadioButtonUnchecked, null,
                    tint = TbOutline, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            step.description,
            style = MaterialTheme.typography.bodyMedium,
            color = when (step.status) {
                StepStatus.DONE -> TbOnSurface
                StepStatus.IN_PROGRESS -> TbPrimary
                StepStatus.FAILED -> TbError
                StepStatus.PENDING -> TbOnSurfaceVariant
            },
            fontWeight = if (step.status == StepStatus.IN_PROGRESS) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun PulsingBackgroundGlow(isFinished: Boolean, isSuccess: Boolean) {
    if (isFinished) return
    val pulse by rememberInfiniteTransition(label = "bg_pulse").animateFloat(
        initialValue = 0.04f, targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "bg_alpha"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = Color(0xFF9B78FF).copy(alpha = pulse),
            radius = 300.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.2f)
        )
    }
}
