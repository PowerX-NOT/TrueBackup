package dev.truebackup.app.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class OpenSslEncCompatTest {

    @Test
    fun roundtripUtf8Passphrase() {
        val dir = File(File(System.getProperty("java.io.tmpdir")!!), "tbt_${System.nanoTime()}").apply { mkdirs() }
        try {
            val plain = File(dir, "plain.txt").apply { writeText("hello-UTF-8-ää") }
            val enc = File(dir, "out.enc")
            val out = File(dir, "dec.txt")
            assertTrue(OpenSslEncCompat.encryptFile(plain.absolutePath, enc.absolutePath, "päss"))
            assertTrue(OpenSslEncCompat.decryptFile(enc.absolutePath, out.absolutePath, "päss"))
            assertEquals(plain.readText(), out.readText())
            assertTrue(OpenSslEncCompat.decryptProbe(enc.absolutePath, "päss"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun rekeyRoundtrip() {
        val dir = File(File(System.getProperty("java.io.tmpdir")!!), "tbt_${System.nanoTime()}").apply { mkdirs() }
        try {
            val plain = File(dir, "p.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
            val enc = File(dir, "blob.enc")
            assertTrue(OpenSslEncCompat.encryptFile(plain.absolutePath, enc.absolutePath, "old"))
            assertTrue(OpenSslEncCompat.rekeyFileInPlace(enc.absolutePath, "old", "new"))
            val dec = File(dir, "out.bin")
            assertTrue(OpenSslEncCompat.decryptFile(enc.absolutePath, dec.absolutePath, "new"))
            assertEquals(plain.readBytes().toList(), dec.readBytes().toList())
        } finally {
            dir.deleteRecursively()
        }
    }

    /** If `openssl` is on PATH, ciphertext must be readable by `openssl enc -d`. */
    @Test
    fun interoperatesWithSystemOpensslDecrypt() {
        val openssl = findOpenssl() ?: return
        val dir = File(File(System.getProperty("java.io.tmpdir")!!), "tbt_${System.nanoTime()}").apply { mkdirs() }
        try {
            val plain = File(dir, "msg.txt").apply { writeText("interop") }
            val enc = File(dir, "from_kotlin.enc")
            assertTrue(OpenSslEncCompat.encryptFile(plain.absolutePath, enc.absolutePath, "secret"))
            val viaOpenssl = File(dir, "via_openssl.txt")
            val pb = ProcessBuilder(
                openssl,
                "enc",
                "-d",
                "-aes-256-cbc",
                "-pbkdf2",
                "-in",
                enc.absolutePath,
                "-out",
                viaOpenssl.absolutePath,
                "-k",
                "secret"
            )
            pb.redirectErrorStream(true)
            val p = pb.start()
            assertTrue(p.waitFor(30, TimeUnit.SECONDS))
            assertEquals(0, p.exitValue())
            assertEquals("interop", viaOpenssl.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun interoperatesWithSystemOpensslEncrypt() {
        val openssl = findOpenssl() ?: return
        val dir = File(File(System.getProperty("java.io.tmpdir")!!), "tbt_${System.nanoTime()}").apply { mkdirs() }
        try {
            val plain = File(dir, "msg.txt").apply { writeText("from-openssl") }
            val enc = File(dir, "from_openssl.enc")
            val pb = ProcessBuilder(
                openssl,
                "enc",
                "-aes-256-cbc",
                "-salt",
                "-pbkdf2",
                "-in",
                plain.absolutePath,
                "-out",
                enc.absolutePath,
                "-k",
                "abc"
            )
            pb.redirectErrorStream(true)
            val p = pb.start()
            assertTrue(p.waitFor(30, TimeUnit.SECONDS))
            assertEquals(0, p.exitValue())
            val dec = File(dir, "dec.txt")
            assertTrue(OpenSslEncCompat.decryptFile(enc.absolutePath, dec.absolutePath, "abc"))
            assertEquals("from-openssl", dec.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun findOpenssl(): String? {
        val path = System.getenv("PATH") ?: return null
        for (dir in path.split(File.pathSeparator)) {
            val f = File(dir, "openssl")
            if (f.isFile && f.canExecute()) return f.absolutePath
        }
        return null
    }
}
