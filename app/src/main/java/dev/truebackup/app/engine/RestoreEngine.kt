package dev.truebackup.app.engine

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * Orchestrates full app restore. Mirrors TrueBackupService.executeRestore() / restoreFromPackageDir().
 */
class RestoreEngine(private val context: Context) {

    private val TAG = "RestoreEngine"

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

    suspend fun restore(
        packageName: String,
        pkgDir: File,
        password: String?,
        onProgress: suspend (Progress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val totalSteps = 8
        try {
            onProgress(Progress.Log("Starting restore for $packageName from ${pkgDir.absolutePath}"))

            // 1. Install APK if needed
            onProgress(Progress.Step(1, totalSteps, "Installing APK"))
            if (!isPackageInstalled(packageName)) {
                onProgress(Progress.Log("Package not installed — installing from backup"))
                installApkFromBackup(packageName, pkgDir, password)
                onProgress(Progress.Log("APK installed"))
            } else {
                onProgress(Progress.Log("Package already installed, skipping APK install"))
            }

            val uid = getUid(packageName)

            // 2. CE data
            onProgress(Progress.Step(2, totalSteps, "Restoring internal data (CE)"))
            restoreZip(File(File(pkgDir, "int_data"), "user.zip"), "$CE_BASE/$packageName", password, uid, uid, null)

            // 3. DE data
            onProgress(Progress.Step(3, totalSteps, "Restoring internal data (DE)"))
            restoreZip(File(File(pkgDir, "int_data"), "user_de.zip"), "$DE_BASE/$packageName", password, uid, uid, null)

            // 4. External data
            onProgress(Progress.Step(4, totalSteps, "Restoring external data"))
            val extGid = statGid("/data/media/0/Android/data", uid)
            restoreZip(File(File(pkgDir, "ext_data"), "data.zip"), "$EXT_DATA_BASE/$packageName", password, uid, extGid, "0760")

            // 5. OBB
            onProgress(Progress.Step(5, totalSteps, "Restoring OBB"))
            val obbGid = statGid("/data/media/0/Android/obb", uid)
            restoreZip(File(File(pkgDir, "addl_data"), "obb.zip"), "$OBB_BASE/$packageName", password, uid, obbGid, "0770")

            // 6. Media
            onProgress(Progress.Step(6, totalSteps, "Restoring media"))
            val mediaGid = statGid("/data/media/0/Android/media", uid)
            restoreZip(File(File(pkgDir, "addl_data"), "media.zip"), "$MEDIA_BASE/$packageName", password, uid, mediaGid, "0770")

            // 7. Apply permissions & appops from config
            onProgress(Progress.Step(7, totalSteps, "Restoring permissions"))
            val configFile = File(pkgDir, "package_restore_config.json")
            if (configFile.exists()) {
                val meta = ConfigWriter.fromJson(configFile.readText())
                meta?.let { applySecurityRestore(packageName, it) }
            }

            // 8. Done
            onProgress(Progress.Step(8, totalSteps, "Finished"))
            onProgress(Progress.Finished(true))
            Log.i(TAG, "Restore finished for $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed for $packageName", e)
            onProgress(Progress.Finished(false, e.message))
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private suspend fun restoreZip(zipFile: File, targetPath: String, password: String?, uid: Int, gid: Int, mode: String?) {
        if (!zipFile.exists()) return
        var toUnzip = zipFile
        var tmp: File? = null
        try {
            if (TrueBackupCrypto.isTbk1(zipFile)) {
                if (password == null) { Log.e(TAG, "No password for encrypted zip: $zipFile"); return }
                tmp = File.createTempFile("tbk_restore_", ".zip", context.cacheDir)
                if (!TrueBackupCrypto.decryptToFile(zipFile, tmp, password)) {
                    Log.e(TAG, "Decrypt failed for $zipFile"); return
                }
                toUnzip = tmp
            }
            ArchiveEngine.unzipToDir(toUnzip, File(targetPath))
            OwnershipEngine.fixupRestoredTree(targetPath, uid, gid, mode)
        } finally {
            tmp?.delete()
        }
    }

    private suspend fun installApkFromBackup(packageName: String, pkgDir: File, password: String?) {
        val apkZip = File(File(pkgDir, "apk"), "apk.zip")
        if (!apkZip.exists()) throw Exception("APK zip not found: $apkZip")

        var toRead = apkZip
        var tmp: File? = null
        try {
            if (TrueBackupCrypto.isTbk1(apkZip)) {
                if (password == null) throw Exception("No password for encrypted apk.zip")
                tmp = File.createTempFile("tbk_apk_", ".zip", context.cacheDir)
                if (!TrueBackupCrypto.decryptToFile(apkZip, tmp, password)) throw Exception("Decrypt apk.zip failed")
                toRead = tmp
            }

            // Try root pm install first (most reliable on rooted devices)
            if (installViaPmRoot(toRead, packageName)) return

            // Fallback: PackageInstaller session
            installViaPackageInstaller(toRead, packageName)
        } finally {
            tmp?.delete()
        }
    }

    private suspend fun installViaPmRoot(apkZip: File, packageName: String): Boolean {
        // Extract base.apk to temp, then pm install
        val tmpApk = File.createTempFile("tbk_install_", ".apk", context.cacheDir)
        try {
            val extracted = extractFirstApk(apkZip, tmpApk)
            if (!extracted) return false
            // Copy to world-readable location, then pm install
            val pubPath = "/data/local/tmp/tbk_install_${System.nanoTime()}.apk"
            val cp = RootShell.exec("cp ${RootShell.quote(tmpApk.absolutePath)} $pubPath && chmod 644 $pubPath")
            if (!cp.success) return false
            val install = RootShell.exec("pm install -r $pubPath")
            RootShell.exec("rm -f $pubPath")
            return install.success || install.stdout.contains("Success", ignoreCase = true)
        } finally {
            tmpApk.delete()
        }
    }

    private fun extractFirstApk(zipFile: File, outApk: File): Boolean {
        return try {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                        outApk.outputStream().use { zis.copyTo(it) }
                        return true
                    }
                    entry = zis.nextEntry
                }
            }
            false
        } catch (e: Exception) { false }
    }

    private fun installViaPackageInstaller(apkZip: File, packageName: String) {
        val pm = context.packageManager
        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(packageName)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            ZipInputStream(FileInputStream(apkZip)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                        session.openWrite(File(entry.name).name, 0, entry.size).use { out ->
                            zis.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            val intent = Intent("dev.truebackup.app.INSTALL_RESULT")
            val pi = PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_MUTABLE)
            session.commit(pi.intentSender)
        } finally {
            session.close()
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun getUid(pkg: String): Int = try {
        context.packageManager.getApplicationInfo(pkg, 0).uid
    } catch (_: Exception) { 0 }

    private suspend fun statGid(dirPath: String, fallback: Int): Int {
        val r = RootShell.exec("stat -c '%g' ${RootShell.quote(dirPath)} 2>/dev/null")
        return if (r.success) r.output.trim().toIntOrNull() ?: fallback else fallback
    }

    private suspend fun applySecurityRestore(pkg: String, meta: BackupMetadata) {
        meta.security.permissions.forEach { perm ->
            val cmd = if (perm.granted) "pm grant $pkg ${perm.name}" else "pm revoke $pkg ${perm.name}"
            RootShell.exec(cmd)
        }
    }
}
