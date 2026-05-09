package dev.truebackup.app.backup

import android.content.Context
import android.content.pm.PackageManager
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

        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }.getOrNull()
        val appInfo = packageInfo?.applicationInfo
        val appLabel = appInfo?.let {
            context.packageManager.getApplicationLabel(it).toString()
        }

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
            .put("label", appLabel)
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

        config.parentFile?.mkdirs()
        config.writeText(root.toString(2))
        return config
    }

    private fun fileSizeIfExists(file: File): Long {
        return if (file.exists() && file.isFile) file.length() else 0L
    }
}
