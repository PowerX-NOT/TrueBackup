package dev.truebackup.app.root

import dev.truebackup.app.crypto.OpenSslEncCompat

data class PrivilegedOperationResult(
    val command: String,
    val exitCode: Int,
    val output: String
) {
    val isSuccess: Boolean
        get() = exitCode == 0
}

class PrivilegedOperations {
    /**
     * Stream-copy [packageDirEntry] from under [parentDir] into [destDir] with tar --exclude (before extract).
     * Extract uses --strip-components=1 so [destDir] has package files at the root (same shape as zipping from CE dir).
     */
    fun tarCopyPackageFiltered(
        parentDir: String,
        packageDirEntry: String,
        destDir: String,
        excludes: List<String>
    ): PrivilegedOperationResult {
        val p = escapeSingleQuotes(parentDir)
        val pkg = escapeSingleQuotes(packageDirEntry)
        val d = escapeSingleQuotes(destDir)
        val exClause = excludes.joinToString(" ") { pat ->
            val escaped = escapeSingleQuotes(pat)
            "--exclude='$escaped'"
        }
        val command =
            "mkdir -p '$d' && tar -cf - $exClause -C '$p' '$pkg' | tar -xf - --strip-components=1 -C '$d' && chmod -R a+rX '$d'"
        return execute(command)
    }

    fun removeRecursive(path: String): PrivilegedOperationResult {
        return runCustom("rm -rf '${escapeSingleQuotes(path)}'")
    }

    fun relabelSelinuxRecursive(targetPath: String): PrivilegedOperationResult {
        val command = "restorecon -R '${escapeSingleQuotes(targetPath)}'"
        return execute(command)
    }

    fun installApk(apkPath: String): PrivilegedOperationResult {
        val command = "pm install -r '${escapeSingleQuotes(apkPath)}'"
        return execute(command)
    }

    fun encryptArchive(inputPath: String, outputPath: String, passphrase: String): PrivilegedOperationResult {
        val command = "internal:OpenSslEncCompat.encryptFile"
        val ok = OpenSslEncCompat.encryptFile(inputPath, outputPath, passphrase)
        return PrivilegedOperationResult(
            command = command,
            exitCode = if (ok) 0 else 1,
            output = if (ok) "" else "OpenSslEncCompat.encryptFile failed"
        )
    }

    fun decryptArchive(inputPath: String, outputPath: String, passphrase: String): PrivilegedOperationResult {
        val command = "internal:OpenSslEncCompat.decryptFile"
        val ok = OpenSslEncCompat.decryptFile(inputPath, outputPath, passphrase)
        return PrivilegedOperationResult(
            command = command,
            exitCode = if (ok) 0 else 1,
            output = if (ok) "" else "OpenSslEncCompat.decryptFile failed"
        )
    }

    /**
     * Re-encrypt an OpenSSL `aes-256-cbc` + PBKDF2 archive in place (decrypt with [oldPassphrase], encrypt with [newPassphrase]).
     */
    fun rekeyOpensslEncInPlace(absPath: String, oldPassphrase: String, newPassphrase: String): PrivilegedOperationResult {
        val command = "internal:OpenSslEncCompat.rekeyFileInPlace"
        val ok = OpenSslEncCompat.rekeyFileInPlace(absPath, oldPassphrase, newPassphrase)
        return PrivilegedOperationResult(
            command = command,
            exitCode = if (ok) 0 else 1,
            output = if (ok) "" else "OpenSslEncCompat.rekeyFileInPlace failed"
        )
    }

    /** Returns success if the archive decrypts with [passphrase] (output discarded). */
    fun opensslDecryptProbe(inputPath: String, passphrase: String): PrivilegedOperationResult {
        val command = "internal:OpenSslEncCompat.decryptProbe"
        val ok = OpenSslEncCompat.decryptProbe(inputPath, passphrase)
        return PrivilegedOperationResult(
            command = command,
            exitCode = if (ok) 0 else 1,
            output = if (ok) "" else "OpenSslEncCompat.decryptProbe failed"
        )
    }

    fun runCustom(command: String): PrivilegedOperationResult {
        return execute(command)
    }

    private fun execute(command: String): PrivilegedOperationResult {
        val result = RootShellClient.execute(command)
        return PrivilegedOperationResult(
            command = command,
            exitCode = result.exitCode,
            output = result.output
        )
    }

    private fun escapeSingleQuotes(raw: String): String {
        return raw.replace("'", "'\"'\"'")
    }
}
