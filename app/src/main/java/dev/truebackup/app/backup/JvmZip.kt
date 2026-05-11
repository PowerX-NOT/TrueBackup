package dev.truebackup.app.backup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Zip / unzip using only [java.util.zip] (no busybox `zip`/`unzip` on device).
 */
object JvmZip {

    private const val bufferSize = 65536

    /**
     * Recursively zips all regular files under [sourceDir] into [zipFile].
     * Entry names use forward slashes. Creates parent dirs for [zipFile].
     */
    fun zipDirectory(sourceDir: File, zipFile: File) {
        require(sourceDir.exists() && sourceDir.isDirectory) { "source must exist and be a directory: $sourceDir" }
        zipFile.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile), bufferSize)).use { zos ->
            sourceDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val relative = sourceDir.toURI().relativize(file.toURI()).path
                        .trimEnd('/')
                        .replace('\\', '/')
                    if (relative.isEmpty()) return@forEach
                    zos.putNextEntry(ZipEntry(relative))
                    BufferedInputStream(FileInputStream(file), bufferSize).use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }
        }
    }

    /**
     * Extracts [zipFile] into [destDir]. Guards against zip-slip.
     */
    fun unzip(zipFile: File, destDir: File) {
        require(zipFile.isFile) { "zip must be a file: $zipFile" }
        destDir.mkdirs()
        val destCanonical = destDir.canonicalFile
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile), bufferSize)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val outFile = File(destDir, entry.name)
                val outCanonical = outFile.canonicalFile
                if (!outCanonical.path.startsWith(destCanonical.path + File.separator) &&
                    outCanonical.path != destCanonical.path
                ) {
                    throw SecurityException("Zip slip blocked: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(outFile), bufferSize).use { bos ->
                        zis.copyTo(bos)
                    }
                }
                zis.closeEntry()
            }
        }
    }
}
