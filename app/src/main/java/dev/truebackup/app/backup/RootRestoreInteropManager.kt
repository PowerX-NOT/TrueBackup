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

    fun restoreFromInteropBackup(
        packageName: String,
        packageDir: File,
        /** Plaintext registration password; required when any part is TBK1 (matches ROM TrueBackup). */
        decryptionPassword: String? = null
    ) {
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
            installApksFromZip(apkZip, decryptionPassword)
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
            restoreZipTree(BackupInteropLayout.userCeZip(packageDir), ceDest, uid, uid, decryptionPassword = decryptionPassword)
        }
        if (wantDe) {
            restoreZipTree(BackupInteropLayout.userDeZip(packageDir), deDest, uid, uid, decryptionPassword = decryptionPassword)
        }
        if (wantExt) {
            val gid = statGidOrUid("/data/media/$backupUserId/Android/data", uid)
            restoreZipTree(BackupInteropLayout.extDataZip(packageDir), extDest, uid, gid, chmodOctal = "760", decryptionPassword = decryptionPassword)
        }
        if (wantObb) {
            val gid = statGidOrUid("/data/media/$backupUserId/Android/obb", uid)
            restoreZipTree(BackupInteropLayout.obbZip(packageDir), obbDest, uid, gid, chmodOctal = "770", decryptionPassword = decryptionPassword)
        }
        if (wantMedia) {
            val gid = statGidOrUid("/data/media/$backupUserId/Android/media", uid)
            restoreZipTree(BackupInteropLayout.mediaZip(packageDir), mediaDest, uid, gid, chmodOctal = "770", decryptionPassword = decryptionPassword)
        }
        restoreRuntimePermissionsFromConfig(json, packageName, backupUserId)
    }

    /**
     * Replays dangerous/runtime grants stored under `security.permissions` (same shape as backup
     * [PackageBackupConfigWriter]). Uses root `pm grant`; skips entries that fail (e.g. removed from manifest).
     */
    private fun restoreRuntimePermissionsFromConfig(
        json: JSONObject,
        packageName: String,
        backupUserId: Int
    ) {
        val perms = json.optJSONObject("security")?.optJSONArray("permissions") ?: return
        val escPkg = escape(packageName)
        for (i in 0 until perms.length()) {
            val entry = perms.optJSONObject(i) ?: continue
            if (!entry.optBoolean("granted", false)) continue
            val perm = entry.optString("name", "").trim()
            if (perm.isEmpty()) continue
            val escPerm = escape(perm)
            val cmd = "pm grant --user $backupUserId '$escPkg' '$escPerm'"
            privilegedOperations.runCustom(cmd)
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return runCatching { pm.getPackageInfo(packageName, 0); true }.getOrDefault(false)
    }

    private fun installApksFromZip(apkZip: File, decryptionPassword: String?) {
        val plainZip = materializePlainZip(apkZip, decryptionPassword)
        val stageRoot = File(context.cacheDir, "tb_restore_apk").apply { mkdirs() }
        val staging = File(stageRoot, "apk_${UUID.randomUUID()}")
        try {
            staging.mkdirs()
            JvmZip.unzip(plainZip, staging)
            val apks = staging.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
                .sortedWith(compareBy({ if (it.name.equals("base.apk", true)) 0 else 1 }, { it.name }))
                .toList()
            if (apks.isEmpty()) {
                throw IllegalStateException("No .apk entries in ${apkZip.name}")
            }
            if (apks.size == 1) {
                val r = privilegedOperations.installApk(apks.single().absolutePath)
                if (!r.isSuccess) {
                    throw IllegalStateException("pm install failed: ${r.output.take(400)}")
                }
            } else {
                // Avoid `pm install-multiple`: some OEM shells truncate the subcommand ("install-multipl")
                // and report an unknown command. Session install uses shorter verbs and works widely.
                installSplitApksViaPmSession(apks)
            }
        } finally {
            staging.deleteRecursively()
            if (plainZip != apkZip) plainZip.delete()
        }
    }

    /**
     * Installs split/base APK sets using `pm install-create`, `pm install-write`, and `pm install-commit`.
     */
    private fun installSplitApksViaPmSession(apks: List<File>) {
        val totalSize = apks.sumOf { it.length() }
        val create = privilegedOperations.runCustom(
            "pm install-create -r -t -d -S $totalSize"
        )
        if (!create.isSuccess) {
            throw IllegalStateException(
                "pm install-create failed (split APK install): ${create.output.take(400)}"
            )
        }
        val sessionId = parsePmInstallSessionId(create.output)
            ?: throw IllegalStateException(
                "Could not parse install session id from: ${create.output.take(400)}"
            )
        var committed = false
        try {
            for (apk in apks) {
                val size = apk.length()
                val split = escape(apk.name)
                val path = escape(apk.absolutePath)
                val write = privilegedOperations.runCustom(
                    "pm install-write -S $size $sessionId '$split' '$path'"
                )
                if (!write.isSuccess) {
                    throw IllegalStateException(
                        "pm install-write failed for ${apk.name}: ${write.output.take(400)}"
                    )
                }
            }
            val commit = privilegedOperations.runCustom("pm install-commit $sessionId")
            if (!commit.isSuccess) {
                throw IllegalStateException("pm install-commit failed: ${commit.output.take(400)}")
            }
            committed = true
        } finally {
            if (!committed) {
                privilegedOperations.runCustom("pm install-abandon $sessionId")
            }
        }
    }

    private fun parsePmInstallSessionId(output: String): Int? {
        val text = output.lineSequence().firstOrNull { it.contains('[') } ?: output
        return Regex("""\[(\d+)]""").find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun restoreZipTree(
        zip: File,
        destDir: String,
        uid: Int,
        gid: Int,
        chmodOctal: String? = null,
        decryptionPassword: String? = null
    ) {
        if (!zip.isFile) return
        val plainZip = materializePlainZip(zip, decryptionPassword)
        val stageRoot = File(context.cacheDir, "tb_restore").apply { mkdirs() }
        val staging = File(stageRoot, "unz_${UUID.randomUUID()}")
        try {
            staging.mkdirs()
            JvmZip.unzip(plainZip, staging)
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
            if (plainZip != zip) plainZip.delete()
        }
    }

    /**
     * If [zip] is TBK1, decrypts to a temp plain zip using [decryptionPassword]; otherwise returns [zip].
     */
    private fun materializePlainZip(zip: File, decryptionPassword: String?): File {
        if (!Tbk1Codec.isTbk1(zip)) return zip
        if (decryptionPassword.isNullOrBlank()) {
            throw IllegalStateException(
                "This backup is TBK1-encrypted. Open Settings and save the same registration password used when the backup was created (ROM TrueBackup or this app)."
            )
        }
        val tmp = File(context.cacheDir, "tb_plain_${UUID.randomUUID()}.zip")
        if (!Tbk1Codec.decryptToFile(zip, tmp, decryptionPassword)) {
            tmp.delete()
            throw IllegalStateException("TBK1 decrypt failed for ${zip.name} (wrong password or corrupt file).")
        }
        return tmp
    }

    private fun statGidOrUid(refDir: String, uidFallback: Int): Int {
        val r = privilegedOperations.runCustom("stat -c '%g' '${escape(refDir)}' 2>/dev/null || echo $uidFallback")
        return r.output.trim().toIntOrNull() ?: uidFallback
    }

    private fun escape(raw: String): String = raw.replace("'", "'\"'\"'")
}
