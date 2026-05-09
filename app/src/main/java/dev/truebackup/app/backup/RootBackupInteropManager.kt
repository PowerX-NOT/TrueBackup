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
    val configFile: File
)

class RootBackupInteropManager(
    context: Context,
    private val privilegedOperations: PrivilegedOperations = PrivilegedOperations()
) {
    private val configWriter = PackageBackupConfigWriter(context)

    fun prepareEmptyBackupSkeleton(request: RootBackupRequest): RootBackupPlanResult {
        val packageDir = BackupInteropLayout.packageBackupDir(
            basePath = request.basePath,
            packageName = request.packageName
        )
        val mkdirResult = privilegedOperations.runCustom(
            "mkdir -p '${packageDir.absolutePath.replace("'", "'\"'\"'")}'/" +
                "{${BackupInteropLayout.DIR_APK},${BackupInteropLayout.DIR_INT_DATA}," +
                "${BackupInteropLayout.DIR_EXT_DATA},${BackupInteropLayout.DIR_ADDL_DATA}}"
        )
        if (!mkdirResult.isSuccess) {
            throw IllegalStateException("Could not create backup folders: ${mkdirResult.output}")
        }

        val configFile = configWriter.write(
            packageName = request.packageName,
            packageDir = packageDir,
            partFlags = BackupPartFlags(
                apk = false,
                userCe = false,
                userDe = false,
                extData = false,
                obb = false,
                media = false
            )
        )
        return RootBackupPlanResult(
            packageDir = packageDir,
            configFile = configFile
        )
    }
}
