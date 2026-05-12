package dev.truebackup.app.backup

import android.content.Context
import android.content.pm.PackageManager
import dev.truebackup.app.root.PrivilegedOperations
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Root-assisted restore for TrueBackup / TrueBackupService interop layout
 * (same paths as [com.android.server.TrueBackupService] restoreFromPackageDir).
 */
class RootRestoreInteropManager(
    private val context: Context,
    private val privilegedOperations: PrivilegedOperations = PrivilegedOperations()
) {

    fun restoreFromInteropBackup(packageName: String, packageDir: File) {
        val cfg = BackupInteropLayout.configFile(packageDir)
        if (!cfg.isFile) {
            throw IllegalStateException("Missing ${BackupInteropLayout.FILE_CONFIG} in ${packageDir.absolutePath}")
        }
        val json = JSONObject(cfg.readText())
        val backupUserId = json.optJSONObject("backupConfig")?.optInt("userId", 0) ?: 0
        val dataStates = json.optJSONObject("dataStates")
        val wantApk = json.optBoolean("apk", false) || dataStates?.optBoolean("apk", false) == true
        val wantCe = json.optBoolean("user_ce", false) || dataStates?.optBoolean("userCe", false) == true
        val wantDe = json.optBoolean("user_de", false) || dataStates?.optBoolean("userDe", false) == true
        val wantExt = json.optBoolean("ext_data", false) || dataStates?.optBoolean("externalData", false) == true
        val wantObb = json.optBoolean("obb", false) || dataStates?.optBoolean("obb", false) == true
        val wantMedia = json.optBoolean("media", false) || dataStates?.optBoolean("media", false) == true

        val pm = context.packageManager
        val apkZip = BackupInteropLayout.apkZip(packageDir)

        if (!isPackageInstalled(pm, packageName) && wantApk && apkZip.isFile) {
            installApksFromZip(apkZip)
        }
        if (!isPackageInstalled(pm, packageName)) {
            throw IllegalStateException("Package $packageName is not installed; install apk.zip first or install the app.")
        }
        val uid = pm.getApplicationInfo(packageName, 0).uid

        val ceDest = "/data/user/$backupUserId/$packageName"
        val deDest = "/data/user_de/$backupUserId/$packageName"
        val extDest = "/data/media/$backupUserId/Android/data/$packageName"
        val obbDest = "/data/media/$backupUserId/Android/obb/$packageName"
        val mediaDest = "/data/media/$backupUserId/Android/media/$packageName"

        if (wantCe) {
            restoreZipTree(BackupInteropLayout.userCeZip(packageDir), ceDest, uid, uid)
        }
        if (wantDe) {
            restoreZipTree(BackupInteropLayout.userDeZip(packageDir), deDest, uid, uid)
        }
        if (wantExt) {
            val gid = statGidOrUid("/data/media/$backupUserId/Android/data", uid)
            restoreZipTree(BackupInteropLayout.extDataZip(packageDir), extDest, uid, gid, chmodOctal = "760")
        }
        if (wantObb) {
            val gid = statGidOrUid("/data/media/$backupUserId/Android/obb", uid)
            restoreZipTree(BackupInteropLayout.obbZip(packageDir), obbDest, uid, gid, chmodOctal = "770")
        }
        if (wantMedia) {
            val gid = statGidOrUid("/data/media/$backupUserId/Android/media", uid)
            restoreZipTree(BackupInteropLayout.mediaZip(packageDir), mediaDest, uid, gid, chmodOctal = "770")
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return runCatching { pm.getPackageInfo(packageName, 0); true }.getOrDefault(false)
    }

    private fun installApksFromZip(apkZip: File) {
        if (isTbk1Encrypted(apkZip)) {
            throw IllegalStateException("Encrypted apk.zip (TBK1) is not supported in-app; use system TrueBackup restore.")
        }
        val stageRoot = File(context.cacheDir, "tb_restore_apk").apply { mkdirs() }
        val staging = File(stageRoot, "apk_${UUID.randomUUID()}")
        try {
            staging.mkdirs()
            JvmZip.unzip(apkZip, staging)
            val apks = staging.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
                .sortedWith(compareBy({ if (it.name.equals("base.apk", true)) 0 else 1 }, { it.name }))
                .toList()
            if (apks.isEmpty()) {
                throw IllegalStateException("No .apk entries in ${apkZip.name}")
            }
            val paths = apks.joinToString(" ") { "'${escape(it.absolutePath)}'" }
            val r = privilegedOperations.runCustom("pm install-multiple -r $paths")
            if (!r.isSuccess) {
                throw IllegalStateException("pm install-multiple failed: ${r.output.take(400)}")
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun restoreZipTree(zip: File, destDir: String, uid: Int, gid: Int, chmodOctal: String? = null) {
        if (!zip.isFile) return
        if (isTbk1Encrypted(zip)) {
            throw IllegalStateException("Encrypted ${zip.name} (TBK1) is not supported in-app.")
        }
        val stageRoot = File(context.cacheDir, "tb_restore").apply { mkdirs() }
        val staging = File(stageRoot, "unz_${UUID.randomUUID()}")
        try {
            staging.mkdirs()
            JvmZip.unzip(zip, staging)
            val src = staging.absolutePath
            val dst = destDir
            val chmodCmd = if (chmodOctal != null) {
                " && chmod -R $chmodOctal '${escape(dst)}'"
            } else {
                " && chmod -R u+rwX '${escape(dst)}'"
            }
            val copy = "rm -rf '${escape(dst)}' && mkdir -p '${escape(dst)}' && cp -a '${escape(src)}'/'.' '${escape(dst)}'/" +
                " && chown -R $uid:$gid '${escape(dst)}'$chmodCmd"
            val r = privilegedOperations.runCustom(copy)
            if (!r.isSuccess) {
                throw IllegalStateException("Restore to $dst failed: ${r.output.take(400)}")
            }
            privilegedOperations.relabelSelinuxRecursive(dst)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun statGidOrUid(refDir: String, uidFallback: Int): Int {
        val r = privilegedOperations.runCustom("stat -c '%g' '${escape(refDir)}' 2>/dev/null || echo $uidFallback")
        return r.output.trim().toIntOrNull() ?: uidFallback
    }

    private fun isTbk1Encrypted(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false
        return file.inputStream().use { ins ->
            val m = ByteArray(4)
            if (ins.read(m) != 4) return@use false
            m[0] == 'T'.code.toByte() && m[1] == 'B'.code.toByte() &&
                m[2] == 'K'.code.toByte() && m[3] == '1'.code.toByte()
        }
    }

    private fun escape(raw: String): String = raw.replace("'", "'\"'\"'")
}
