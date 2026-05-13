package dev.truebackup.app.crypto

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Same on-disk format as `openssl enc -aes-256-cbc -salt -pbkdf2` /
 * `openssl enc -d -aes-256-cbc -pbkdf2` (Salted__ header, PBKDF2-HMAC-SHA256,
 * 10000 iterations, passphrase as UTF-8 octets — matches OpenSSL 1.1.1+ / 3.x `enc` defaults).
 */
object OpenSslEncCompat {

    private const val MAGIC = "Salted__"
    private const val SALT_LEN = 8
    private const val KEY_BITS = 256
    private const val IV_LEN = 16
    private const val PBKDF2_ITERATIONS = 10_000

    private val secureRandom = SecureRandom()

    fun encryptFile(plainPath: String, cipherPath: String, password: String): Boolean =
        runCatching {
            val salt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
            val (key, iv) = deriveKeyIv(password, salt)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            FileOutputStream(cipherPath).use { out ->
                out.write(MAGIC.toByteArray(Charsets.US_ASCII))
                out.write(salt)
                FileInputStream(plainPath).use { ins ->
                    CipherOutputStream(out, cipher).use { cos -> ins.copyTo(cos) }
                }
            }
            true
        }.getOrDefault(false)

    fun decryptFile(cipherPath: String, plainPath: String, password: String): Boolean =
        runCatching {
            FileInputStream(cipherPath).use { ins ->
                val header = ByteArray(MAGIC.length + SALT_LEN)
                if (!ins.readFully(header)) return@runCatching false
                if (!header.copyOfRange(0, MAGIC.length).contentEquals(MAGIC.toByteArray(Charsets.US_ASCII))) {
                    return@runCatching false
                }
                val salt = header.copyOfRange(MAGIC.length, header.size)
                val (key, iv) = deriveKeyIv(password, salt)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                FileOutputStream(plainPath).use { out ->
                    CipherInputStream(ins, cipher).use { cis -> cis.copyTo(out) }
                }
            }
            true
        }.getOrDefault(false)

    /** True if [cipherPath] decrypts with [password] (output discarded). */
    fun decryptProbe(cipherPath: String, password: String): Boolean =
        runCatching {
            FileInputStream(cipherPath).use { ins ->
                val header = ByteArray(MAGIC.length + SALT_LEN)
                if (!ins.readFully(header)) return@runCatching false
                if (!header.copyOfRange(0, MAGIC.length).contentEquals(MAGIC.toByteArray(Charsets.US_ASCII))) {
                    return@runCatching false
                }
                val salt = header.copyOfRange(MAGIC.length, header.size)
                val (key, iv) = deriveKeyIv(password, salt)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                CipherInputStream(ins, cipher).use { cis ->
                    val sink = ByteArray(8192)
                    while (cis.read(sink) >= 0) { }
                }
            }
            true
        }.getOrDefault(false)

    /**
     * Re-encrypt in place using temp files in [target]'s parent directory (same filesystem as rename).
     */
    fun rekeyFileInPlace(absPath: String, oldPassword: String, newPassword: String): Boolean {
        val target = File(absPath)
        val dir = target.parentFile ?: return false
        val tmpPlain = File.createTempFile("tb_rekey_plain_", ".tmp", dir)
        var tmpCipher: File? = File.createTempFile("tb_rekey_enc_", ".tmp", dir)
        try {
            if (!decryptFile(absPath, tmpPlain.absolutePath, oldPassword)) return false
            val encOut = tmpCipher!!
            if (!encryptFile(tmpPlain.absolutePath, encOut.absolutePath, newPassword)) return false
            if (!target.delete()) return false
            if (!encOut.renameTo(target)) return false
            tmpCipher = null
            return target.isFile
        } finally {
            tmpPlain.delete()
            tmpCipher?.delete()
        }
    }

    private fun deriveKeyIv(password: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val passUtf8 = password.toByteArray(Charsets.UTF_8)
        val keyLenBytes = KEY_BITS / 8
        val dk = pbkdf2HmacSha256(passUtf8, salt, PBKDF2_ITERATIONS, keyLenBytes + IV_LEN)
        val key = dk.copyOfRange(0, keyLenBytes)
        val iv = dk.copyOfRange(keyLenBytes, keyLenBytes + IV_LEN)
        return key to iv
    }

    /** RFC 2898 PBKDF2 with HMAC-SHA256 (same as OpenSSL `enc -pbkdf2`). */
    private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
        val hLen = 32
        val mac = Mac.getInstance("HmacSHA256")
        val prfKey = SecretKeySpec(password, "HmacSHA256")
        val blocks = (dkLen + hLen - 1) / hLen
        val dk = ByteArray(dkLen)
        var outOff = 0
        for (block in 1..blocks) {
            mac.reset()
            mac.init(prfKey)
            mac.update(salt)
            mac.update(int32BigEndian(block))
            var u = mac.doFinal()
            val f = u.copyOf(u.size)
            repeat(iterations - 1) {
                mac.reset()
                mac.init(prfKey)
                u = mac.doFinal(u)
                for (i in f.indices) {
                    f[i] = (f[i].toInt() xor u[i].toInt()).toByte()
                }
            }
            val copyLen = minOf(f.size, dkLen - outOff)
            System.arraycopy(f, 0, dk, outOff, copyLen)
            outOff += copyLen
        }
        return dk
    }

    private fun int32BigEndian(i: Int): ByteArray = byteArrayOf(
        (i shr 24).toByte(),
        (i shr 16).toByte(),
        (i shr 8).toByte(),
        i.toByte()
    )

    private fun InputStream.readFully(buffer: ByteArray): Boolean {
        var off = 0
        while (off < buffer.size) {
            val n = read(buffer, off, buffer.size - off)
            if (n < 0) return false
            off += n
        }
        return true
    }
}
