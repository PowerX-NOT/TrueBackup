package dev.truebackup.app.backup

import org.json.JSONObject
import java.io.File
import java.io.Serializable

/**
 * One app folder under [BackupInteropLayout] (has [BackupInteropLayout.FILE_CONFIG]).
 * Discovers per-app backup folders that contain [BackupInteropLayout.FILE_CONFIG]
 * (same idea as system TrueBackup metadata discovery).
 */
data class InteropBackedUpPackage(
    val packageName: String,
    val label: String,
    val packageDir: File
) : Serializable

object InteropBackupIndex {

    fun listBackedUpPackages(basePath: String): List<InteropBackedUpPackage> {
        val appsRoot = File(File(basePath, BackupInteropLayout.DIR_BACKUP), BackupInteropLayout.DIR_APPS)
        if (!appsRoot.isDirectory) return emptyList()
        val dirs = appsRoot.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        val out = ArrayList<InteropBackedUpPackage>()
        for (dir in dirs) {
            val cfg = File(dir, BackupInteropLayout.FILE_CONFIG)
            if (!cfg.isFile) continue
            val (pkg, label) = readPackageAndLabel(cfg, folderName = dir.name)
            out.add(
                InteropBackedUpPackage(
                    packageName = pkg,
                    label = label.ifBlank { pkg },
                    packageDir = dir
                )
            )
        }
        return out.sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))
    }

    /** Label from [BackupInteropLayout.FILE_CONFIG] if readable; otherwise the folder name. */
    fun readBackedUpAppLabel(packageDir: File): String {
        val cfg = BackupInteropLayout.configFile(packageDir)
        if (!cfg.isFile) return packageDir.name
        val (pkg, label) = readPackageAndLabel(cfg, folderName = packageDir.name)
        return label.ifBlank { pkg }
    }

    private fun readPackageAndLabel(configFile: File, folderName: String): Pair<String, String> {
        return runCatching {
            val json = JSONObject(configFile.readText())
            val pkgInfo = json.optJSONObject("packageInfo")
            val fromJson = pkgInfo?.optString("packageName", null)?.trim()?.takeIf { it.isNotEmpty() }
            val pkg = fromJson ?: json.optString("package", "").trim().ifEmpty { folderName }
            var label = pkgInfo?.optString("label", null)?.trim().orEmpty()
            if (label.isEmpty()) {
                label = pkgInfo?.optString("appLabel", null)?.trim().orEmpty()
            }
            if (label.isEmpty()) label = pkg
            pkg to label
        }.getOrElse { folderName to folderName }
    }
}
