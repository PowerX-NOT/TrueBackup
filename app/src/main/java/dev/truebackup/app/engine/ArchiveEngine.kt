package dev.truebackup.app.engine

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Archive engine — ZIP / UNZIP operations via root shell.
 *
 * Strategy for zipping:
 *   1. Try `su -c "zip -r <out> ."` (inside source dir)
 *   2. Try `su -c "busybox zip -r <out> ."`
 *   3. Fallback: Java ZipOutputStream with root-piped file reads
 *
 * Strategy for unzipping:
 *   1. Try `su -c "unzip -o <zip> -d <dir>"`
 *   2. Try `su -c "busybox unzip -o <zip> -d <dir>"`
 *   3. Fallback: Java ZipInputStream + `su tee` per entry
 */
object ArchiveEngine {

    private const val TAG = "ArchiveEngine"

    // -------------------------------------------------------------------------
    // ZIP
    // -------------------------------------------------------------------------

    /**
     * Recursively zips [sourceDir] into [outZip].
     * [outZip]'s parent directory must already exist (created by caller via root).
     * Returns true on success.
     */
    suspend fun zipDir(sourceDir: File, outZip: File): Boolean {
        val src = sourceDir.absolutePath
        val dst = outZip.absolutePath

        // Strategy 1 — native zip
        val r1 = RootShell.exec(
            "cd ${RootShell.quote(src)} && zip -r ${RootShell.quote(dst)} . 2>&1"
        )
        if (r1.success) {
            Log.d(TAG, "zipDir: native zip succeeded for $src")
            return true
        }
        Log.w(TAG, "zipDir: native zip failed (${r1.exitCode}): ${r1.stderr.take(200)}")

        // Strategy 2 — busybox zip
        val r2 = RootShell.exec(
            "cd ${RootShell.quote(src)} && busybox zip -r ${RootShell.quote(dst)} . 2>&1"
        )
        if (r2.success) {
            Log.d(TAG, "zipDir: busybox zip succeeded for $src")
            return true
        }
        Log.w(TAG, "zipDir: busybox zip failed (${r2.exitCode}): ${r2.stderr.take(200)}")

        // Strategy 3 — copy dir to temp, then Java ZipOutputStream
        return javaZipFallback(sourceDir, outZip)
    }

    /**
     * Zips multiple individual [files] (pairs of <absolutePath, entryName>) into [outZip].
     * Used for APK backup (base.apk + splits).
     */
    suspend fun zipMultiFile(files: List<Pair<File, String>>, outZip: File): Boolean {
        if (files.isEmpty()) return false
        // Build a temp dir with symlinks / copies, then zip
        // Simplest reliable approach: use Java ZipOutputStream via root-cat reads
        return javaZipMulti(files, outZip)
    }

    // -------------------------------------------------------------------------
    // UNZIP
    // -------------------------------------------------------------------------

    /**
     * Extracts [zipFile] into [targetDir] (created if necessary).
     * Returns true on success.
     */
    suspend fun unzipToDir(zipFile: File, targetDir: File): Boolean {
        val zipPath = zipFile.absolutePath
        val dirPath = targetDir.absolutePath

        // Ensure target dir exists (root mkdir)
        RootShell.exec("mkdir -p ${RootShell.quote(dirPath)}")

        // Strategy 1 — native unzip
        val r1 = RootShell.exec(
            "unzip -o ${RootShell.quote(zipPath)} -d ${RootShell.quote(dirPath)} 2>&1"
        )
        if (r1.success) {
            Log.d(TAG, "unzipToDir: native unzip succeeded")
            return true
        }
        Log.w(TAG, "unzipToDir: native unzip failed (${r1.exitCode}): ${r1.stderr.take(200)}")

        // Strategy 2 — busybox unzip
        val r2 = RootShell.exec(
            "busybox unzip -o ${RootShell.quote(zipPath)} -d ${RootShell.quote(dirPath)} 2>&1"
        )
        if (r2.success) {
            Log.d(TAG, "unzipToDir: busybox unzip succeeded")
            return true
        }
        Log.w(TAG, "unzipToDir: busybox unzip failed (${r2.exitCode}): ${r2.stderr.take(200)}")

        // Strategy 3 — Java ZipInputStream (file must be readable by app process,
        //               i.e. caller already decrypted to a temp location)
        return javaUnzipFallback(zipFile, targetDir)
    }

