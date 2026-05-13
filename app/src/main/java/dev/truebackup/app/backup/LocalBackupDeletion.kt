package dev.truebackup.app.backup

import org.json.JSONObject
import java.io.File

/**
 * Local-only backup folder delete rules, aligned with system TrueBackup restore details
 * ([TrueBackupBackupDeletion] in android_packages_apps_TrueBackup).
 */
object LocalBackupDeletion {

    fun resolveAppsRoot(basePath: String): File {
        val base = File(basePath.trim()).absoluteFile
        return File(File(base, BackupInteropLayout.DIR_BACKUP), BackupInteropLayout.DIR_APPS).absoluteFile
    }

    fun isPackageDirUnderAppsRoot(appsRoot: File, packageDir: File): Boolean {
        return try {
            val root = appsRoot.canonicalFile
            val leaf = packageDir.canonicalFile
            leaf.path.startsWith(root.path + File.separator) && leaf.isDirectory
        } catch (_: Exception) {
            false
        }
    }

    fun isUnderBackupBasePath(basePath: String, dir: File): Boolean {
        return try {
            val base = File(basePath.trim()).canonicalFile
            val leaf = dir.canonicalFile
            leaf.path == base.path || leaf.path.startsWith(base.path + File.separator)
        } catch (_: Exception) {
            false
        }
    }

    fun mayDeleteBackup(backupBasePath: String, backupDir: File, jsonRoot: JSONObject?): Boolean {
        val appsRoot = runCatching { resolveAppsRoot(backupBasePath) }.getOrNull()
        if (appsRoot != null && appsRoot.isDirectory && isPackageDirUnderAppsRoot(appsRoot, backupDir)) {
            return true
        }
        if (isUnderBackupBasePath(backupBasePath, backupDir)) {
            return true
        }
        val sp = jsonRoot?.optJSONObject("backupConfig")
            ?.optString("storagePath", "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return false
        return runCatching {
            backupDir.canonicalFile == File(sp).canonicalFile
        }.getOrDefault(false)
    }

    fun deleteBackupDirectory(backupDir: File): Boolean {
        if (!backupDir.isDirectory) return false
        return runCatching { backupDir.deleteRecursively() }.getOrDefault(false)
    }
}
