package dev.truebackup.app.backup

import dev.truebackup.app.root.PrivilegedOperations
import java.io.File

/**
 * Scans the interop backup tree for **encrypted** archives: OpenSSL `.tar.enc` (this app) and legacy **TBK1** `.zip`
 * (ROM truebackupd). Validates passwords and rekeys where supported.
 */
object BackupTbk1Tree {

    private fun appsRoot(backupBasePath: String): File {
        val trimmed = backupBasePath.trim().trimEnd('/')
        return File(File(trimmed, BackupInteropLayout.DIR_BACKUP), BackupInteropLayout.DIR_APPS)
    }

    /** Legacy TBK1 `.zip` under `backupBasePath/backup/apps/`. */
    fun collectTbk1Zips(backupBasePath: String): List<File> {
        val root = appsRoot(backupBasePath)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".zip", ignoreCase = true) && Tbk1Codec.isTbk1(it) }
            .toList()
    }

    /** OpenSSL-encrypted tar (`.tar.enc`) written by this app. */
    fun collectOpenSslTarEncArchives(backupBasePath: String): List<File> {
        val root = appsRoot(backupBasePath)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".tar.enc", ignoreCase = true) }
            .toList()
    }

    fun collectAllRekeyableArchives(backupBasePath: String): List<File> =
        collectOpenSslTarEncArchives(backupBasePath) + collectTbk1Zips(backupBasePath)

    fun hasAnyEncryptedArchives(backupBasePath: String?): Boolean {
        if (backupBasePath.isNullOrBlank()) return false
        return collectTbk1Zips(backupBasePath).isNotEmpty() ||
            collectOpenSslTarEncArchives(backupBasePath).isNotEmpty()
    }

    @Deprecated("Use hasAnyEncryptedArchives", ReplaceWith("hasAnyEncryptedArchives(backupBasePath)"))
    fun hasTbk1Archives(backupBasePath: String?): Boolean = hasAnyEncryptedArchives(backupBasePath)

    /**
     * If there are no encrypted archives, returns true. Otherwise tries TBK1 decrypt or OpenSSL decrypt probe
     * with [password].
     */
    fun canDecryptAnyEncrypted(
        backupBasePath: String?,
        password: String,
        workDir: File,
        privilegedOperations: PrivilegedOperations = PrivilegedOperations()
    ): Boolean {
        if (backupBasePath.isNullOrBlank()) return true
        val tbk = collectTbk1Zips(backupBasePath)
        val oss = collectOpenSslTarEncArchives(backupBasePath)
        if (tbk.isEmpty() && oss.isEmpty()) return true
        for (z in tbk) {
            val probe = File.createTempFile("tbk_probe_", ".zip", workDir)
            try {
                if (Tbk1Codec.decryptToFile(z, probe, password)) return true
            } finally {
                probe.delete()
            }
        }
        for (f in oss) {
            if (privilegedOperations.opensslDecryptProbe(f.absolutePath, password).isSuccess) {
                return true
            }
        }
        return false
    }

    @Deprecated("Use canDecryptAnyEncrypted", ReplaceWith("canDecryptAnyEncrypted(backupBasePath, password, workDir)"))
    fun canDecryptAnyTbk1(backupBasePath: String?, password: String, workDir: File): Boolean =
        canDecryptAnyEncrypted(backupBasePath, password, workDir)

    /**
     * Re-encrypts every OpenSSL `.tar.enc` and every TBK1 `.zip` under the backup tree.
     * Returns false if any file fails.
     */
    fun rekeyAllEncrypted(
        backupBasePath: String?,
        oldPassword: String,
        newPassword: String,
        workDir: File,
        privilegedOperations: PrivilegedOperations = PrivilegedOperations()
    ): Boolean {
        if (backupBasePath.isNullOrBlank()) return true
        for (f in collectOpenSslTarEncArchives(backupBasePath)) {
            if (!rekeySingleOpenSslTarEnc(f, oldPassword, newPassword, privilegedOperations)) return false
        }
        for (z in collectTbk1Zips(backupBasePath)) {
            if (!rekeySingleTbk1Zip(z, oldPassword, newPassword, workDir)) return false
        }
        return true
    }

    @Deprecated("Use rekeyAllEncrypted", ReplaceWith("rekeyAllEncrypted(backupBasePath, oldPassword, newPassword, workDir)"))
    fun rekeyAllTbk1(backupBasePath: String?, oldPassword: String, newPassword: String, workDir: File): Boolean =
        rekeyAllEncrypted(backupBasePath, oldPassword, newPassword, workDir)

    fun rekeySingleOpenSslTarEnc(
        file: File,
        oldPassword: String,
        newPassword: String,
        privilegedOperations: PrivilegedOperations = PrivilegedOperations()
    ): Boolean {
        val r = privilegedOperations.rekeyOpensslEncInPlace(file.absolutePath, oldPassword, newPassword)
        return r.isSuccess
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
