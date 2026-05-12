package dev.truebackup.app.backup

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BackupPartFlags(
    val apk: Boolean,
    val userCe: Boolean,
    val userDe: Boolean,
    val extData: Boolean,
    val obb: Boolean,
    val media: Boolean
)

/**
 * Writes [BackupInteropLayout.FILE_CONFIG] matching
 * [com.android.server.TrueBackupService] `writeConfig` JSON shape (version 2, backupConfig,
 * dataStates, dataStats, security).
 */
class PackageBackupConfigWriter(
    private val context: Context
) {
    fun write(
        packageName: String,
        packageDir: File,
        partFlags: BackupPartFlags
    ): File {
        val config = BackupInteropLayout.configFile(packageDir)
        val now = System.currentTimeMillis()

        val pm = context.packageManager
        val packageInfo = runCatching {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }.getOrNull()
        val appInfo = packageInfo?.applicationInfo
        val appLabel = appInfo?.let { pm.getApplicationLabel(it)?.toString() }

        val root = JSONObject()
            .put("version", 2)
            .put("package", packageName)
            .put("apk", partFlags.apk)
            .put("user_ce", partFlags.userCe)
            .put("user_de", partFlags.userDe)
            .put("ext_data", partFlags.extData)
            .put("obb", partFlags.obb)
            .put("media", partFlags.media)

        val pkgInfo = JSONObject()
        pkgInfo.put("label", appLabel ?: JSONObject.NULL)
        if (packageInfo != null) {
            pkgInfo
                .put("versionName", packageInfo.versionName)
                .put("versionCode", packageInfo.longVersionCode)
                .put("firstInstallTime", packageInfo.firstInstallTime)
                .put("lastUpdateTime", packageInfo.lastUpdateTime)
        }
        if (appInfo != null) {
            pkgInfo
                .put("flags", appInfo.flags)
                .put("uid", appInfo.uid)
                .put("sourceDir", appInfo.sourceDir)
        }
        root.put("packageInfo", pkgInfo)

        val backupConfig = JSONObject()
            .put("packageName", packageName)
            .put("userId", 0)
            .put("compression", "zip")
            .put("preserveId", now)
            .put("storagePath", packageDir.absolutePath)
            .put("createdAt", now)
        root.put("backupConfig", backupConfig)

        val dataStates = JSONObject()
            .put("apk", partFlags.apk)
            .put("userCe", partFlags.userCe)
            .put("userDe", partFlags.userDe)
            .put("externalData", partFlags.extData)
            .put("obb", partFlags.obb)
            .put("media", partFlags.media)
        root.put("dataStates", dataStates)

        val dataStats = JSONObject()
            .put("apkBytes", fileSizeIfExists(BackupInteropLayout.apkZip(packageDir)))
            .put("userBytes", fileSizeIfExists(BackupInteropLayout.userCeZip(packageDir)))
            .put("userDeBytes", fileSizeIfExists(BackupInteropLayout.userDeZip(packageDir)))
            .put("dataBytes", fileSizeIfExists(BackupInteropLayout.extDataZip(packageDir)))
            .put("obbBytes", fileSizeIfExists(BackupInteropLayout.obbZip(packageDir)))
            .put("mediaBytes", fileSizeIfExists(BackupInteropLayout.mediaZip(packageDir)))
        root.put("dataStats", dataStats)

        val security = JSONObject()
        if (appInfo != null) {
            security.put("uid", appInfo.uid)
        }
        security.put("ssaid", JSONObject.NULL)
        security.put("keystore", "unknown")
        security.put("appops", buildAppOpsJson(packageName, appInfo?.uid ?: -1))
        security.put("permissions", buildPermissionsJson(pm, packageInfo))
        root.put("security", security)

        config.parentFile?.mkdirs()
        config.writeText(root.toString(2))
        return config
    }

    private fun fileSizeIfExists(file: File): Long {
        return if (file.exists() && file.isFile) file.length() else 0L
    }

    private fun buildPermissionsJson(pm: PackageManager, pi: PackageInfo?): JSONArray {
        val perms = JSONArray()
        if (pi?.requestedPermissions == null) return perms
        val names = pi.requestedPermissions ?: return perms
        val flagsArr = pi.requestedPermissionsFlags
        for (i in names.indices) {
            val name = names[i] ?: continue
            val permInfo = try {
                pm.getPermissionInfo(name, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                continue
            }
            if (!isRuntimeDangerousPermission(permInfo)) {
                continue
            }
            var granted = false
            if (flagsArr != null && i < flagsArr.size) {
                granted = (flagsArr[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            }
            perms.put(
                JSONObject()
                    .put("name", name)
                    .put("granted", granted)
            )
        }
        return perms
    }

    /**
     * TrueBackupService fills this via [AppOpsManager.getOpsForPackage], which third-party apps
     * cannot use for arbitrary UIDs. Shape matches restore (`op` + `mode`); empty means skip replay.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun buildAppOpsJson(packageName: String, uid: Int): JSONArray {
        return JSONArray()
    }

    /** Same filter as TrueBackupService [PermissionInfo.isRuntime] (dangerous / runtime-grantable). */
    private fun isRuntimeDangerousPermission(permInfo: PermissionInfo): Boolean {
        return (permInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) ==
            PermissionInfo.PROTECTION_DANGEROUS
    }
}
