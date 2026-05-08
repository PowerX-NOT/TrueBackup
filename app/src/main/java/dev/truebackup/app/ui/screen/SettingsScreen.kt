package dev.truebackup.app.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import dev.truebackup.app.ui.components.GlassCard
import dev.truebackup.app.ui.components.GradientButton
import dev.truebackup.app.ui.components.SectionHeader
import dev.truebackup.app.ui.theme.*

@Composable
fun SettingsScreen(
    isPasswordSet: Boolean,
    backupPath: String,
    isRooted: Boolean,
    onSetPassword: (String) -> Unit,
    onChangePassword: (String, String) -> Unit,
    onClearPassword: () -> Unit,
    onBackupPathChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf(backupPath) }

    Column(
        modifier = modifier.fillMaxSize().background(TbBackground).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Settings", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold, color = TbOnBackground)
        Text("Configure TrueBackup", style = MaterialTheme.typography.bodySmall, color = TbOnSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        // ── Root Status ──────────────────────────────────────────────────────
        SectionHeader("Root Status")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isRooted) Icons.Default.AdminPanelSettings else Icons.Default.Block,
                    null,
                    tint = if (isRooted) TbAccentGreen else TbError,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (isRooted) "Root Access Granted" else "Root Not Available",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isRooted) TbAccentGreen else TbError,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isRooted) "All operations will run with root privileges"
                        else "Install a root solution (Magisk/KernelSU) to use TrueBackup",
                        style = MaterialTheme.typography.bodySmall,
                        color = TbOnSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Encryption Password ──────────────────────────────────────────────
        SectionHeader("Encryption Password")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = TbAccentViolet, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isPasswordSet) "Password Set" else "No Password Set",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isPasswordSet) TbAccentGreen else TbOnSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isPasswordSet) "Backups are encrypted with TBK1 (AES-256-GCM)"
                        else "Backups will be stored unencrypted",
                        style = MaterialTheme.typography.bodySmall,
                        color = TbOnSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isPasswordSet) {
                    GradientButton("Set Password", onClick = { showSetPasswordDialog = true },
                        modifier = Modifier.weight(1f))
                } else {
                    OutlinedButton(
                        onClick = { showChangePasswordDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TbPrimary)
                    ) { Text("Change") }
                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TbError),
                        border = BorderStroke(1.dp, TbError.copy(alpha = 0.5f))
                    ) { Text("Clear") }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Backup Location ──────────────────────────────────────────────────
        SectionHeader("Backup Location")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, null, tint = TbAccentCyan, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Base backup directory", style = MaterialTheme.typography.labelSmall, color = TbOnSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pathInput,
                onValueChange = { pathInput = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TbPrimary, unfocusedBorderColor = TbOutline,
                    focusedContainerColor = TbSurfaceVariant, unfocusedContainerColor = TbSurfaceVariant,
                    focusedTextColor = TbOnSurface, unfocusedTextColor = TbOnSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onBackupPathChange(pathInput) },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(containerColor = TbPrimary)
            ) { Text("Apply") }
        }

        Spacer(Modifier.height(20.dp))

        // ── About ─────────────────────────────────────────────────────────────
        SectionHeader("About")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = TbOnSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("TrueBackup v1.0.0", style = MaterialTheme.typography.titleSmall,
                        color = TbOnSurface, fontWeight = FontWeight.SemiBold)
                    Text("Backup format: TBK1 (AES-256-GCM, PBKDF2-SHA256, 120 000 iter)",
                        style = MaterialTheme.typography.bodySmall, color = TbOnSurfaceVariant)
                    Text("Compatible with android_packages_apps_TrueBackup",
                        style = MaterialTheme.typography.bodySmall, color = TbOnSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    // Dialogs
    if (showSetPasswordDialog) {
        PasswordDialog(
            title = "Set Encryption Password",
            confirmLabel = "Set Password",
            requireOldPassword = false,
            onConfirm = { _, new -> onSetPassword(new); showSetPasswordDialog = false },
            onDismiss = { showSetPasswordDialog = false }
        )
    }
    if (showChangePasswordDialog) {
        PasswordDialog(
            title = "Change Password",
            confirmLabel = "Change Password",
            requireOldPassword = true,
            onConfirm = { old, new -> onChangePassword(old, new); showChangePasswordDialog = false },
            onDismiss = { showChangePasswordDialog = false }
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = TbSurfaceVariant,
            title = { Text("Clear Password?", color = TbOnSurface, fontWeight = FontWeight.SemiBold) },
            text = { Text("Existing encrypted backups cannot be restored without the password. Are you sure?",
                color = TbOnSurfaceVariant, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(onClick = { onClearPassword(); showClearConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = TbError)) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel", color = TbOnSurfaceVariant) } }
        )
    }
}

@Composable
private fun PasswordDialog(
    title: String, confirmLabel: String, requireOldPassword: Boolean,
    onConfirm: (old: String, new: String) -> Unit, onDismiss: () -> Unit
) {
    var oldPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    val mismatch = newPw.isNotEmpty() && confirmPw.isNotEmpty() && newPw != confirmPw

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TbSurfaceVariant,
        title = { Text(title, color = TbOnSurface, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (requireOldPassword) {
                    OutlinedTextField(oldPw, { oldPw = it }, label = { Text("Current Password") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = settingsFieldColors())
                }
                OutlinedTextField(newPw, { newPw = it }, label = { Text("New Password") },
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton({ showNew = !showNew }) {
                        Icon(if (showNew) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TbOnSurfaceVariant)
                    }},
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = settingsFieldColors())
                OutlinedTextField(confirmPw, { confirmPw = it }, label = { Text("Confirm Password") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    isError = mismatch, supportingText = { if (mismatch) Text("Passwords do not match", color = TbError) },
                    modifier = Modifier.fillMaxWidth(), colors = settingsFieldColors())
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(oldPw, newPw) },
                enabled = newPw.isNotEmpty() && newPw == confirmPw && (!requireOldPassword || oldPw.isNotEmpty()),
                colors = ButtonDefaults.buttonColors(containerColor = TbPrimary)
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = TbOnSurfaceVariant) } }
    )
}

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TbPrimary, unfocusedBorderColor = TbOutline,
    focusedContainerColor = TbSurfaceContainer, unfocusedContainerColor = TbSurfaceContainer,
    focusedTextColor = TbOnSurface, unfocusedTextColor = TbOnSurface,
    focusedLabelColor = TbPrimary, unfocusedLabelColor = TbOnSurfaceVariant
)
