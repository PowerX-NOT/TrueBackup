package dev.truebackup.app.backup

import dev.truebackup.app.root.PrivilegedOperations
import java.io.File

/**
 * Scans the interop backup tree for OpenSSL-encrypted **`.tar.enc`** archives (this app) and
 * validates / rekeys them with the registration password.
 */
object BackupOpenSslTarEncTree {

    private fun appsRoot(backupBasePath: String): File {
        val trimmed = backupBasePath.trim().trimEnd('/')
        return File(File(trimmed, BackupInteropLayout.DIR_BACKUP), BackupInteropLayout.DIR_APPS)
    }

    fun collectOpenSslTarEncArchives(backupBasePath: String): List<File> {
        val root = appsRoot(backupBasePath)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".tar.enc", ignoreCase = true) }
            .toList()
    }

    fun hasAnyEncryptedArchives(backupBasePath: String?): Boolean {
        if (backupBasePath.isNullOrBlank()) return false
        return collectOpenSslTarEncArchives(backupBasePath).isNotEmpty()
    }

    /**
     * If there are no `.tar.enc` files, returns true. Otherwise tries [opensslDecryptProbe] with [password].
     */
    fun canDecryptAnyEncrypted(
        backupBasePath: String?,
        password: String,
        @Suppress("UNUSED_PARAMETER") workDir: File,
        privilegedOperations: PrivilegedOperations = PrivilegedOperations()
    ): Boolean {
        if (backupBasePath.isNullOrBlank()) return true
        val files = collectOpenSslTarEncArchives(backupBasePath)
        if (files.isEmpty()) return true
        for (f in files) {
            if (privilegedOperations.opensslDecryptProbe(f.absolutePath, password).isSuccess) {
                return true
            }
        }
        return false
    }

    /**
     * Re-encrypts every `.tar.enc` under the backup tree. Returns false if any file fails.
     */
    fun rekeyAllEncrypted(
        backupBasePath: String?,
        oldPassword: String,
        newPassword: String,
        @Suppress("UNUSED_PARAMETER") workDir: File,
        privilegedOperations: PrivilegedOperations = PrivilegedOperations()
    ): Boolean {
        if (backupBasePath.isNullOrBlank()) return true
        for (f in collectOpenSslTarEncArchives(backupBasePath)) {
            if (!rekeySingleOpenSslTarEnc(f, oldPassword, newPassword, privilegedOperations)) return false
        }
        return true
    }

    fun rekeySingleOpenSslTarEnc(
        file: File,
        oldPassword: String,
        newPassword: String,
        privilegedOperations: PrivilegedOperations = PrivilegedOperations()
    ): Boolean {
        val r = privilegedOperations.rekeyOpensslEncInPlace(file.absolutePath, oldPassword, newPassword)
        return r.isSuccess
    }
}
