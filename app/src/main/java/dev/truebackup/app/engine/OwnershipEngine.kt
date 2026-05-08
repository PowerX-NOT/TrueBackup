package dev.truebackup.app.engine

import android.util.Log

/**
 * Ownership & SELinux fixup engine — replaces truebackupd CHOWN_AND_CHCON via root shell.
 * Uses restorecon -RF which is the standard rooted-device equivalent of SELinux relabeling.
 */
object OwnershipEngine {

    private const val TAG = "OwnershipEngine"

    /**
     * Recursively chown [path] to [uid]:[gid], chmod to [mode] (e.g. "0760"), then restorecon.
     * Mirrors TrueBackupService.fixupRestoredTree().
     */
    suspend fun fixupRestoredTree(path: String, uid: Int, gid: Int, mode: String? = null): Boolean {
        var ok = true
        val chown = RootShell.exec("chown -R $uid:$gid ${RootShell.quote(path)} 2>&1")
        if (!chown.success) {
            Log.w(TAG, "chown failed for $path: ${chown.stderr.take(200)}")
            ok = false
        }
        if (mode != null) {
            val chmod = RootShell.exec("chmod -R $mode ${RootShell.quote(path)} 2>&1")
            if (!chmod.success) {
                Log.w(TAG, "chmod failed for $path: ${chmod.stderr.take(200)}")
                ok = false
            }
        }
        val restorecon = RootShell.exec("restorecon -RF ${RootShell.quote(path)} 2>&1")
        if (!restorecon.success) {
            // restorecon may not exist on all devices; non-fatal
            Log.w(TAG, "restorecon skipped/failed for $path: ${restorecon.stderr.take(100)}")
        }
        return ok
    }

    /** Recursively chown only. */
    suspend fun chownRecursive(path: String, uid: Int, gid: Int): Boolean {
        val r = RootShell.exec("chown -R $uid:$gid ${RootShell.quote(path)} 2>&1")
        if (!r.success) Log.w(TAG, "chownRecursive failed for $path: ${r.stderr.take(200)}")
        return r.success
    }

    /** Recursively chmod only. */
    suspend fun chmodRecursive(path: String, mode: String): Boolean {
        val r = RootShell.exec("chmod -R $mode ${RootShell.quote(path)} 2>&1")
        if (!r.success) Log.w(TAG, "chmodRecursive failed for $path: ${r.stderr.take(200)}")
        return r.success
    }

    /** Run restorecon -RF on [path]. Non-fatal if restorecon is missing. */
    suspend fun restoreconRecursive(path: String): Boolean {
        val r = RootShell.exec("restorecon -RF ${RootShell.quote(path)} 2>&1")
        return r.success
    }

    /** Recursively delete a directory tree. Safety check: path must contain "/apps/". */
    suspend fun deleteTree(path: String): Boolean {
        if (!path.startsWith("/") || path == "/" || path == "/data" || path == "/system") {
            Log.e(TAG, "deleteTree: refusing dangerous path: $path"); return false
        }
        if (!path.contains("/apps/")) {
            Log.e(TAG, "deleteTree: path must contain /apps/: $path"); return false
        }
        val r = RootShell.exec("rm -rf ${RootShell.quote(path)} 2>&1")
        if (!r.success) Log.w(TAG, "deleteTree failed for $path: ${r.stderr.take(200)}")
        return r.success
    }

    /** Create directories recursively as root. */
    suspend fun mkdirs(path: String): Boolean {
        val r = RootShell.exec("mkdir -p ${RootShell.quote(path)} 2>&1")
        return r.success
    }

    /** Write [data] to [path] as root using tee. */
    suspend fun writeFile(path: String, data: ByteArray): Boolean {
        // Write to temp file in app's cache, then cp as root
        val tmp = java.io.File.createTempFile("tbk_write_", ".bin")
        return try {
            tmp.writeBytes(data)
            val r = RootShell.exec("cp ${RootShell.quote(tmp.absolutePath)} ${RootShell.quote(path)} 2>&1")
            r.success
        } catch (e: Exception) {
            Log.e(TAG, "writeFile failed for $path", e)
            false
        } finally {
            tmp.delete()
        }
    }
}
