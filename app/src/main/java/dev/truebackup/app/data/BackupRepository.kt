package dev.truebackup.app.data

import android.util.Log
import dev.truebackup.app.engine.BackupEngine
import dev.truebackup.app.engine.ConfigWriter
import dev.truebackup.app.engine.BackupMetadata
import dev.truebackup.app.engine.OwnershipEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class BackupEntry(
    val packageName: String,
    val label: String?,
    val pkgDir: File,
    val metadata: BackupMetadata?,
    val totalBytes: Long,
    val backedUpAt: Long,
    val isEncrypted: Boolean
)

class BackupRepository {

    private val TAG = "BackupRepository"

    /** Lists all backup entries under [basePath]. Mirrors TrueBackupService.listBackedUpApps(). */
    suspend fun listBackups(basePath: String): List<BackupEntry> = withContext(Dispatchers.IO) {
        val appsDir = resolveAppsDir(basePath) ?: return@withContext emptyList()
        val pkgDirs = appsDir.listFiles() ?: return@withContext emptyList()

        pkgDirs.mapNotNull { pkgDir ->
            if (!pkgDir.isDirectory) return@mapNotNull null
            val configFile = File(pkgDir, "package_restore_config.json")
            if (!configFile.isFile) return@mapNotNull null

            val meta = try { ConfigWriter.fromJson(configFile.readText()) } catch (_: Exception) { null }
            val pkg = meta?.packageName?.takeIf { it.isNotEmpty() } ?: pkgDir.name
            val label = meta?.pkgInfo?.label
            val totalBytes = meta?.dataStats?.totalBytes ?: dirSize(pkgDir)
            val createdAt = meta?.backupConfig?.createdAt ?: configFile.lastModified()
            val encrypted = hasEncryptedZip(pkgDir)

            BackupEntry(pkg, label, pkgDir, meta, totalBytes, createdAt, encrypted)
        }.sortedByDescending { it.backedUpAt }
    }

    /** Reads metadata for a single package backup. */
    suspend fun readMetadata(basePath: String, packageName: String): BackupMetadata? = withContext(Dispatchers.IO) {
        val appsDir = resolveAppsDir(basePath) ?: return@withContext null
        val pkgDir = findPkgDir(appsDir, packageName) ?: return@withContext null
        val configFile = File(pkgDir, "package_restore_config.json")
        if (!configFile.isFile) return@withContext null
        try { ConfigWriter.fromJson(configFile.readText()) } catch (_: Exception) { null }
    }

    /** Deletes a package backup directory. Path must contain /apps/ (safety check in OwnershipEngine). */
    suspend fun deleteBackup(basePath: String, packageName: String): Boolean = withContext(Dispatchers.IO) {
        val appsDir = resolveAppsDir(basePath) ?: return@withContext false
        val pkgDir = findPkgDir(appsDir, packageName) ?: return@withContext false
        if (!pkgDir.isDirectory) return@withContext false
        OwnershipEngine.deleteTree(pkgDir.absolutePath)
    }

    // -------------------------------------------------------------------------
    // Helpers mirroring TrueBackupService.resolveAppsDir()
    // -------------------------------------------------------------------------

    fun resolveAppsDir(basePath: String): File? {
        val base = File(basePath)
        // Direct <base>/apps
        if (base.isDirectory && base.name == "apps") return base
        // <base>/backup/apps
        if (base.isDirectory && base.name == "backup") {
            val c = File(base, "apps"); if (c.isDirectory) return c
        }
        // <base>/backup/apps
        val c1 = File(File(base, "backup"), "apps"); if (c1.isDirectory) return c1
        // <base>/apps
        val c2 = File(base, "apps"); if (c2.isDirectory) return c2
        return null
    }

    private fun findPkgDir(appsDir: File, packageName: String): File? {
        // Direct name match
        val direct = File(appsDir, packageName)
        if (File(direct, "package_restore_config.json").isFile) return direct
        // Scan JSON for packageName
        return appsDir.listFiles()?.firstOrNull { dir ->
            if (!dir.isDirectory) return@firstOrNull false
            val cfg = File(dir, "package_restore_config.json")
            if (!cfg.isFile) return@firstOrNull false
            try {
                val meta = ConfigWriter.fromJson(cfg.readText())
                meta?.packageName == packageName || dir.name == packageName
            } catch (_: Exception) { dir.name == packageName }
        }
    }

    private fun dirSize(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun hasEncryptedZip(dir: File): Boolean {
        val magic = byteArrayOf('T'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())
        return dir.walkTopDown().filter { it.isFile && it.extension == "zip" }.any { f ->
            try {
                val buf = ByteArray(4); f.inputStream().use { it.read(buf) }; buf.contentEquals(magic)
            } catch (_: Exception) { false }
        }
    }
}
