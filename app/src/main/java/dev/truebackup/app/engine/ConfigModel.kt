package dev.truebackup.app.engine

import org.json.JSONArray
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────────────────────
// Data classes mirroring package_restore_config.json (version = 2)
// Field names match TrueBackupService.writeConfig() exactly for interoperability
// ─────────────────────────────────────────────────────────────────────────────

data class BackupParts(
    val apk: Boolean = false,
    val userCe: Boolean = false,
    val userDe: Boolean = false,
    val extData: Boolean = false,
    val obb: Boolean = false,
    val media: Boolean = false
)

data class PackageInfoSnapshot(
    val label: String? = null,
    val packageName: String = "",
    val versionName: String? = null,
    val versionCode: Long = 0L,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L,
    val flags: Int = 0,
    val uid: Int = -1,
    val sourceDir: String? = null
)

data class BackupConfigSection(
    val packageName: String = "",
    val userId: Int = 0,
    val compression: String = "zip",
    val preserveId: Long = 0L,
    val storagePath: String? = null,
    val createdAt: Long = 0L
)

data class DataStats(
    val apkBytes: Long = 0L,
    val userBytes: Long = 0L,
    val userDeBytes: Long = 0L,
    val dataBytes: Long = 0L,
    val obbBytes: Long = 0L,
    val mediaBytes: Long = 0L
) {
    val totalBytes: Long get() = apkBytes + userBytes + userDeBytes + dataBytes + obbBytes + mediaBytes
}

data class PermissionEntry(val name: String, val granted: Boolean)
data class AppOpEntry(val op: Int, val mode: Int)

data class SecurityInfo(
    val uid: Int = -1,
    val ssaid: String? = null,
    val permissions: List<PermissionEntry> = emptyList(),
    val appops: List<AppOpEntry> = emptyList()
)

data class BackupMetadata(
    val version: Int = 2,
    val packageName: String = "",
    val parts: BackupParts = BackupParts(),
    val pkgInfo: PackageInfoSnapshot = PackageInfoSnapshot(),
    val backupConfig: BackupConfigSection = BackupConfigSection(),
    val dataStats: DataStats = DataStats(),
    val security: SecurityInfo = SecurityInfo()
)

// ─────────────────────────────────────────────────────────────────────────────
// ConfigWriter — serialize / deserialize BackupMetadata ↔ JSON
// ─────────────────────────────────────────────────────────────────────────────

object ConfigWriter {

    fun toJson(meta: BackupMetadata): String {
        val obj = JSONObject()
        obj.put("version", meta.version)
        obj.put("package", meta.packageName)

        // Top-level booleans (original format)
        obj.put("apk", meta.parts.apk)
        obj.put("user_ce", meta.parts.userCe)
        obj.put("user_de", meta.parts.userDe)
        obj.put("ext_data", meta.parts.extData)
        obj.put("obb", meta.parts.obb)
        obj.put("media", meta.parts.media)

        // packageInfo
        val pkgInfo = JSONObject()
        pkgInfo.put("label", meta.pkgInfo.label)
        pkgInfo.put("appLabel", meta.pkgInfo.label)
        pkgInfo.put("packageName", meta.pkgInfo.packageName)
        pkgInfo.put("versionName", meta.pkgInfo.versionName)
        pkgInfo.put("versionCode", meta.pkgInfo.versionCode)
        pkgInfo.put("firstInstallTime", meta.pkgInfo.firstInstallTime)
        pkgInfo.put("lastUpdateTime", meta.pkgInfo.lastUpdateTime)
        pkgInfo.put("flags", meta.pkgInfo.flags)
        pkgInfo.put("uid", meta.pkgInfo.uid)
        pkgInfo.put("sourceDir", meta.pkgInfo.sourceDir)
        obj.put("packageInfo", pkgInfo)

        // backupConfig
        val bc = JSONObject()
        bc.put("packageName", meta.backupConfig.packageName)
        bc.put("userId", meta.backupConfig.userId)
        bc.put("compression", meta.backupConfig.compression)
        bc.put("preserveId", meta.backupConfig.preserveId)
        bc.put("storagePath", meta.backupConfig.storagePath)
        bc.put("createdAt", meta.backupConfig.createdAt)
        obj.put("backupConfig", bc)

        // dataStates (original uses camelCase keys)
        val ds = JSONObject()
        ds.put("apk", meta.parts.apk)
        ds.put("userCe", meta.parts.userCe)
        ds.put("userDe", meta.parts.userDe)
        ds.put("externalData", meta.parts.extData)
        ds.put("obb", meta.parts.obb)
        ds.put("media", meta.parts.media)
        obj.put("dataStates", ds)

        // dataStats
        val stat = JSONObject()
        stat.put("apkBytes", meta.dataStats.apkBytes)
        stat.put("userBytes", meta.dataStats.userBytes)
        stat.put("userDeBytes", meta.dataStats.userDeBytes)
        stat.put("dataBytes", meta.dataStats.dataBytes)
        stat.put("obbBytes", meta.dataStats.obbBytes)
        stat.put("mediaBytes", meta.dataStats.mediaBytes)
        obj.put("dataStats", stat)

        // security
        val sec = JSONObject()
        sec.put("uid", meta.security.uid)
        if (meta.security.ssaid != null) sec.put("ssaid", meta.security.ssaid) else sec.put("ssaid", JSONObject.NULL)
        sec.put("keystore", "unknown")
        val perms = JSONArray()
        meta.security.permissions.forEach { p ->
            val po = JSONObject(); po.put("name", p.name); po.put("granted", p.granted); perms.put(po)
        }
        sec.put("permissions", perms)
        val ops = JSONArray()
        meta.security.appops.forEach { o ->
            val oo = JSONObject(); oo.put("op", o.op); oo.put("mode", o.mode); ops.put(oo)
        }
        sec.put("appops", ops)
        obj.put("security", sec)

        return obj.toString(2)
    }

