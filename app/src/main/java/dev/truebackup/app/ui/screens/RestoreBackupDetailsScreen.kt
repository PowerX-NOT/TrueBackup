package dev.truebackup.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.truebackup.app.R
import dev.truebackup.app.backup.BackupInteropLayout
import dev.truebackup.app.backup.LocalBackupDeletion
import dev.truebackup.app.ui.navigation.RestoreBackupDetailNavArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

private data class BackupDetailsDisplay(
    val appLabel: String,
    val packageName: String,
    val backupFolderSummary: String,
    val partRows: List<Pair<String, String>>,
    val infoRows: List<Pair<String, String>>,
    val permissionRows: List<Pair<String, String>>,
    val permissionsEmpty: Boolean,
)

/** Matches [RestoreScreen] layout: padded column, headline, cards with 12dp corners and surfaceContainerHigh. */
@Composable
fun RestoreBackupDetailsScreen(
    args: RestoreBackupDetailNavArgs,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packageDir = remember(args.packageDirAbsolutePath) { File(args.packageDirAbsolutePath) }
    var jsonRoot by remember { mutableStateOf<JSONObject?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(args.packageDirAbsolutePath) {
        loadError = null
        jsonRoot = null
        val cfg = File(packageDir, BackupInteropLayout.FILE_CONFIG)
        val parsed = withContext(Dispatchers.IO) {
            if (!cfg.isFile) {
                return@withContext Result.failure(IllegalStateException("missing_config"))
            }
            runCatching { JSONObject(cfg.readText()) }
        }
        jsonRoot = parsed.getOrNull()
        if (parsed.isFailure) {
            loadError = context.getString(R.string.backup_detail_error_read_config)
        }
    }

    val display = remember(jsonRoot, packageDir, context) {
        jsonRoot?.let { buildDisplayModel(context, it, packageDir) }
    }

    val canDelete = remember(jsonRoot, args.backupBasePath, packageDir) {
        jsonRoot != null && LocalBackupDeletion.mayDeleteBackup(args.backupBasePath, packageDir, jsonRoot)
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.backup_detail_navigate_up)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.backup_detail_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            loadError != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(loadError!!, style = MaterialTheme.typography.bodyLarge)
                }
            }
            display == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.backup_detail_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                RestoreBackupDetailsContent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    display = display,
                    packageName = display.packageName,
                    canDelete = canDelete,
                    onDeleteClick = { showDeleteDialog = true }
                )
            }
        }
    }

    if (showDeleteDialog && display != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.backup_detail_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.backup_detail_delete_confirm_message, display.appLabel)
                )
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                LocalBackupDeletion.deleteBackupDirectory(packageDir)
                            }
                            if (ok) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.backup_detail_delete_done),
                                    Toast.LENGTH_SHORT
                                ).show()
                                onDeleted()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.backup_detail_delete_failed),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.backup_detail_delete_action))
                }
            }
        )
    }
}

