package dev.truebackup.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.truebackup.app.R
import dev.truebackup.app.backup.BackupTbk1Tree
import dev.truebackup.app.root.RootPreflight
import dev.truebackup.app.root.RootPreflightResult
import dev.truebackup.app.settings.AppSettingsRepository
import dev.truebackup.app.settings.RegistrationPasswordStore
import dev.truebackup.app.ui.util.resolvePrimaryStoragePathFromTreeUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { AppSettingsRepository(context) }
    val passwordStore = remember(context) { RegistrationPasswordStore(context) }
    val preflight = remember { RootPreflight() }
    val backupBasePath by repo.backupBasePath.collectAsState(initial = null)
    val encryptionEnabled by repo.backupEncryptionEnabled.collectAsState(initial = false)

    var hasRegisteredPassword by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasRegisteredPassword = withContext(Dispatchers.IO) { passwordStore.isConfigured() }
    }

    var registerNew by remember { mutableStateOf("") }
    var registerConfirm by remember { mutableStateOf("") }
    var changeOld by remember { mutableStateOf("") }
    var changeNew by remember { mutableStateOf("") }
    var changeConfirm by remember { mutableStateOf("") }
    var passwordPolicyError by remember { mutableStateOf<String?>(null) }
    var verifyRootAtStartup by remember { mutableStateOf(true) }
    var isCheckingRoot by remember { mutableStateOf(false) }
    var rootResult by remember { mutableStateOf<RootPreflightResult?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val selected = resolvePrimaryStoragePathFromTreeUri(uri)
        scope.launch {
            repo.setBackupBasePath(selected)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Runtime behavior and backup preferences.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // ── Root status card ─────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Root status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                AnimatedVisibility(
                    visible = isCheckingRoot,
                    enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                ) {
                    Column {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (isCheckingRoot) return@Button
                        isCheckingRoot = true
                        rootResult = null
                        scope.launch {
                            rootResult = withContext(Dispatchers.IO) { preflight.verify() }
                            isCheckingRoot = false
                        }
                    }
                ) {
                    Text(if (isCheckingRoot) "Checking..." else "Run root preflight")
                }

                AnimatedVisibility(
                    visible = rootResult != null,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                ) {
                    rootResult?.let { result ->
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(result.message, style = MaterialTheme.typography.bodyMedium)
                            if (result.output.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Output: ${result.output}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // ── Backup destination card ───────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Backup destination",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                FolderPathDisplay(path = backupBasePath)

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { folderPicker.launch(null) }
                ) {
                    Text(if (backupBasePath.isNullOrBlank()) "Choose backup folder" else "Change folder")
                }
                Spacer(modifier = Modifier.height(16.dp))

                SettingRow(
                    title = "Verify root on startup",
                    checked = verifyRootAtStartup,
                    onCheckedChange = { verifyRootAtStartup = it }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingRow(
                    title = "Encrypt new backups (TBK1)",
                    checked = encryptionEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            repo.setBackupEncryptionEnabled(enabled)
                        }
                    }
                )
                Text(
                    "Uses the same TBK1 format as system TrueBackup (truebackupd). " +
                        "Register the same passphrase used on the ROM to open existing encrypted backups; " +
                        "turn on “Encrypt new backups” only after a password is saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                passwordPolicyError?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (!hasRegisteredPassword) {
                    Text(
                        "Register password",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = registerNew,
                        onValueChange = { registerNew = it; passwordPolicyError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.password_new)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = registerConfirm,
                        onValueChange = { registerConfirm = it; passwordPolicyError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.password_confirm)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                passwordPolicyError = null
                                if (registerNew.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.password_enter_new),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                }
                                if (registerNew != registerConfirm) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.password_mismatch),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                }
                                val result = withContext(Dispatchers.IO) {
                                    val base = backupBasePath
                                    if (BackupTbk1Tree.hasTbk1Archives(base)) {
                                        if (!BackupTbk1Tree.canDecryptAnyTbk1(base, registerNew, context.cacheDir)) {
                                            return@withContext "mismatch"
                                        }
                                    }
                                    if (!passwordStore.writePlaintext(registerNew)) {
                                        return@withContext "write"
                                    }
                                    "ok"
                                }
                                when (result) {
                                    "mismatch" -> {
                                        passwordPolicyError =
                                            context.getString(R.string.password_backup_mismatch)
                                    }
                                    "write" -> {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.password_save_failed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    else -> {
                                        hasRegisteredPassword = true
                                        registerNew = ""
                                        registerConfirm = ""
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.password_saved),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.password_register))
                    }
                } else {
                    Text(
                        "Change password",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = changeOld,
                        onValueChange = { changeOld = it; passwordPolicyError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.password_old)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = changeNew,
                        onValueChange = { changeNew = it; passwordPolicyError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.password_new)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = changeConfirm,
                        onValueChange = { changeConfirm = it; passwordPolicyError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.password_confirm)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                passwordPolicyError = null
                                if (changeOld.isBlank() || changeNew.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.password_enter_all),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                }
                                if (changeNew != changeConfirm) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.password_mismatch),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                }
                                val result = withContext(Dispatchers.IO) {
                                    val cur = passwordStore.readPlaintext()
                                    if (cur != changeOld) {
                                        return@withContext "old"
                                    }
                                    val base = backupBasePath
                                    if (BackupTbk1Tree.hasTbk1Archives(base)) {
                                        if (!BackupTbk1Tree.rekeyAllTbk1(base!!, changeOld, changeNew, context.cacheDir)) {
                                            return@withContext "rekey"
                                        }
                                    }
                                    if (!passwordStore.changePlaintext(changeOld, changeNew)) {
                                        return@withContext "change"
                                    }
                                    "ok"
                                }
                                when (result) {
                                    "old" -> Toast.makeText(
                                        context,
                                        context.getString(R.string.password_wrong_old),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    "rekey" -> Toast.makeText(
                                        context,
                                        context.getString(R.string.password_rekey_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    "change" -> Toast.makeText(
                                        context,
                                        context.getString(R.string.password_save_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    else -> {
                                        changeOld = ""
                                        changeNew = ""
                                        changeConfirm = ""
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.password_changed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.password_change))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            passwordPolicyError = null
                            withContext(Dispatchers.IO) {
                                passwordStore.clear()
                                repo.setBackupEncryptionEnabled(false)
                            }
                            hasRegisteredPassword = false
                            registerNew = ""
                            registerConfirm = ""
                            changeOld = ""
                            changeNew = ""
                            changeConfirm = ""
                            Toast.makeText(
                                context,
                                context.getString(R.string.password_cleared),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Text("Clear password")
                }
            }
        }
    }
}

/**
 * Styled read-only path display with a folder icon.
 * Shows the last path segment in bold and the parent path muted above it.
 */
@Composable
private fun FolderPathDisplay(path: String?) {
    val scheme = MaterialTheme.colorScheme
    val hasPath = !path.isNullOrBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (hasPath) Icons.Outlined.Folder else Icons.Outlined.FolderOff,
            contentDescription = null,
            tint = if (hasPath) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        if (hasPath) {
            val p = path!!
            val segments = p.trimEnd('/').split('/')
            val folderName = segments.last()
            val parentPath = segments.dropLast(1).joinToString("/").ifBlank { "/" }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = parentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = "No folder selected",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
