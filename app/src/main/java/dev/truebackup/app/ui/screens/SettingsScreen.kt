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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.truebackup.app.backup.BackupOpenSslTarEncTree
import dev.truebackup.app.root.RootPreflight
import dev.truebackup.app.root.RootPreflightResult
import dev.truebackup.app.root.RootSessionCoordinator
import dev.truebackup.app.root.RootShellClient
import dev.truebackup.app.settings.RootAccessRepository
import dev.truebackup.app.settings.AppSettingsRepository
import dev.truebackup.app.settings.PasswordChangeRekeySession
import dev.truebackup.app.settings.RegistrationPasswordStore
import dev.truebackup.app.ui.util.resolvePrimaryStoragePathFromTreeUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onNavigateToReencrypt: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { AppSettingsRepository(context) }
    val passwordStore = remember(context) { RegistrationPasswordStore(context) }
    val rootAccessRepo = remember(context) { RootAccessRepository(context) }
    val preflight = remember { RootPreflight() }
    val cachedRootResult by rootAccessRepo.cachedResult.collectAsState(initial = null)
    val backupBasePath by repo.backupBasePath.collectAsState(initial = null)

    var hasRegisteredPassword by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        hasRegisteredPassword = withContext(Dispatchers.IO) { passwordStore.isConfigured() }
    }

    var registerNew by remember { mutableStateOf("") }
    var registerConfirm by remember { mutableStateOf("") }
    var changeOld by remember { mutableStateOf("") }
    var changeNew by remember { mutableStateOf("") }
    var changeConfirm by remember { mutableStateOf("") }
    var passwordPolicyError by remember { mutableStateOf<String?>(null) }
    var showRegisterPasswordDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var isCheckingRoot by remember { mutableStateOf(false) }
    var rootResult by remember { mutableStateOf<RootPreflightResult?>(null) }

    val rootProbeMutex = remember { Mutex() }

    LaunchedEffect(cachedRootResult) {
        if (!isCheckingRoot) {
            rootResult = cachedRootResult
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val selected = resolvePrimaryStoragePathFromTreeUri(uri)
        scope.launch {
            repo.setBackupBasePath(selected)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Runtime behavior and backup preferences.", style = MaterialTheme.typography.bodyMedium)
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
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // ── Security (registration password / backup crypto) ───────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Security",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.backup_password_required_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = when (hasRegisteredPassword) {
                    true  -> stringResource(R.string.password_status_registered)
                    false -> stringResource(R.string.password_status_not_registered)
                    null  -> "…"
                },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                val backupFolderPath = backupBasePath?.trim().orEmpty()
                val backupPathReady = backupFolderPath.isNotEmpty()
                if (hasRegisteredPassword == false) {
                    if (!backupPathReady) {
                        Text(
                            text = stringResource(R.string.password_register_requires_backup_folder),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = backupPathReady,
                        onClick = {
                            if (!backupPathReady) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.password_register_requires_backup_folder),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            passwordPolicyError = null
                            registerNew = ""
                            registerConfirm = ""
                            showRegisterPasswordDialog = true
                        }
                    ) {
                        Text(stringResource(R.string.password_register))
                    }
                } else if (hasRegisteredPassword == true) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            passwordPolicyError = null
                            changeOld = ""
                            changeNew = ""
                            changeConfirm = ""
                            showChangePasswordDialog = true
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
                            showRegisterPasswordDialog = false
                            showChangePasswordDialog = false
                            withContext(Dispatchers.IO) {
                                passwordStore.clear()
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
        Spacer(modifier = Modifier.height(16.dp))

        // ── Root status (auto-checked) ─────────────────────────────────────────
        RootSettingsStatusCard(
            isChecking = isCheckingRoot,
            result = rootResult,
            onRecheck = {
                if (!isCheckingRoot) {
                    scope.launch {
                        rootProbeMutex.withLock {
                            isCheckingRoot = true
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    RootSessionCoordinator.stopSession(context)
                                    RootShellClient.stopDaemon()
                                    preflight.verify().also { verified ->
                                        rootAccessRepo.saveVerification(verified)
                                        if (verified.isRootAvailable) {
                                            RootSessionCoordinator.ensureSessionAfterSetup(context)
                                        }
                                    }
                                }
                                rootResult = result
                            } finally {
                                isCheckingRoot = false
                            }
                        }
                    }
                }
            }
        )

        if (showRegisterPasswordDialog) {
            AlertDialog(
                onDismissRequest = {
                    showRegisterPasswordDialog = false
                    passwordPolicyError = null
                    registerNew = ""
                    registerConfirm = ""
                },
                title = { Text(stringResource(R.string.password_dialog_register_title)) },
                text = {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(scrollState)
                    ) {
                        passwordPolicyError?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
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
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                passwordPolicyError = null
                                val baseTrimmed = backupBasePath?.trim()?.takeIf { it.isNotEmpty() }
                                if (baseTrimmed == null) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.password_register_requires_backup_folder),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                }
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
                                    val base = baseTrimmed
                                    if (BackupOpenSslTarEncTree.hasAnyEncryptedArchives(base)) {
                                        if (!BackupOpenSslTarEncTree.canDecryptAnyEncrypted(base, registerNew, context.cacheDir)) {
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
                                        passwordPolicyError = null
                                        showRegisterPasswordDialog = false
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
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showRegisterPasswordDialog = false
                            passwordPolicyError = null
                            registerNew = ""
                            registerConfirm = ""
                        }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        if (showChangePasswordDialog) {
            AlertDialog(
                onDismissRequest = {
                    showChangePasswordDialog = false
                    passwordPolicyError = null
                    changeOld = ""
                    changeNew = ""
                    changeConfirm = ""
                },
                title = { Text(stringResource(R.string.password_dialog_change_title)) },
                text = {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(scrollState)
                    ) {
                        passwordPolicyError?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
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
                    }
                },
                confirmButton = {
                    TextButton(
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
                                    val base = backupBasePath?.trim()?.takeIf { it.isNotEmpty() }
                                    if (base != null && BackupOpenSslTarEncTree.hasAnyEncryptedArchives(base)) {
                                        return@withContext "nav_reencrypt"
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
                                    "nav_reencrypt" -> {
                                        val base = backupBasePath?.trim()?.trimEnd('/').orEmpty()
                                        if (base.isBlank()) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.password_rekey_failed),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            PasswordChangeRekeySession.prepare(
                                                base,
                                                changeOld,
                                                changeNew
                                            )
                                            changeOld = ""
                                            changeNew = ""
                                            changeConfirm = ""
                                            passwordPolicyError = null
                                            showChangePasswordDialog = false
                                            onNavigateToReencrypt()
                                        }
                                    }
                                    "change" -> Toast.makeText(
                                        context,
                                        context.getString(R.string.password_save_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    else -> {
                                        changeOld = ""
                                        changeNew = ""
                                        changeConfirm = ""
                                        passwordPolicyError = null
                                        showChangePasswordDialog = false
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
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showChangePasswordDialog = false
                            passwordPolicyError = null
                            changeOld = ""
                            changeNew = ""
                            changeConfirm = ""
                        }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun RootSettingsStatusCard(
    isChecking: Boolean,
    result: RootPreflightResult?,
    onRecheck: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        isChecking -> scheme.surfaceContainerHigh
        result?.isRootAvailable == true -> scheme.primaryContainer.copy(alpha = 0.42f)
        result != null -> scheme.errorContainer.copy(alpha = 0.38f)
        else -> scheme.surfaceContainerHigh
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.root_status_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (!isChecking) {
                    TextButton(onClick = onRecheck) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.root_status_check_again))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            AnimatedVisibility(
                visible = isChecking,
                enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(150))
            ) {
                Column {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.root_status_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            AnimatedVisibility(
                visible = !isChecking && result != null,
                enter = expandVertically(tween(260)) + fadeIn(tween(260)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(150))
            ) {
                result?.let { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (r.isRootAvailable) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null,
                            tint = if (r.isRootAvailable) scheme.primary else scheme.error,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (r.isRootAvailable) {
                                    stringResource(R.string.root_status_ready_title)
                                } else {
                                    stringResource(R.string.root_status_unavailable_title)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (r.isRootAvailable) {
                                    stringResource(R.string.root_status_ready_caption)
                                } else {
                                    r.message
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