@Composable
private fun RestoreBackupDetailsContent(
    modifier: Modifier = Modifier,
    display: BackupDetailsDisplay,
    packageName: String,
    canDelete: Boolean,
    onDeleteClick: () -> Unit,
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96)
        }.getOrNull()
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon.asImageBitmap(),
                            contentDescription = display.appLabel,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = display.appLabel.ifBlank { "?" }.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(display.appLabel, style = MaterialTheme.typography.titleMedium)
                        Text(
                            display.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            val installed = remember(display.packageName) {
                isPackageInstalled(context.packageManager, display.packageName)
            }
            if (installed) {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${display.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.backup_detail_open_app_info))
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.backup_detail_section_storage)) {
                DetailLine(
                    title = stringResource(R.string.backup_detail_backup_folder_label),
                    body = display.backupFolderSummary
                )
            }
        }

        item {
            SectionCard(title = stringResource(R.string.backup_detail_section_parts)) {
                display.partRows.forEachIndexed { index, row ->
                    if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                    DetailLine(title = row.first, body = row.second)
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.backup_detail_section_info)) {
                display.infoRows.forEachIndexed { index, row ->
                    if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                    DetailLine(title = row.first, body = row.second)
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.backup_detail_section_permissions)) {
                if (display.permissionsEmpty) {
                    Text(
                        stringResource(R.string.backup_detail_permissions_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    display.permissionRows.forEachIndexed { index, row ->
                        if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                        DetailLine(title = row.first, body = row.second)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        stringResource(R.string.backup_detail_delete_backup),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (canDelete) {
                            stringResource(R.string.backup_detail_delete_backup_summary)
                        } else {
                            stringResource(R.string.backup_detail_delete_unavailable)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onDeleteClick,
                        enabled = canDelete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(R.string.backup_detail_delete_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), content = content)
        }
    }
}

@Composable
private fun DetailLine(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun buildDisplayModel(context: Context, root: JSONObject, backupDir: File): BackupDetailsDisplay {
    var packageName = backupDir.name
    var appLabel = packageName
    val pkgInfo = root.optJSONObject("packageInfo")
    if (pkgInfo != null) {
        val label = pkgInfo.optString("appLabel", "").takeIf { it.isNotEmpty() }
            ?: pkgInfo.optString("label", "").takeIf { it.isNotEmpty() }
        val pkgFromJson = pkgInfo.optString("packageName", "").takeIf { it.isNotEmpty() }
        if (pkgFromJson != null) packageName = pkgFromJson
        if (label != null) appLabel = label
    }

    val backupFolderSummary = when {
        backupDir.isDirectory -> backupDir.absolutePath
        else -> {
            val sp = root.optJSONObject("backupConfig")?.optString("storagePath", "")?.trim().orEmpty()
            if (sp.isNotEmpty()) sp else context.getString(R.string.backup_detail_folder_unknown)
        }
    }

    val dataStates = root.optJSONObject("dataStates")
    val dataStats = root.optJSONObject("dataStats")
    val backupConfig = root.optJSONObject("backupConfig")
    val security = root.optJSONObject("security")

    fun partSummary(rootKey: String, statesKey: String, bytesKey: String): String {
        val present = root.optBoolean(rootKey, false) ||
            (dataStates?.optBoolean(statesKey, false) == true)
        val bytes = dataStats?.optLong(bytesKey, 0L) ?: 0L
        return formatBackupPartSummary(context, present, bytes)
    }

    val partRows = listOf(
        context.getString(R.string.backup_detail_part_apk) to partSummary("apk", "apk", "apkBytes"),
        context.getString(R.string.backup_detail_part_user_ce) to partSummary("user_ce", "userCe", "userBytes"),
        context.getString(R.string.backup_detail_part_user_de) to partSummary("user_de", "userDe", "userDeBytes"),
        context.getString(R.string.backup_detail_part_ext_data) to partSummary("ext_data", "externalData", "dataBytes"),
        context.getString(R.string.backup_detail_part_obb) to partSummary("obb", "obb", "obbBytes"),
        context.getString(R.string.backup_detail_part_media) to partSummary("media", "media", "mediaBytes"),
    )

    val uid = when {
        pkgInfo != null && pkgInfo.has("uid") -> pkgInfo.optInt("uid", -1)
        security != null && security.has("uid") -> security.optInt("uid", -1)
        else -> -1
    }
    val uidStr = if (uid >= 0) uid.toString() else context.getString(R.string.backup_detail_value_unknown)

    val versionName = pkgInfo?.optString("versionName", "")?.takeIf { it.isNotEmpty() }
    val versionCode = if (pkgInfo != null && pkgInfo.has("versionCode")) {
        pkgInfo.optLong("versionCode", 0L)
    } else {
        0L
    }
    val versionSummary = when {
        versionName != null && versionCode > 0L -> "$versionName ($versionCode)"
        versionName != null -> versionName
        versionCode > 0L -> versionCode.toString()
        else -> context.getString(R.string.backup_detail_value_unknown)
    }

    val firstInstall = pkgInfo?.optLong("firstInstallTime", 0L) ?: 0L
    val lastUpdate = pkgInfo?.optLong("lastUpdateTime", 0L) ?: 0L
    val lastBackup = backupConfig?.optLong("createdAt", 0L) ?: 0L

    val infoRows = listOf(
        context.getString(R.string.backup_detail_info_uid) to uidStr,
        context.getString(R.string.backup_detail_info_version) to versionSummary,
        context.getString(R.string.backup_detail_info_first_installed) to formatEpochMillis(context, firstInstall),
        context.getString(R.string.backup_detail_info_last_update) to formatEpochMillis(context, lastUpdate),
        context.getString(R.string.backup_detail_info_last_backup) to formatEpochMillis(context, lastBackup),
    )

    val perms = security?.optJSONArray("permissions")
    val permissionRows = mutableListOf<Pair<String, String>>()
    if (perms != null && perms.length() > 0) {
        for (i in 0 until perms.length()) {
            val o = perms.optJSONObject(i) ?: continue
            val name = o.optString("name", "").takeIf { it.isNotEmpty() } ?: continue
            val granted = o.optBoolean("granted", false)
            val summary = context.getString(
                if (granted) R.string.backup_detail_permission_granted
                else R.string.backup_detail_permission_denied
            )
            permissionRows.add(name to summary)
        }
    }

    return BackupDetailsDisplay(
        appLabel = appLabel,
        packageName = packageName,
        backupFolderSummary = backupFolderSummary,
        partRows = partRows,
        infoRows = infoRows,
        permissionRows = permissionRows,
        permissionsEmpty = permissionRows.isEmpty()
    )
}

private fun formatBackupPartSummary(context: Context, present: Boolean, bytes: Long): String {
    val tf = if (present) context.getString(R.string.backup_detail_bool_true) else context.getString(R.string.backup_detail_bool_false)
    return "$tf · ${bytesToMbString(bytes)}"
}

private fun bytesToMbString(bytes: Long): String {
    if (bytes <= 0L) return "0.00 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.2f MB", mb)
}

private fun formatEpochMillis(context: Context, ms: Long): String {
    if (ms <= 0L) return context.getString(R.string.backup_detail_value_unknown)
    return DateUtils.formatDateTime(
        context,
        ms,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL
    )
}

private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
    return try {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