    // -------------------------------------------------------------------------
    // Internal — Java fallbacks
    // -------------------------------------------------------------------------

    /** Fallback: copies source dir tree to a readable temp dir, then streams into ZipOutputStream. */
    private suspend fun javaZipFallback(sourceDir: File, outZip: File): Boolean {
        return try {
            // Copy source tree to readable temp via root
            val tmpDir = File(outZip.parentFile, ".tbk_tmp_zip_${System.nanoTime()}")
            val cpResult = RootShell.exec(
                "cp -a ${RootShell.quote(sourceDir.absolutePath)} ${RootShell.quote(tmpDir.absolutePath)} && " +
                "chmod -R a+r ${RootShell.quote(tmpDir.absolutePath)}"
            )
            if (!cpResult.success) {
                Log.e(TAG, "javaZipFallback: cp failed: ${cpResult.stderr}")
                return false
            }

            val ok = streamZipDir(tmpDir, outZip)
            RootShell.exec("rm -rf ${RootShell.quote(tmpDir.absolutePath)}")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "javaZipFallback failed", e)
            false
        }
    }

    private fun streamZipDir(srcDir: File, outZip: File): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(outZip)).use { zos ->
                addDirToZip(zos, srcDir, "")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "streamZipDir failed", e)
            false
        }
    }

    private fun addDirToZip(zos: ZipOutputStream, dir: File, prefix: String) {
        dir.listFiles()?.forEach { child ->
            val entryName = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            when {
                child.isSymlink() -> { /* skip symlinks for safety */ }
                child.isDirectory -> {
                    zos.putNextEntry(ZipEntry("$entryName/"))
                    zos.closeEntry()
                    addDirToZip(zos, child, entryName)
                }
                child.isFile -> {
                    zos.putNextEntry(ZipEntry(entryName))
                    child.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun File.isSymlink(): Boolean {
        return try {
            canonicalPath != absolutePath
        } catch (_: Exception) { false }
    }

    private suspend fun javaZipMulti(files: List<Pair<File, String>>, outZip: File): Boolean {
        // Copy each file to a temp readable location, then zip
        val tmpDir = File(outZip.parentFile, ".tbk_tmp_multi_${System.nanoTime()}")
        tmpDir.mkdirs()
        return try {
            val mapped = files.mapNotNull { (file, entry) ->
                val tmpFile = File(tmpDir, entry)
                tmpFile.parentFile?.mkdirs()
                val r = RootShell.exec(
                    "cat ${RootShell.quote(file.absolutePath)} > ${RootShell.quote(tmpFile.absolutePath)} && " +
                    "chmod a+r ${RootShell.quote(tmpFile.absolutePath)}"
                )
                if (r.success) tmpFile to entry else null
            }
            if (mapped.isEmpty()) return false
            ZipOutputStream(FileOutputStream(outZip)).use { zos ->
                mapped.forEach { (f, entry) ->
                    if (!f.exists()) return@forEach
                    zos.putNextEntry(ZipEntry(entry))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "javaZipMulti failed", e)
            false
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private fun javaUnzipFallback(zipFile: File, targetDir: File): Boolean {
        return try {
            targetDir.mkdirs()
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (isPathTraversal(entry.name)) {
                        Log.w(TAG, "Skipping path traversal entry: ${entry.name}")
                        entry = zis.nextEntry
                        continue
                    }
                    val outFile = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                    }
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "javaUnzipFallback failed", e)
            false
        }
    }

    private fun isPathTraversal(name: String): Boolean {
        if (name.isEmpty() || name.startsWith("/")) return true
        return name.split("/").any { it == ".." }
    }
}
