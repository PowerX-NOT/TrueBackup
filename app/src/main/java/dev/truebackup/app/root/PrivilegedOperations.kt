package dev.truebackup.app.root

enum class PrivilegedOperationType {
    MIRROR_COPY,
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
     * Copy all contents of [sourceDir] into [destDir] (must exist or be created), then chmod so the app UID can read.
     * Used to stage privileged paths into app cache before [JvmZip.zipDirectory].
     */
    fun mirrorCopyDirectoryContents(sourceDir: String, destDir: String): PrivilegedOperationResult {
        val s = escapeSingleQuotes(sourceDir)
        val d = escapeSingleQuotes(destDir)
        val command = "mkdir -p '$d' && cp -a '$s'/'.' '$d'/ && chmod -R a+rX '$d'"
        return execute(PrivilegedOperationType.MIRROR_COPY, command)
    }

    /**
     * Same idea as Android-DataBackup [Tar.compress]: stream-copy one directory entry from [parentDir]
     * (e.g. `/data/user/0`) named [packageDirEntry] into [destDir] with tar --exclude patterns relative to
     * that archive member (e.g. `com.app/cache`). Extract strips the top directory so [destDir] matches
     * [mirrorCopyDirectoryContents] (package files at dest root). Uses system `tar` (no zip binary).
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

    /**
     * After [mirrorCopyDirectoryContents], paths under [stagingDir] are the package root (no package prefix).
     * [excludes] use tar-style names like `com.pkg/cache`; this removes the matching subtree under staging.
     */
    fun removeMirroredPackageExcludes(
        stagingDir: String,
        packageDirEntry: String,
        excludes: List<String>
    ): PrivilegedOperationResult {
        val d = escapeSingleQuotes(stagingDir)
        val prefix = "$packageDirEntry/"
        val segments = excludes.mapNotNull { pat ->
            if (!pat.startsWith(prefix)) return@mapNotNull null
            val rel = pat.removePrefix(prefix)
            rel.takeIf { it.isNotEmpty() }
        }.distinct()
        if (segments.isEmpty()) {
            return PrivilegedOperationResult(PrivilegedOperationType.CUSTOM, "true", 0, "")
        }
        val cmds = buildList {
            for (rel in segments) {
                when {
                    '*' in rel || '?' in rel -> {
                        if (!rel.matches(Regex("""^[A-Za-z0-9_.*?-]+$"""))) continue
                        add("cd '$d' && for f in $rel; do [ -e \"\$f\" ] && rm -rf \"\$f\"; done")
                    }
                    else -> {
                        val r = escapeSingleQuotes(rel)
                        add("rm -rf '$d'/'$r'")
                    }
                }
            }
        }
        if (cmds.isEmpty()) {
            return PrivilegedOperationResult(PrivilegedOperationType.CUSTOM, "true", 0, "")
        }
        return runCustom(cmds.joinToString(" && "))
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