    fun fromJson(json: String): BackupMetadata? = try {
        val obj = JSONObject(json)
        val ver = obj.optInt("version", 2)
        val pkg = obj.optString("package", "")

        val parts = BackupParts(
            apk = obj.optBoolean("apk"),
            userCe = obj.optBoolean("user_ce"),
            userDe = obj.optBoolean("user_de"),
            extData = obj.optBoolean("ext_data"),
            obb = obj.optBoolean("obb"),
            media = obj.optBoolean("media")
        )

        val piObj = obj.optJSONObject("packageInfo")
        val pkgInfo = if (piObj != null) PackageInfoSnapshot(
            label = piObj.optString("label").takeIf { it.isNotEmpty() }
                ?: piObj.optString("appLabel").takeIf { it.isNotEmpty() },
            packageName = piObj.optString("packageName", pkg),
            versionName = piObj.optString("versionName").takeIf { it.isNotEmpty() },
            versionCode = piObj.optLong("versionCode"),
            firstInstallTime = piObj.optLong("firstInstallTime"),
            lastUpdateTime = piObj.optLong("lastUpdateTime"),
            flags = piObj.optInt("flags"),
            uid = piObj.optInt("uid", -1),
            sourceDir = piObj.optString("sourceDir").takeIf { it.isNotEmpty() }
        ) else PackageInfoSnapshot(packageName = pkg)

        val bcObj = obj.optJSONObject("backupConfig")
        val bc = if (bcObj != null) BackupConfigSection(
            packageName = bcObj.optString("packageName", pkg),
            userId = bcObj.optInt("userId", 0),
            compression = bcObj.optString("compression", "zip"),
            preserveId = bcObj.optLong("preserveId"),
            storagePath = bcObj.optString("storagePath").takeIf { it.isNotEmpty() },
            createdAt = bcObj.optLong("createdAt")
        ) else BackupConfigSection(packageName = pkg)

        val statObj = obj.optJSONObject("dataStats")
        val stats = if (statObj != null) DataStats(
            apkBytes = statObj.optLong("apkBytes"),
            userBytes = statObj.optLong("userBytes"),
            userDeBytes = statObj.optLong("userDeBytes"),
            dataBytes = statObj.optLong("dataBytes"),
            obbBytes = statObj.optLong("obbBytes"),
            mediaBytes = statObj.optLong("mediaBytes")
        ) else DataStats()

        val secObj = obj.optJSONObject("security")
        val sec = if (secObj != null) {
            val permArr = secObj.optJSONArray("permissions")
            val perms = (0 until (permArr?.length() ?: 0)).mapNotNull { i ->
                val p = permArr?.optJSONObject(i) ?: return@mapNotNull null
                PermissionEntry(p.optString("name"), p.optBoolean("granted"))
            }
            val opsArr = secObj.optJSONArray("appops")
            val ops = (0 until (opsArr?.length() ?: 0)).mapNotNull { i ->
                val o = opsArr?.optJSONObject(i) ?: return@mapNotNull null
                AppOpEntry(o.optInt("op", -1), o.optInt("mode", 0))
            }.filter { it.op >= 0 }
            SecurityInfo(
                uid = secObj.optInt("uid", -1),
                ssaid = secObj.optString("ssaid").takeIf { it.isNotEmpty() && it != "null" },
                permissions = perms,
                appops = ops
            )
        } else SecurityInfo()

        BackupMetadata(ver, pkg, parts, pkgInfo, bc, stats, sec)
    } catch (e: Exception) {
        null
    }
}
