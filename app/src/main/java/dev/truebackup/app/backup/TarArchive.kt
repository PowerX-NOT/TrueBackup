package dev.truebackup.app.backup

import android.util.Log
import java.io.File

/**
 * Creates / extracts **ustar** tarballs using the system `tar` binary (toybox on Android),
 * same idea as piping `tar -cvf -` / `tar -xvf -` in a shell.
 */
object TarArchive {

    private const val TAG = "TarArchive"

    private val tarCandidates = listOf(
        "/system/bin/tar",
        "/vendor/bin/tar",
        "tar"
    )

    fun createFromDirectory(sourceDir: File, destTar: File): Boolean {
        if (!sourceDir.isDirectory) return false
        destTar.parentFile?.mkdirs()
        for (bin in tarCandidates) {
            val code = runTar(
                listOf(
                    bin,
                    "-cf",
                    destTar.absolutePath,
                    "-C",
                    sourceDir.absolutePath,
                    "."
                )
            )
            if (code == 0 && destTar.isFile && destTar.length() > 0L) return true
            destTar.delete()
        }
        Log.e(TAG, "tar -cf failed for ${sourceDir.absolutePath} -> ${destTar.absolutePath}")
        return false
    }

    fun extractToDirectory(tarFile: File, destDir: File): Boolean {
        if (!tarFile.isFile) return false
        destDir.mkdirs()
        for (bin in tarCandidates) {
            val code = runTar(
                listOf(
                    bin,
                    "-xf",
                    tarFile.absolutePath,
                    "-C",
                    destDir.absolutePath
                )
            )
            if (code == 0) return true
        }
        Log.e(TAG, "tar -xf failed for ${tarFile.absolutePath}")
        return false
    }

    private fun runTar(argv: List<String>): Int {
        return try {
            val pb = ProcessBuilder(argv)
                .redirectErrorStream(true)
            val p = pb.start()
            val err = p.inputStream.bufferedReader().use { it.readText() }.trim()
            val exit = p.waitFor()
            if (exit != 0 && err.isNotEmpty()) {
                Log.w(TAG, "tar stderr: ${err.take(500)}")
            }
            exit
        } catch (e: Exception) {
            Log.w(TAG, "tar exec failed: ${argv.firstOrNull()}", e)
            -1
        }
    }
}
