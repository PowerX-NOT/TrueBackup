package dev.truebackup.app.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class OpenSslEncCompatTest {

    @Test
    fun roundtripUtf8Passphrase() {
        withTempDir { dir ->
            val plain = File(dir, "plain.txt").apply { writeText("hello-UTF-8-ää") }
            val enc = File(dir, "out.enc")
            val out = File(dir, "dec.txt")
            assertTrue(OpenSslEncCompat.encryptFile(plain.absolutePath, enc.absolutePath, "päss"))
            assertTrue(OpenSslEncCompat.decryptFile(enc.absolutePath, out.absolutePath, "päss"))
            assertEquals(plain.readText(), out.readText())
            assertTrue(OpenSslEncCompat.decryptProbe(enc.absolutePath, "päss"))
        }
    }

    @Test
    fun rekeyRoundtrip() {
        withTempDir { dir ->
            val plain = File(dir, "p.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
            val enc = File(dir, "blob.enc")
            assertTrue(OpenSslEncCompat.encryptFile(plain.absolutePath, enc.absolutePath, "old"))
            assertTrue(OpenSslEncCompat.rekeyFileInPlace(enc.absolutePath, "old", "new"))
            val dec = File(dir, "out.bin")
            assertTrue(OpenSslEncCompat.decryptFile(enc.absolutePath, dec.absolutePath, "new"))
            assertArrayEquals(plain.readBytes(), dec.readBytes())
        }
    }

    @Test
    fun interoperatesWithSystemOpenssl() {
        val openssl = findOpenssl()
        assumeTrue("openssl not on PATH", openssl != null)
        val bin = openssl!!

        withTempDir { dir ->
            val plain = File(dir, "msg.txt").apply { writeText("interop") }
            val enc = File(dir, "kotlin.enc")
            assertTrue(OpenSslEncCompat.encryptFile(plain.absolutePath, enc.absolutePath, "secret"))

            val viaCli = File(dir, "via_openssl.txt")
            assertEquals(
                0,
                runProcess(
                    bin, "enc", "-d", "-aes-256-cbc", "-pbkdf2",
                    "-in", enc.absolutePath, "-out", viaCli.absolutePath, "-k", "secret"
                )
            )
            assertEquals("interop", viaCli.readText())

            val fromCli = File(dir, "openssl.enc")
            assertEquals(
                0,
                runProcess(
                    bin, "enc", "-aes-256-cbc", "-salt", "-pbkdf2",
                    "-in", plain.absolutePath, "-out", fromCli.absolutePath, "-k", "secret"
                )
            )
            val round = File(dir, "kotlin_dec.txt")
            assertTrue(OpenSslEncCompat.decryptFile(fromCli.absolutePath, round.absolutePath, "secret"))
            assertEquals("interop", round.readText())
        }
    }

    private fun <T> withTempDir(block: (File) -> T): T {
        val parent = checkNotNull(System.getProperty("java.io.tmpdir"))
        val dir = File(parent, "tbt_${System.nanoTime()}").apply { mkdirs() }
        try {
            return block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun runProcess(vararg command: String): Int {
        val p = ProcessBuilder(*command).redirectErrorStream(true).start()
        check(p.waitFor(30, TimeUnit.SECONDS)) { "openssl subprocess timed out" }
        return p.exitValue()
    }

    private fun findOpenssl(): String? {
        val path = System.getenv("PATH") ?: return null
        for (segment in path.split(File.pathSeparator)) {
            val candidate = File(segment, "openssl")
            if (candidate.isFile && candidate.canExecute()) return candidate.absolutePath
        }
        return null
    }
}
