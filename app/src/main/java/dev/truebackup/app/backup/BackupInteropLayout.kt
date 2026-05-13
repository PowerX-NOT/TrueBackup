package dev.truebackup.app.backup

import java.io.File

object BackupInteropLayout {
    const val DIR_BACKUP = "backup"
    const val DIR_APPS = "apps"
    const val FILE_CONFIG = "package_restore_config.json"

    const val DIR_APK = "apk"
    const val DIR_INT_DATA = "int_data"
    const val DIR_EXT_DATA = "ext_data"
    const val DIR_ADDL_DATA = "addl_data"

    const val TAR_APK_ENC = "apk.tar.enc"
    const val TAR_APK = "apk.tar"
    const val TAR_USER_ENC = "user.tar.enc"
    const val TAR_USER = "user.tar"
    const val TAR_USER_DE_ENC = "user_de.tar.enc"
    const val TAR_USER_DE = "user_de.tar"
    const val TAR_DATA_ENC = "data.tar.enc"
    const val TAR_DATA = "data.tar"
    const val TAR_OBB_ENC = "obb.tar.enc"
    const val TAR_OBB = "obb.tar"
    const val TAR_MEDIA_ENC = "media.tar.enc"
    const val TAR_MEDIA = "media.tar"

    fun packageBackupDir(basePath: String, packageName: String): File {
        return File(File(File(basePath, DIR_BACKUP), DIR_APPS), packageName)
    }

    fun configFile(packageDir: File): File = File(packageDir, FILE_CONFIG)

    fun apkPartForWrite(packageDir: File, encrypted: Boolean): File =
        File(File(packageDir, DIR_APK), if (encrypted) TAR_APK_ENC else TAR_APK)

    fun userCePartForWrite(packageDir: File, encrypted: Boolean): File =
        File(File(packageDir, DIR_INT_DATA), if (encrypted) TAR_USER_ENC else TAR_USER)

    fun userDePartForWrite(packageDir: File, encrypted: Boolean): File =
        File(File(packageDir, DIR_INT_DATA), if (encrypted) TAR_USER_DE_ENC else TAR_USER_DE)

    fun extDataPartForWrite(packageDir: File, encrypted: Boolean): File =
        File(File(packageDir, DIR_EXT_DATA), if (encrypted) TAR_DATA_ENC else TAR_DATA)

    fun obbPartForWrite(packageDir: File, encrypted: Boolean): File =
        File(File(packageDir, DIR_ADDL_DATA), if (encrypted) TAR_OBB_ENC else TAR_OBB)

    fun mediaPartForWrite(packageDir: File, encrypted: Boolean): File =
        File(File(packageDir, DIR_ADDL_DATA), if (encrypted) TAR_MEDIA_ENC else TAR_MEDIA)

    /** Prefer encrypted `.tar.enc`, then plain `.tar`. */
    fun resolveApkPart(packageDir: File): File? =
        listOf(
            File(File(packageDir, DIR_APK), TAR_APK_ENC),
            File(File(packageDir, DIR_APK), TAR_APK),
        ).firstOrNull { it.isFile }

    fun resolveUserCePart(packageDir: File): File? =
        listOf(
            File(File(packageDir, DIR_INT_DATA), TAR_USER_ENC),
            File(File(packageDir, DIR_INT_DATA), TAR_USER),
        ).firstOrNull { it.isFile }

    fun resolveUserDePart(packageDir: File): File? =
        listOf(
            File(File(packageDir, DIR_INT_DATA), TAR_USER_DE_ENC),
            File(File(packageDir, DIR_INT_DATA), TAR_USER_DE),
        ).firstOrNull { it.isFile }

    fun resolveExtDataPart(packageDir: File): File? =
        listOf(
            File(File(packageDir, DIR_EXT_DATA), TAR_DATA_ENC),
            File(File(packageDir, DIR_EXT_DATA), TAR_DATA),
        ).firstOrNull { it.isFile }

    fun resolveObbPart(packageDir: File): File? =
        listOf(
            File(File(packageDir, DIR_ADDL_DATA), TAR_OBB_ENC),
            File(File(packageDir, DIR_ADDL_DATA), TAR_OBB),
        ).firstOrNull { it.isFile }

    fun resolveMediaPart(packageDir: File): File? =
        listOf(
            File(File(packageDir, DIR_ADDL_DATA), TAR_MEDIA_ENC),
            File(File(packageDir, DIR_ADDL_DATA), TAR_MEDIA),
        ).firstOrNull { it.isFile }

    fun partFileSizeOrZero(f: File?): Long = if (f != null && f.isFile) f.length() else 0L

    /**
     * Returns `.../backup/apps/<package>/` for an archive file under the interop tree,
     * or null if [archive] is not under [backupBasePath]/backup/apps/.
     */
    fun packageDirContainingArchive(archive: File, backupBasePath: String): File? {
        val base = File(backupBasePath.trim().trimEnd('/')).absoluteFile
        val appsRoot = File(File(base, DIR_BACKUP), DIR_APPS).absoluteFile
        var child: File = archive.absoluteFile.parentFile ?: return null
        while (true) {
            val parent = child.parentFile ?: return null
            if (parent.absolutePath == appsRoot.absolutePath) return child
            child = parent
        }
    }
}
