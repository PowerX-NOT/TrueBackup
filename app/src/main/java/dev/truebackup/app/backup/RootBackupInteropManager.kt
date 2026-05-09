package dev.truebackup.app.backup

import android.content.Context
import dev.truebackup.app.root.PrivilegedOperations
import java.io.File

data class RootBackupRequest(
    val packageName: String,
    val basePath: String
)

data class RootBackupPlanResult(
    val packageDir: File,
    val configFile: File,
    val partFlags: BackupPartFlags
)

class RootBackupInteropManager(
    private val context: Context,
    private val privilegedOperations: PrivilegedOperations = PrivilegedOperations()
) {
    private val configWriter = PackageBackupConfigWriter(context)
    private val ceBase = "/data/user/0"
    private val deBase = "/data/user_de/0"
    private val extDataBase = "/data/media/0/Android/data"
    private val obbBase = "/data/media/0/Android/obb"
    private val mediaBase = "/data/media/0/Android/media"

    fun createBackupArchives(request: RootBackupRequest): RootBackupPlanResult {
        val packageDir = BackupInteropLayout.packageBackupDir(
            basePath = request.basePath,
            packageName = request.packageName
        )
        ensureBackupFolders(packageDir)

        val apk = zipApkIfInstalled(request.packageName, BackupInteropLayout.apkZip(packageDir))
        val userCe = zipDirIfPresent(
            "$ceBase/${request.packageName}",
            BackupInteropLayout.userCeZip(packageDir)
        )
        val userDe = zipDirIfPresent(
            "$deBase/${request.packageName}",
            BackupInteropLayout.userDeZip(packageDir)
        )
        val extData = zipDirIfPresent(
            "$extDataBase/${request.packageName}",
            BackupInteropLayout.extDataZip(packageDir)
        )
        val obb = zipDirIfPresent(
            "$obbBase/${request.packageName}",
            BackupInteropLayout.obbZip(packageDir)
        )
        val media = zipDirIfPresent(
            "$mediaBase/${request.packageName}",
            BackupInteropLayout.mediaZip(packageDir)
        )

        val flags = BackupPartFlags(
            apk = apk,
            userCe = userCe,
            userDe = userDe,
            extData = extData,
            obb = obb,
            media = media
        )

        val configFile = configWriter.write(
            packageName = request.packageName,
            packageDir = packageDir,
            partFlags = flags
        )
        return RootBackupPlanResult(
            packageDir = packageDir,
            configFile = configFile,
            partFlags = flags
        )
    }

    private fun ensureBackupFolders(packageDir: File) {
        val mkdirResult = privilegedOperations.runCustom(
            "mkdir -p '${escape(packageDir.absolutePath)}'/" +
                "{${BackupInteropLayout.DIR_APK},${BackupInteropLayout.DIR_INT_DATA}," +
                "${BackupInteropLayout.DIR_EXT_DATA},${BackupInteropLayout.DIR_ADDL_DATA}}"
        )
        if (!mkdirResult.isSuccess) {
            throw IllegalStateException("Could not create backup folders: ${mkdirResult.output}")
        }
    }

    private fun zipApkIfInstalled(packageName: String, destinationZip: File): Boolean {
        val appInfo = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrElse {
            return false
        }
        val sourceDir = appInfo.sourceDir ?: return false
        if (!isFile(sourceDir)) return false

        val staging = "/data/local/tmp/truebackup_apk_${packageName}_${System.currentTimeMillis()}"
        val splitSourceDirs = appInfo.splitSourceDirs?.toList().orEmpty()

        val builder = StringBuilder()
        builder.append("mkdir -p '${escape(staging)}' && ")
        builder.append("cp '${escape(sourceDir)}' '${escape("$staging/base.apk")}'")
        splitSourceDirs.forEach { split ->
            val splitName = File(split).name
            builder.append(" && cp '${escape(split)}' '${escape("$staging/$splitName")}'")
        }
        builder.append(" && cd '${escape(staging)}'")
        builder.append(" && zip -r '${escape(destinationZip.absolutePath)}' .")
        builder.append(" && rm -rf '${escape(staging)}'")

        val result = privilegedOperations.runCustom(builder.toString())
        if (result.isSuccess && destinationZip.exists()) return true

        privilegedOperations.runCustom("rm -rf '${escape(staging)}'")
        return false
    }

    private fun zipDirIfPresent(sourceDir: String, destinationZip: File): Boolean {
        if (!isDirectory(sourceDir)) {
            return false
        }
        val zip = privilegedOperations.zipDirectory(
            sourceDir = sourceDir,
            destinationZip = destinationZip.absolutePath
        )
        return zip.isSuccess && destinationZip.exists()
    }

    private fun isDirectory(path: String): Boolean {
        val result = privilegedOperations.runCustom("[ -d '${escape(path)}' ]")
        return result.isSuccess
    }

    private fun isFile(path: String): Boolean {
        val result = privilegedOperations.runCustom("[ -f '${escape(path)}' ]")
        return result.isSuccess
    }

    private fun escape(raw: String): String {
        return raw.replace("'", "'\"'\"'")
    }
}
