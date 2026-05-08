package dev.truebackup.app.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates full app backup. Mirrors TrueBackupService.executeBackup().
 *
 * Backup layout (interop-compatible):
 *   <basePath>/backup/apps/<pkg>/
 *     ├── package_restore_config.json
 *     ├── apk/apk.zip
 *     ├── int_data/user.zip
 *     ├── int_data/user_de.zip
 *     ├── ext_data/data.zip
 *     └── addl_data/obb.zip + media.zip
 */
class BackupEngine(private val context: Context) {

    private val TAG = "BackupEngine"

    // Source paths (match TrueBackupService constants)
    private val CE_BASE = "/data/user/0"
    private val DE_BASE = "/data/user_de/0"
    private val EXT_DATA_BASE = "/data/media/0/Android/data"
    private val OBB_BASE = "/data/media/0/Android/obb"
    private val MEDIA_BASE = "/data/media/0/Android/media"

    sealed class Progress {
        data class Step(val stepIndex: Int, val totalSteps: Int, val description: String) : Progress()
        data class Log(val message: String) : Progress()
        data class Finished(val success: Boolean, val error: String? = null) : Progress()
    }

    /**
     * Backs up [packageName] into [basePath]/backup/apps/<pkg>/.
     * Emits [Progress] events via [onProgress] callback.
     */
    suspend fun backup(
        packageName: String,
        basePath: String,
        password: String?,
        onProgress: suspend (Progress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val totalSteps = 7
        try {
            onProgress(Progress.Log("Starting backup for $packageName"))

            // 1. Resolve & create package backup dir
            onProgress(Progress.Step(1, totalSteps, "Creating backup directories"))
            val pkgDir = resolvePackageBackupDir(basePath, packageName)
            val dirs = listOf(
                File(pkgDir, "apk"), File(pkgDir, "int_data"),
                File(pkgDir, "ext_data"), File(pkgDir, "addl_data")
            )
            dirs.forEach { OwnershipEngine.mkdirs(it.absolutePath) }
            onProgress(Progress.Log("Backup dir: ${pkgDir.absolutePath}"))

            val parts = BackupParts()
            var apk = false; var ce = false; var de = false
            var ext = false; var obb = false; var media = false

            // 2. APK
            onProgress(Progress.Step(2, totalSteps, "Backing up APK"))
            val apkZip = File(File(pkgDir, "apk"), "apk.zip")
            apk = zipApk(packageName, apkZip)
            if (apk && password != null) TrueBackupCrypto.encryptInPlace(apkZip, password)
            onProgress(Progress.Log("APK backed up: $apk"))

            // 3. Internal data CE
            onProgress(Progress.Step(3, totalSteps, "Backing up internal data (CE)"))
            val ceZip = File(File(pkgDir, "int_data"), "user.zip")
            ce = zipDirIfExists("$CE_BASE/$packageName", ceZip)
            if (ce && password != null) TrueBackupCrypto.encryptInPlace(ceZip, password)
            onProgress(Progress.Log("CE data backed up: $ce"))

            // 4. Internal data DE
            onProgress(Progress.Step(4, totalSteps, "Backing up internal data (DE)"))
            val deZip = File(File(pkgDir, "int_data"), "user_de.zip")
            de = zipDirIfExists("$DE_BASE/$packageName", deZip)
            if (de && password != null) TrueBackupCrypto.encryptInPlace(deZip, password)
            onProgress(Progress.Log("DE data backed up: $de"))

            // 5. External data
            onProgress(Progress.Step(5, totalSteps, "Backing up external data"))
            val extZip = File(File(pkgDir, "ext_data"), "data.zip")
            ext = zipDirIfExists("$EXT_DATA_BASE/$packageName", extZip)
            if (ext && password != null) TrueBackupCrypto.encryptInPlace(extZip, password)

            // OBB + Media
            val obbZip = File(File(pkgDir, "addl_data"), "obb.zip")
            obb = zipDirIfExists("$OBB_BASE/$packageName", obbZip)
            if (obb && password != null) TrueBackupCrypto.encryptInPlace(obbZip, password)

            val mediaZip = File(File(pkgDir, "addl_data"), "media.zip")
            media = zipDirIfExists("$MEDIA_BASE/$packageName", mediaZip)
            if (media && password != null) TrueBackupCrypto.encryptInPlace(mediaZip, password)
            onProgress(Progress.Log("Ext/OBB/Media backed up"))

            // 6. Write config
            onProgress(Progress.Step(6, totalSteps, "Writing backup config"))
            val backedParts = BackupParts(apk, ce, de, ext, obb, media)
            val meta = buildMetadata(packageName, pkgDir, backedParts)
            val configJson = ConfigWriter.toJson(meta)
            val configFile = File(pkgDir, "package_restore_config.json")
            OwnershipEngine.writeFile(configFile.absolutePath, configJson.toByteArray())
            onProgress(Progress.Log("Config written"))

            // 7. Done
            onProgress(Progress.Step(7, totalSteps, "Finished"))
            onProgress(Progress.Finished(true))
            Log.i(TAG, "Backup finished for $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed for $packageName", e)
            onProgress(Progress.Finished(false, e.message))
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private suspend fun zipApk(packageName: String, outZip: File): Boolean {
        val pm = context.packageManager
        val ai = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            Log.e(TAG, "zipApk: package not found $packageName"); return false
        }
        val baseApk = File(ai.sourceDir ?: return false)
        val splits = ai.splitSourceDirs
        val files = mutableListOf(baseApk to "base.apk")
        splits?.forEach { split -> files.add(File(split) to File(split).name) }
        return ArchiveEngine.zipMultiFile(files, outZip)
    }

    private suspend fun zipDirIfExists(srcPath: String, outZip: File): Boolean {
        val check = RootShell.exec("test -d ${RootShell.quote(srcPath)} && echo exists")
        if (!check.output.contains("exists")) return false
        return ArchiveEngine.zipDir(File(srcPath), outZip)
    }

    fun resolvePackageBackupDir(basePath: String, packageName: String): File {
        // Always use <base>/backup/apps/<pkg> (same default as TrueBackupService)
        return File(File(File(File(basePath), "backup"), "apps"), packageName)
    }

    private fun buildMetadata(pkg: String, pkgDir: File, parts: BackupParts): BackupMetadata {
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        var pi: PackageInfo? = null
        var ai: ApplicationInfo? = null
        try {
            pi = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            ai = pi.applicationInfo
        } catch (_: Exception) {}

        val label = ai?.let { pm.getApplicationLabel(it).toString() }

        fun fileLen(f: File) = if (f.exists()) f.length() else 0L

        return BackupMetadata(
            version = 2,
            packageName = pkg,
            parts = parts,
            pkgInfo = PackageInfoSnapshot(
                label = label,
                packageName = pkg,
                versionName = pi?.versionName,
                versionCode = pi?.longVersionCode ?: 0L,
                firstInstallTime = pi?.firstInstallTime ?: 0L,
                lastUpdateTime = pi?.lastUpdateTime ?: 0L,
                flags = ai?.flags ?: 0,
                uid = ai?.uid ?: -1,
                sourceDir = ai?.sourceDir
            ),
            backupConfig = BackupConfigSection(
                packageName = pkg, userId = 0, compression = "zip",
                preserveId = now, storagePath = pkgDir.absolutePath, createdAt = now
            ),
            dataStats = DataStats(
                apkBytes = fileLen(File(File(pkgDir, "apk"), "apk.zip")),
                userBytes = fileLen(File(File(pkgDir, "int_data"), "user.zip")),
                userDeBytes = fileLen(File(File(pkgDir, "int_data"), "user_de.zip")),
                dataBytes = fileLen(File(File(pkgDir, "ext_data"), "data.zip")),
                obbBytes = fileLen(File(File(pkgDir, "addl_data"), "obb.zip")),
                mediaBytes = fileLen(File(File(pkgDir, "addl_data"), "media.zip"))
            ),
            security = SecurityInfo(uid = ai?.uid ?: -1)
        )
    }
}
