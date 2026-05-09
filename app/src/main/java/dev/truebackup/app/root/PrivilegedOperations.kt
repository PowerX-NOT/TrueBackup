package dev.truebackup.app.root

enum class PrivilegedOperationType {
    ZIP,
    UNZIP,
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
    fun zipDirectory(sourceDir: String, destinationZip: String): PrivilegedOperationResult {
        val command = "cd '${escapeSingleQuotes(sourceDir)}' && zip -r '${escapeSingleQuotes(destinationZip)}' ."
        return execute(PrivilegedOperationType.ZIP, command)
    }

    fun unzipArchive(archivePath: String, destinationDir: String): PrivilegedOperationResult {
        val command = "unzip -o '${escapeSingleQuotes(archivePath)}' -d '${escapeSingleQuotes(destinationDir)}'"
        return execute(PrivilegedOperationType.UNZIP, command)
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
            "openssl enc -aes-256-cbc -salt -pbkdf2 -in '${escapeSingleQuotes(inputPath)}' " +
                "-out '${escapeSingleQuotes(outputPath)}' -k '${escapeSingleQuotes(passphrase)}'"
        return execute(PrivilegedOperationType.ENCRYPT_ARCHIVE, command)
    }

    fun decryptArchive(inputPath: String, outputPath: String, passphrase: String): PrivilegedOperationResult {
        val command =
            "openssl enc -d -aes-256-cbc -pbkdf2 -in '${escapeSingleQuotes(inputPath)}' " +
                "-out '${escapeSingleQuotes(outputPath)}' -k '${escapeSingleQuotes(passphrase)}'"
        return execute(PrivilegedOperationType.DECRYPT_ARCHIVE, command)
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
