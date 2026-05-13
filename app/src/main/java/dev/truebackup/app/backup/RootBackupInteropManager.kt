package dev.truebackup.app.backup

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.system.Os
import android.util.Log
import dev.truebackup.app.root.PrivilegedOperations
import java.io.File
import java.util.UUID

data class RootBackupRequest(
    val packageName: String,
    val basePath: String,
    /** When true, writes `.tar.enc` parts using OpenSSL `enc -aes-256-cbc -salt -pbkdf2` (same idea as shell examples). */
    val encryptArchives: Boolean = false,
    val encryptionPassword: String? = null
)

data class RootBackupPlanResult(
    val packageDir: File,
    val configFile: File,
    val partFlags: BackupPartFlags
)

/**
 * Backs up CE/DE and scoped storage paths (TrueBackup interop layout), stages with root filtered `tar`,
 * rechowns to this app via [Os.stat], then writes **tar** archives (optionally **OpenSSL-encrypted** `.tar.enc`).
 */
class RootBackupInteropManager(
    private val context: Context,
    private val privilegedOperations: PrivilegedOperations = PrivilegedOperations()
) {
    private val configWriter = PackageBackupConfigWriter(context)

    fun createBackupArchives(request: RootBackupRequest): RootBackupPlanResult {
        val shellUserId = userIdFromUid(Process.myUid())
        val packageName = request.packageName
        val encrypt = request.encryptArchives
        val password = request.encryptionPassword

        if (encrypt && password.isNullOrBlank()) {
            throw IllegalStateException(
                "Encryption is on but no registration password is set. Save a password in Settings first."
            )
        }

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

        val pathUserId = inferPathUserId(targetApp?.dataDir, shellUserId)

        val userCeByProfile = "/data/user/$pathUserId/$packageName"
        val dataDirHint = targetApp?.dataDir?.trim()?.takeIf { it.isNotEmpty() }
        val userCePath = listOfNotNull(
            dataDirHint,
            userCeByProfile,
            "/data/user/$shellUserId/$packageName",
            "/data/data/$packageName"
        ).distinct().firstOrNull { isDirectory(it) }
            ?: userCeByProfile

        val userDeByProfile = "/data/user_de/$pathUserId/$packageName"
        val deviceDeHint =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) targetApp?.deviceProtectedDataDir else null
        val userDePath: String? = listOfNotNull(
            deviceDeHint?.trim()?.takeIf { it.isNotEmpty() },
            userDeByProfile,
            "/data/user_de/$shellUserId/$packageName"
        ).distinct().firstOrNull { isDirectory(it) }

        val extRoot = "/data/media/$pathUserId/Android/data"
        val obbRoot = "/data/media/$pathUserId/Android/obb"
        val mediaRoot = "/data/media/$pathUserId/Android/media"

        val apk = tarApkIfInstalled(
            packageName,
            BackupInteropLayout.apkPartForWrite(packageDir, encrypt),
            encrypt,
            password
        )
        val userCe = tarInternalOrExternalData(
            parentDir = File(userCePath).parent ?: "/data/user/$pathUserId",
            dirEntry = packageName,
            physicalPathForTest = userCePath,
            destinationArchive = BackupInteropLayout.userCePartForWrite(packageDir, encrypt),
            excludes = internalInteropExcludes(packageName),
            encrypt = encrypt,
            password = password
        )
        val userDe = userDePath?.let { de ->
            tarInternalOrExternalData(
                parentDir = File(de).parent ?: "/data/user_de/$pathUserId",
                dirEntry = packageName,
                physicalPathForTest = de,
                destinationArchive = BackupInteropLayout.userDePartForWrite(packageDir, encrypt),
                excludes = internalInteropExcludes(packageName),
                encrypt = encrypt,
                password = password
            )
        } ?: false
        val extData = tarInternalOrExternalData(
            parentDir = extRoot,
            dirEntry = packageName,
            physicalPathForTest = "$extRoot/$packageName",
            destinationArchive = BackupInteropLayout.extDataPartForWrite(packageDir, encrypt),
            excludes = externalScopedBackupExcludes(packageName),
            encrypt = encrypt,
            password = password
        )
        val obb = tarInternalOrExternalData(
            parentDir = obbRoot,
            dirEntry = packageName,
            physicalPathForTest = "$obbRoot/$packageName",
            destinationArchive = BackupInteropLayout.obbPartForWrite(packageDir, encrypt),
            excludes = externalScopedBackupExcludes(packageName),
            encrypt = encrypt,
            password = password
        )
        val media = tarInternalOrExternalData(
            parentDir = mediaRoot,
            dirEntry = packageName,
            physicalPathForTest = "$mediaRoot/$packageName",
            destinationArchive = BackupInteropLayout.mediaPartForWrite(packageDir, encrypt),
            excludes = externalScopedBackupExcludes(packageName),
            encrypt = encrypt,
            password = password
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
            throw IllegalStateException(
                "CE backup failed (user.tar) for $packageName at $userCePath. See logcat tag $TAG."
            )
        }
        if (userDePath != null && isDirectory(userDePath) && !userDe) {
            throw IllegalStateException(
                "DE backup failed (user_de.tar) for $packageName at $userDePath. See logcat tag $TAG."
            )
        }

        Log.i(TAG, "$packageName ce=$userCePath parts apk=$apk user=$userCe userDe=$userDe ext=$extData obb=$obb media=$media")

        val configFile = configWriter.write(
            packageName = packageName,
            packageDir = packageDir,
            partFlags = flags,
            useOpensslTarEnc = encrypt
        )
        return RootBackupPlanResult(
            packageDir = packageDir,
            configFile = configFile,
            partFlags = flags
        )
    }

    private fun internalInteropExcludes(packageName: String): List<String> =
        listOf(
            "$packageName/.ota",
            "$packageName/cache",
            "$packageName/lib",
            "$packageName/code_cache",
            "$packageName/no_backup"
        )

    private fun externalScopedBackupExcludes(packageName: String): List<String> =
        listOf(
            "$packageName/cache",
            "$packageName/Backup_*"
        )

    private fun tarInternalOrExternalData(
        parentDir: String,
        dirEntry: String,
        physicalPathForTest: String,
        destinationArchive: File,
        excludes: List<String>,
        encrypt: Boolean,
        password: String?
    ): Boolean {
        val label = destinationArchive.name
        if (!isDirectory(physicalPathForTest)) {
            Log.w(TAG, "$label skip: not a dir $physicalPathForTest")
            return false
        }
        val stageRoot = File(context.cacheDir, "tb_stage").apply { mkdirs() }
        val staging = File(stageRoot, "dir_${UUID.randomUUID()}")
        val stagingPath = staging.absolutePath
        privilegedOperations.removeRecursive(stagingPath)
        staging.mkdirs()

        val populated = privilegedOperations.tarCopyPackageFiltered(
            parentDir = parentDir,
            packageDirEntry = dirEntry,
            destDir = stagingPath,
            excludes = excludes
        )
        if (!populated.isSuccess) {
            privilegedOperations.removeRecursive(stagingPath)
            Log.e(TAG, "$label stage failed $physicalPathForTest exit=${populated.exitCode} ${populated.output.take(300)}")
            return false
        }
        if (!chownTreeToApp(stagingPath)) {
            privilegedOperations.removeRecursive(stagingPath)
            Log.e(TAG, "$label chown failed ${appOwnerSpec()} $stagingPath")
            return false
        }

        val plainTar = File(context.cacheDir, "tb_part_${UUID.randomUUID()}.tar")
        return try {
            destinationArchive.parentFile?.mkdirs()
            if (!TarArchive.createFromDirectory(staging, plainTar)) {
                Log.e(TAG, "$label tar create failed")
                false
            } else if (encrypt) {
                val r = privilegedOperations.encryptArchive(
                    plainTar.absolutePath,
                    destinationArchive.absolutePath,
                    password!!
                )
                if (!r.isSuccess) {
                    Log.e(TAG, "$label openssl enc failed exit=${r.exitCode} ${r.output.take(400)}")
                    destinationArchive.delete()
                    false
                } else {
                    destinationArchive.isFile && destinationArchive.canRead()
                }
            } else {
                val ok = if (plainTar.renameTo(destinationArchive)) {
                    true
                } else {
                    plainTar.inputStream().use { ins ->
                        destinationArchive.outputStream().use { outs -> ins.copyTo(outs) }
                    }
                    plainTar.delete()
                    destinationArchive.isFile && destinationArchive.canRead()
                }
                ok
            }
        } catch (e: Exception) {
            Log.e(TAG, "$label archive failed", e)
            destinationArchive.delete()
            false
        } finally {
            if (plainTar.exists()) plainTar.delete()
            privilegedOperations.removeRecursive(stagingPath)
        }
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

    private fun tarApkIfInstalled(
        packageName: String,
        destinationArchive: File,
        encrypt: Boolean,
        password: String?
    ): Boolean {
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

        val plainTar = File(context.cacheDir, "tb_apk_${UUID.randomUUID()}.tar")
        return try {
            destinationArchive.parentFile?.mkdirs()
            if (!TarArchive.createFromDirectory(staging, plainTar)) {
                false
            } else if (encrypt) {
                val r = privilegedOperations.encryptArchive(
                    plainTar.absolutePath,
                    destinationArchive.absolutePath,
                    password!!
                )
                if (!r.isSuccess) {
                    destinationArchive.delete()
                    false
                } else {
                    destinationArchive.isFile && destinationArchive.canRead()
                }
            } else {
                if (plainTar.renameTo(destinationArchive)) {
                    true
                } else {
                    plainTar.inputStream().use { ins ->
                        destinationArchive.outputStream().use { outs -> ins.copyTo(outs) }
                    }
                    plainTar.delete()
                    destinationArchive.isFile && destinationArchive.canRead()
                }
            }
        } catch (_: Exception) {
            destinationArchive.delete()
            false
        } finally {
            if (plainTar.exists()) plainTar.delete()
            privilegedOperations.removeRecursive(stagingPath)
        }
    }

    private fun fixBackupOutputTreeOwnership(packageDir: File) {
        if (!chownTreeToApp(packageDir.absolutePath)) {
            throw IllegalStateException(
                "Could not chown backup tree to app uid: ${packageDir.absolutePath}"
            )
        }
    }

    private fun appOwnerSpec(): String {
        return try {
            val st = Os.stat(context.filesDir.absolutePath)
            "${st.st_uid}:${st.st_gid}"
        } catch (_: Throwable) {
            val u = context.applicationInfo.uid
            "$u:$u"
        }
    }

    private fun chownTreeToApp(path: String): Boolean {
        val own = appOwnerSpec()
        val cmd = "chown -R $own '${escape(path)}' && chmod -R u+rwX '${escape(path)}'"
        return privilegedOperations.runCustom(cmd).isSuccess
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
        private const val PER_USER_RANGE = 100_000

        private fun userIdFromUid(uid: Int): Int = uid / PER_USER_RANGE

        private fun inferPathUserId(dataDir: String?, fallbackShellUser: Int): Int {
            if (dataDir.isNullOrBlank()) return fallbackShellUser
            val m = Regex("""/data/(?:user|media)/(\d+)/""").find(dataDir)
            return m?.groupValues?.get(1)?.toIntOrNull() ?: fallbackShellUser
        }
    }
}
