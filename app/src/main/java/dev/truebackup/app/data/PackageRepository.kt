package dev.truebackup.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val label: String,
    val uid: Int,
    val sourceDir: String,
    val splitSourceDirs: Array<String>?,
    val versionName: String?,
    val versionCode: Long,
    val firstInstallTime: Long,
    val flags: Int,
    val isSystemApp: Boolean
) {
    fun getIcon(context: Context): Drawable? = try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (_: Exception) { null }
}

class PackageRepository(private val context: Context) {

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .mapNotNull { pi ->
                val ai = pi.applicationInfo ?: return@mapNotNull null
                AppInfo(
                    packageName = pi.packageName,
                    label = pm.getApplicationLabel(ai).toString(),
                    uid = ai.uid,
                    sourceDir = ai.sourceDir ?: return@mapNotNull null,
                    splitSourceDirs = ai.splitSourceDirs,
                    versionName = pi.versionName,
                    versionCode = pi.longVersionCode,
                    firstInstallTime = pi.firstInstallTime,
                    flags = ai.flags,
                    isSystemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun getAppInfo(packageName: String): AppInfo? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        try {
            val pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val ai = pi.applicationInfo ?: return@withContext null
            AppInfo(
                packageName = pi.packageName,
                label = pm.getApplicationLabel(ai).toString(),
                uid = ai.uid,
                sourceDir = ai.sourceDir ?: return@withContext null,
                splitSourceDirs = ai.splitSourceDirs,
                versionName = pi.versionName,
                versionCode = pi.longVersionCode,
                firstInstallTime = pi.firstInstallTime,
                flags = ai.flags,
                isSystemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        } catch (_: PackageManager.NameNotFoundException) { null }
    }
}
