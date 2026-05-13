package dev.truebackup.app.root

/**
 * OpenSSL used for backup encrypt/decrypt/rekey under `su`.
 * Termux ships a usable `openssl`; system images often lack one in the root shell PATH.
 */
private const val OPENSSL_EXECUTABLE = "/data/data/com.termux/files/usr/bin/openssl"

enum class PrivilegedOperationType {
    TAR_COPY,
    CHOWN_RECURSIVE,
    RELABEL_SELINUX,
    INSTALL_APK,
    ENCRYPT_ARCHIVE,
    DECRYPT_ARCHIVE,
    CUSTOM
}

data class PrivilegedOperationResult(
    val type: PrivilegedOperationType,
    val command: String,
    val exitCode: Int,
    val output: String
) {
    val isSuccess: Boolean
        get() = exitCode == 0
}

class PrivilegedOperations(
    private val rootCommandExecutor: RootCommandExecutor = RootCommandExecutor()
) {
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
        return execute(PrivilegedOperationType.TAR_COPY, command)
    }

    fun removeRecursive(path: String): PrivilegedOperationResult {
        return runCustom("rm -rf '${escapeSingleQuotes(path)}'")
    }

    fun chownRecursive(ownerGroup: String, targetPath: String): PrivilegedOperationResult {
        val command = "chown -R ${escapeSingleQuotes(ownerGroup)} '${escapeSingleQuotes(targetPath)}'"
        return execute(PrivilegedOperationType.CHOWN_RECURSIVE, command)
    }

    fun relabelSelinuxRecursive(targetPath: String): PrivilegedOperationResult {
        val command = "restorecon -R '${escapeSingleQuotes(targetPath)}'"
        return execute(PrivilegedOperationType.RELABEL_SELINUX, command)
    }

    fun installApk(apkPath: String): PrivilegedOperationResult {
        val command = "pm install -r '${escapeSingleQuotes(apkPath)}'"
        return execute(PrivilegedOperationType.INSTALL_APK, command)
    }

    fun encryptArchive(inputPath: String, outputPath: String, passphrase: String): PrivilegedOperationResult {
        val command =
            "$OPENSSL_EXECUTABLE enc -aes-256-cbc -salt -pbkdf2 -in '${escapeSingleQuotes(inputPath)}' " +
                "-out '${escapeSingleQuotes(outputPath)}' -k '${escapeSingleQuotes(passphrase)}'"
        return execute(PrivilegedOperationType.ENCRYPT_ARCHIVE, command)
    }

    fun decryptArchive(inputPath: String, outputPath: String, passphrase: String): PrivilegedOperationResult {
        val command =
            "$OPENSSL_EXECUTABLE enc -d -aes-256-cbc -pbkdf2 -in '${escapeSingleQuotes(inputPath)}' " +
                "-out '${escapeSingleQuotes(outputPath)}' -k '${escapeSingleQuotes(passphrase)}'"
        return execute(PrivilegedOperationType.DECRYPT_ARCHIVE, command)
    }

    /**
     * Re-encrypt an OpenSSL `aes-256-cbc` + PBKDF2 archive in place (decrypt with [oldPassphrase], encrypt with [newPassphrase]).
     */
    fun rekeyOpensslEncInPlace(absPath: String, oldPassphrase: String, newPassphrase: String): PrivilegedOperationResult {
        val p = escapeSingleQuotes(absPath)
        val op = escapeSingleQuotes(oldPassphrase)
        val np = escapeSingleQuotes(newPassphrase)
        val tmp = escapeSingleQuotes("$absPath.rekey_tmp")
        val command =
            "rm -f '$tmp' && $OPENSSL_EXECUTABLE enc -d -aes-256-cbc -pbkdf2 -in '$p' -k '$op' | " +
                "$OPENSSL_EXECUTABLE enc -aes-256-cbc -salt -pbkdf2 -out '$tmp' -k '$np' && mv '$tmp' '$p'"
        return execute(PrivilegedOperationType.CUSTOM, command)
    }

    /** Returns success if OpenSSL can decrypt [inputPath] with [passphrase] (output discarded). */
    fun opensslDecryptProbe(inputPath: String, passphrase: String): PrivilegedOperationResult {
        val command =
            "$OPENSSL_EXECUTABLE enc -d -aes-256-cbc -pbkdf2 -in '${escapeSingleQuotes(inputPath)}' " +
                "-k '${escapeSingleQuotes(passphrase)}' -out /dev/null"
        return execute(PrivilegedOperationType.CUSTOM, command)
    }

    fun runCustom(command: String): PrivilegedOperationResult {
        return execute(PrivilegedOperationType.CUSTOM, command)
    }

    private fun execute(type: PrivilegedOperationType, command: String): PrivilegedOperationResult {
        val result = rootCommandExecutor.run(command)
        return PrivilegedOperationResult(
            type = type,
            command = command,
            exitCode = result.exitCode,
            output = result.output
        )
    }

    private fun escapeSingleQuotes(raw: String): String {
        return raw.replace("'", "'\"'\"'")
    }
}
