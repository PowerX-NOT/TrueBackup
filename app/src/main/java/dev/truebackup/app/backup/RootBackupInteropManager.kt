package dev.truebackup.app.backup

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import dev.truebackup.app.root.PrivilegedOperations
import java.io.File
import java.util.UUID

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
    private val extDataBase = "/data/media/0/Android/data"
    private val obbBase = "/data/media/0/Android/obb"
    private val mediaBase = "/data/media/0/Android/media"

    fun createBackupArchives(request: RootBackupRequest): RootBackupPlanResult {
        val packageDir = BackupInteropLayout.packageBackupDir(
            basePath = request.basePath,
            packageName = request.packageName
        )
        ensureBackupFolders(packageDir)
        packageDir.mkdirs()

        val targetApp: ApplicationInfo? = runCatching {
            context.packageManager.getApplicationInfo(request.packageName, 0)
        }.getOrNull()

        val userCePath = targetApp?.dataDir
            ?: "/data/user/0/${request.packageName}"
        val userDePath: String? = when {
            targetApp != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                val de = targetApp.deviceProtectedDataDir
                if (!de.isNullOrEmpty()) de else null
            }
            else -> "/data/user_de/0/${request.packageName}"
        }

        val apk = zipApkIfInstalled(request.packageName, BackupInteropLayout.apkZip(packageDir))
        val userCe = zipDirIfPresent(userCePath, BackupInteropLayout.userCeZip(packageDir))
        val userDe = userDePath?.let { zipDirIfPresent(it, BackupInteropLayout.userDeZip(packageDir)) } ?: false
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
        val sourceApk = appInfo.sourceDir ?: return false
        if (!isFile(sourceApk)) return false

        val stageRoot = File(context.cacheDir, "tb_stage").apply { mkdirs() }
        val staging = File(stageRoot, "apk_${packageName}_${UUID.randomUUID()}")
        val stagingPath = staging.absolutePath
        val splitSourceDirs = appInfo.splitSourceDirs?.toList().orEmpty()

        val builder = StringBuilder()
        builder.append("mkdir -p '${escape(stagingPath)}' && ")
        builder.append("cp '${escape(sourceApk)}' '${escape("$stagingPath/base.apk")}'")
        splitSourceDirs.forEach { split ->
            val splitName = File(split).name
            builder.append(" && cp '${escape(split)}' '${escape("$stagingPath/$splitName")}'")
        }
        builder.append(" && chmod -R a+rX '${escape(stagingPath)}'")

        val copyResult = privilegedOperations.runCustom(builder.toString())
        if (!copyResult.isSuccess) {
            privilegedOperations.removeRecursive(stagingPath)
            return false
        }
        return try {
            JvmZip.zipDirectory(staging, destinationZip)
            destinationZip.exists() && destinationZip.length() > 0L
        } catch (_: Exception) {
            false
        } finally {
            privilegedOperations.removeRecursive(stagingPath)
        }
    }

    private fun zipDirIfPresent(sourceDir: String, destinationZip: File): Boolean {
        if (!isDirectory(sourceDir)) {
            return false
        }
        val stageRoot = File(context.cacheDir, "tb_stage").apply { mkdirs() }
        val staging = File(stageRoot, "dir_${UUID.randomUUID()}")
        val stagingPath = staging.absolutePath
        staging.mkdirs()
        val mirror = privilegedOperations.mirrorCopyDirectoryContents(sourceDir, stagingPath)
        if (!mirror.isSuccess) {
            privilegedOperations.removeRecursive(stagingPath)
            return false
        }
        return try {
            JvmZip.zipDirectory(staging, destinationZip)
            destinationZip.exists() && destinationZip.length() > 0L
        } catch (_: Exception) {
            false
        } finally {
            privilegedOperations.removeRecursive(stagingPath)
        }
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
