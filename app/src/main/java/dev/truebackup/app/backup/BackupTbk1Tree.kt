package dev.truebackup.app.backup

import java.io.File

/**
 * Scans the interop backup tree under a user-chosen base path for TBK1 archives and validates / rekeys them,
 * mirroring truebackupd `HasEncryptedBackupZip` / `CanDecryptAnyKnownEncryptedBackup` / `RekeyBackupTree` behavior.
 */
object BackupTbk1Tree {

    private fun appsRoot(backupBasePath: String): File {
        val trimmed = backupBasePath.trim().trimEnd('/')
        return File(File(trimmed, BackupInteropLayout.DIR_BACKUP), BackupInteropLayout.DIR_APPS)
    }

    /** Every TBK1 `.zip` under `backupBasePath/backup/apps/`. */
    fun collectTbk1Zips(backupBasePath: String): List<File> {
        val root = appsRoot(backupBasePath)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".zip", ignoreCase = true) && Tbk1Codec.isTbk1(it) }
            .toList()
    }

    fun hasTbk1Archives(backupBasePath: String?): Boolean {
        if (backupBasePath.isNullOrBlank()) return false
        return collectTbk1Zips(backupBasePath).isNotEmpty()
    }

    /**
     * If there are no TBK1 files, returns true. Otherwise tries to decrypt at least one with [password]
     * (same idea as truebackupd `CanDecryptAnyKnownEncryptedBackup`).
     */
    fun canDecryptAnyTbk1(backupBasePath: String?, password: String, workDir: File): Boolean {
        if (backupBasePath.isNullOrBlank()) return true
        val zips = collectTbk1Zips(backupBasePath)
        if (zips.isEmpty()) return true
        for (z in zips) {
            val probe = File.createTempFile("tbk_probe_", ".zip", workDir)
            try {
                if (Tbk1Codec.decryptToFile(z, probe, password)) return true
            } finally {
                probe.delete()
            }
        }
        return false
    }

    /**
     * Re-encrypts every TBK1 zip under the backup tree from [oldPassword] to [newPassword].
     * Returns false if any file fails.
     */
    fun rekeyAllTbk1(backupBasePath: String?, oldPassword: String, newPassword: String, workDir: File): Boolean {
        if (backupBasePath.isNullOrBlank()) return true
        val zips = collectTbk1Zips(backupBasePath)
        for (z in zips) {
            if (!rekeySingleTbk1Zip(z, oldPassword, newPassword, workDir)) return false
        }
        return true
    }

    /** Re-encrypt one TBK1 archive in place; returns false on any failure. */
    fun rekeySingleTbk1Zip(zip: File, oldPassword: String, newPassword: String, workDir: File): Boolean =
        rekeySingleZip(zip, oldPassword, newPassword, workDir)

    private fun rekeySingleZip(zip: File, oldPassword: String, newPassword: String, workDir: File): Boolean {
        val tmpPlain = File.createTempFile("tbk_rekey_plain_", ".zip", workDir)
        return try {
            if (!Tbk1Codec.decryptToFile(zip, tmpPlain, oldPassword)) return false
            if (!zip.delete()) return false
            val moved = tmpPlain.renameTo(zip)
            if (!moved) {
                if (!copyReplace(tmpPlain, zip)) return false
                tmpPlain.delete()
            }
            Tbk1Codec.encryptInPlace(zip, newPassword)
        } finally {
            if (tmpPlain.exists()) tmpPlain.delete()
        }
    }

    private fun copyReplace(src: File, dst: File): Boolean {
        return runCatching {
            dst.parentFile?.mkdirs()
            src.inputStream().use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
            true
        }.getOrDefault(false)
    }
}
