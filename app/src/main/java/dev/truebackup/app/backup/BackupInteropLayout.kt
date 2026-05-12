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

    const val ZIP_APK = "apk.zip"
    const val ZIP_USER = "user.zip"
    const val ZIP_USER_DE = "user_de.zip"
    const val ZIP_DATA = "data.zip"
    const val ZIP_OBB = "obb.zip"
    const val ZIP_MEDIA = "media.zip"

    fun packageBackupDir(basePath: String, packageName: String): File {
        return File(File(File(basePath, DIR_BACKUP), DIR_APPS), packageName)
    }

    fun configFile(packageDir: File): File = File(packageDir, FILE_CONFIG)
    fun apkZip(packageDir: File): File = File(File(packageDir, DIR_APK), ZIP_APK)
    fun userCeZip(packageDir: File): File = File(File(packageDir, DIR_INT_DATA), ZIP_USER)
    fun userDeZip(packageDir: File): File = File(File(packageDir, DIR_INT_DATA), ZIP_USER_DE)
    fun extDataZip(packageDir: File): File = File(File(packageDir, DIR_EXT_DATA), ZIP_DATA)
    fun obbZip(packageDir: File): File = File(File(packageDir, DIR_ADDL_DATA), ZIP_OBB)
    fun mediaZip(packageDir: File): File = File(File(packageDir, DIR_ADDL_DATA), ZIP_MEDIA)

    /**
     * Returns `.../backup/apps/<package>/` for a zip under the interop tree, or null if [zip] is not under [backupBasePath]/backup/apps/.
     */
    fun packageDirContainingZip(zip: File, backupBasePath: String): File? {
        val base = File(backupBasePath.trim().trimEnd('/')).absoluteFile
        val appsRoot = File(File(base, DIR_BACKUP), DIR_APPS).absoluteFile
        var child: File = zip.absoluteFile.parentFile ?: return null
        while (true) {
            val parent = child.parentFile ?: return null
            if (parent.absolutePath == appsRoot.absolutePath) return child
            child = parent
        }
    }
}
