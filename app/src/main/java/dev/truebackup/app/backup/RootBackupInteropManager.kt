package dev.truebackup.app.backup

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.system.Os
import android.util.Log
import dev.truebackup.app.root.PrivilegedOperationResult
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

/**
 * Internal (CE/DE) and external paths follow Android-DataBackup
 * ([com.xayah.core.util.PathUtil], [com.xayah.core.data.util.DataType.srcDir]):
 * `/data/user/<userId>/<packageName>`, `/data/user_de/<userId>/<packageName>`,
 * `/data/media/<userId>/Android/{data,obb,media}/<packageName>`.
 *
 * Staging: root `cp -a` (same as a manual copy), then tar-with-excludes as fallback;
 * [JvmZip] writes user.zip. Target tree is chowned using numeric uid:gids from [android.system.Os.stat]
 * (shell `stat -c` is often missing on device).
 */
class RootBackupInteropManager(
    private val context: Context,
    private val privilegedOperations: PrivilegedOperations = PrivilegedOperations()
) {
    private val configWriter = PackageBackupConfigWriter(context)

    fun createBackupArchives(request: RootBackupRequest): RootBackupPlanResult {
        val userId = userIdFromUid(Process.myUid())
        val packageName = request.packageName
        Log.i(TAG, "createBackupArchives pkg=$packageName userId=$userId myUid=${Process.myUid()} owner=${appOwnerSpec()}")

        val packageDir = BackupInteropLayout.packageBackupDir(
            basePath = request.basePath,
            packageName = packageName
        )
        ensureBackupFolders(packageDir)
        packageDir.mkdirs()
        fixBackupOutputTreeOwnership(packageDir)

        val targetApp: ApplicationInfo? = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull()

        // PathUtil.getPackageUserDir + getDataSrc — same as Android-DataBackup PACKAGE_USER.
        val userCeByProfile = "/data/user/$userId/$packageName"
        val dataDirHint = targetApp?.dataDir
        val userCePath = when {
            isDirectory(userCeByProfile) -> userCeByProfile
            !dataDirHint.isNullOrBlank() && isDirectory(dataDirHint) -> dataDirHint
            else -> userCeByProfile
        }

        val userDeByProfile = "/data/user_de/$userId/$packageName"
        val deviceDeHint =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) targetApp?.deviceProtectedDataDir else null
        val userDePath: String? = when {
            isDirectory(userDeByProfile) -> userDeByProfile
            !deviceDeHint.isNullOrBlank() && isDirectory(deviceDeHint) -> deviceDeHint
            else -> userDeByProfile.takeIf { isDirectory(it) }
        }

        val extRoot = "/data/media/$userId/Android/data"
        val obbRoot = "/data/media/$userId/Android/obb"
        val mediaRoot = "/data/media/$userId/Android/media"

        val apk = zipApkIfInstalled(packageName, BackupInteropLayout.apkZip(packageDir))
        val userCe = zipInternalOrExternalData(
            parentDir = File(userCePath).parent ?: "/data/user/$userId",
            dirEntry = packageName,
            physicalPathForTest = userCePath,
            destinationZip = BackupInteropLayout.userCeZip(packageDir),
            excludes = internalDataBackupExcludes(packageName)
        )
        val userDe = userDePath?.let { de ->
            zipInternalOrExternalData(
                parentDir = File(de).parent ?: "/data/user_de/$userId",
                dirEntry = packageName,
                physicalPathForTest = de,
                destinationZip = BackupInteropLayout.userDeZip(packageDir),
                excludes = internalDataBackupExcludes(packageName)
            )
        } ?: false
        val extData = zipInternalOrExternalData(
            parentDir = extRoot,
            dirEntry = packageName,
            physicalPathForTest = "$extRoot/$packageName",
            destinationZip = BackupInteropLayout.extDataZip(packageDir),
            excludes = externalScopedBackupExcludes(packageName)
        )
        val obb = zipInternalOrExternalData(
            parentDir = obbRoot,
            dirEntry = packageName,
            physicalPathForTest = "$obbRoot/$packageName",
            destinationZip = BackupInteropLayout.obbZip(packageDir),
            excludes = externalScopedBackupExcludes(packageName)
        )
        val media = zipInternalOrExternalData(
            parentDir = mediaRoot,
            dirEntry = packageName,
            physicalPathForTest = "$mediaRoot/$packageName",
            destinationZip = BackupInteropLayout.mediaZip(packageDir),
            excludes = externalScopedBackupExcludes(packageName)
        )

        val flags = BackupPartFlags(
            apk = apk,
            userCe = userCe,
            userDe = userDe,
            extData = extData,
            obb = obb,
            media = media
        )

        if (isDirectory(userCePath) && !userCe) {
            val msg =
                "Internal (CE) data exists at $userCePath but user.zip failed. Check logcat tag $TAG for mirror/chown/zip lines."
            Log.e(TAG, msg)
            throw IllegalStateException(msg)
        }
        if (userDePath != null && isDirectory(userDePath) && !userDe) {
            val msg =
                "Device-protected (DE) data exists at $userDePath but user_de.zip failed. Check logcat tag $TAG."
            Log.e(TAG, msg)
            throw IllegalStateException(msg)
        }

        Log.i(
            TAG,
            "parts pkg=$packageName apk=$apk userCe=$userCe userDe=$userDe ext=$extData obb=$obb media=$media"
        )

        val configFile = configWriter.write(
            packageName = packageName,
            packageDir = packageDir,
            partFlags = flags
        )
        return RootBackupPlanResult(
            packageDir = packageDir,
            configFile = configFile,
            partFlags = flags
        )
    }

    /** Matches PackagesBackupUtil.backupData exclusions for PACKAGE_USER / PACKAGE_USER_DE. */
    private fun internalDataBackupExcludes(packageName: String): List<String> =
        listOf(
            "$packageName/.ota",
            "$packageName/cache",
            "$packageName/lib",
            "$packageName/code_cache",
            "$packageName/no_backup"
        )

    /** Matches PackagesBackupUtil for PACKAGE_DATA / OBB / MEDIA (cache + Backup_*). */
    private fun externalScopedBackupExcludes(packageName: String): List<String> =
        listOf(
            "$packageName/cache",
            "$packageName/Backup_*"
        )

    /**
     * Runs `tar -C parentDir packageName` like DataBackup; [physicalPathForTest] is `parentDir/dirEntry`
     * for existence checks when [parentDir] is not the direct parent (should not happen).
     */
    private fun zipInternalOrExternalData(
        parentDir: String,
        dirEntry: String,
        physicalPathForTest: String,
        destinationZip: File,
        excludes: List<String>
    ): Boolean {
        val label = destinationZip.name
        if (!isDirectory(physicalPathForTest)) {
            Log.w(TAG, "[$label] skip: not a directory: $physicalPathForTest")
            return false
        }
        val stageRoot = File(context.cacheDir, "tb_stage").apply { mkdirs() }
        val staging = File(stageRoot, "dir_${UUID.randomUUID()}")
        val stagingPath = staging.absolutePath
        privilegedOperations.removeRecursive(stagingPath)
        staging.mkdirs()

        // Prefer cp -a first (matches manual backup; works when toybox tar --exclude/pipe is flaky).
        var populated = privilegedOperations.mirrorCopyDirectoryContents(physicalPathForTest, stagingPath)
        logOp("[$label] mirror cp", physicalPathForTest, stagingPath, populated)
        if (!populated.isSuccess) {
            privilegedOperations.removeRecursive(stagingPath)
            staging.mkdirs()
            populated = privilegedOperations.tarCopyPackageFiltered(
                parentDir = parentDir,
                packageDirEntry = dirEntry,
                destDir = stagingPath,
                excludes = excludes
            )
            logOp("[$label] tar fallback", parentDir, stagingPath, populated)
        }
        if (!populated.isSuccess) {
            privilegedOperations.removeRecursive(stagingPath)
            Log.e(TAG, "[$label] stage failed for $physicalPathForTest")
            return false
        }
        if (!chownTreeToApp(stagingPath)) {
            privilegedOperations.removeRecursive(stagingPath)
            Log.e(TAG, "[$label] chown failed owner=${appOwnerSpec()} path=$stagingPath")
            return false
        }
        val readableFiles = countFilesUnder(staging)
        Log.i(TAG, "[$label] files under staging (app-readable)=$readableFiles dir=$stagingPath")
        return try {
            JvmZip.zipDirectory(staging, destinationZip)
            val ok = destinationZip.isFile && destinationZip.canRead()
            Log.i(TAG, "[$label] zip -> ${destinationZip.absolutePath} ok=$ok bytes=${destinationZip.length()}")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "[$label] JvmZip failed", e)
            destinationZip.delete()
            false
        } finally {
            privilegedOperations.removeRecursive(stagingPath)
        }
    }

    private fun countFilesUnder(dir: File): Int =
        if (!dir.isDirectory) 0
        else dir.walkTopDown().filter { it.isFile }.count()

    private fun logOp(tag: String, a: String, b: String, r: PrivilegedOperationResult) {
        val out = r.output.take(800)
        Log.i(TAG, "$tag exit=${r.exitCode} ok=${r.isSuccess}\n  cmd=${r.command.take(500)}\n  out=$out")
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
        if (!chownTreeToApp(stagingPath)) {
            privilegedOperations.removeRecursive(stagingPath)
            return false
        }
        return try {
            JvmZip.zipDirectory(staging, destinationZip)
            destinationZip.isFile && destinationZip.canRead()
        } catch (_: Exception) {
            destinationZip.delete()
            false
        } finally {
            privilegedOperations.removeRecursive(stagingPath)
        }
    }

    private fun fixBackupOutputTreeOwnership(packageDir: File) {
        if (!chownTreeToApp(packageDir.absolutePath)) {
            throw IllegalStateException(
                "Could not chown backup tree to app uid; int_data zips will fail. Path: ${packageDir.absolutePath}"
            )
        }
    }

    /**
     * Android often has no GNU `stat -c`; empty `$(stat …)` breaks `chown` and leaves staging owned by
     * the target app (e.g. u0_a463), so this process cannot read `databases/` and [JvmZip] yields empty zips.
     * Use numeric uid:gid from [Os.stat] on our [Context.getFilesDir] (same as `ls -n` for this app).
     */
    private fun appOwnerSpec(): String {
        return try {
            val st = Os.stat(context.filesDir.absolutePath)
            "${st.st_uid}:${st.st_gid}"
        } catch (_: Throwable) {
            val u = context.applicationInfo.uid
            "$u:$u"
        }
    }

    /** @return false if root chown/chmod failed */
    private fun chownTreeToApp(path: String): Boolean {
        val own = appOwnerSpec()
        val cmd =
            "chown -R $own '${escape(path)}' && chmod -R u+rwX '${escape(path)}'"
        val r = privilegedOperations.runCustom(cmd)
        Log.i(TAG, "chownTreeToApp path=$path owner=$own exit=${r.exitCode} ok=${r.isSuccess} out=${r.output.take(400)}")
        return r.isSuccess
    }

    private fun isDirectory(path: String): Boolean {
        val result = privilegedOperations.runCustom("test -d '${escape(path)}'")
        return result.isSuccess
    }

    private fun isFile(path: String): Boolean {
        val result = privilegedOperations.runCustom("test -f '${escape(path)}'")
        return result.isSuccess
    }

    private fun escape(raw: String): String {
        return raw.replace("'", "'\"'\"'")
    }

    companion object {
        private const val TAG = "TrueBackup"

        /** Same partition as [android.os.UserHandle.getUserId] for typical Android builds. */
        private const val PER_USER_RANGE = 100_000

        private fun userIdFromUid(uid: Int): Int = uid / PER_USER_RANGE
    }
}
