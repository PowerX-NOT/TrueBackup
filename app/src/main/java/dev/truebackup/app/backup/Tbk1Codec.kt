package dev.truebackup.app.backup

import android.system.Os
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * TrueBackup container v1 (TBK1) — matches `cmds/truebackupd/truebackupd.cpp`
 * (`EncryptZipInPlace` / `DecryptZipToFile`): AES-256-GCM wrapped master + AES-256-GCM payload,
 * PBKDF2-HMAC-SHA256 (120000 iterations) from UTF-8 password bytes.
 */
object Tbk1Codec {

    private val MAGIC = byteArrayOf('T'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())
    private const val VERSION: Byte = 1
    private const val PBKDF2_ITERATIONS = 120_000
    private const val SALT_LEN = 16
    private const val WRAP_IV_LEN = 12
    private const val PAYLOAD_IV_LEN = 12
    private const val MASTER_KEY_LEN = 32
    private const val GCM_TAG_LEN = 16
    private const val WRAPPED_MASTER_LEN = 48

    internal const val HEADER_LEN = 4 + 1 + 4 + SALT_LEN + WRAP_IV_LEN + PAYLOAD_IV_LEN + WRAPPED_MASTER_LEN

    fun isTbk1(file: File): Boolean {
        if (!file.isFile || file.length() < 4L) return false
        return file.inputStream().use { ins ->
            val m = ByteArray(4)
            if (ins.read(m) != 4) return@use false
            m.contentEquals(MAGIC)
        }
    }

    private fun writeU32Be(out: java.io.OutputStream, v: Int) {
        out.write((v ushr 24) and 0xff)
        out.write((v ushr 16) and 0xff)
        out.write((v ushr 8) and 0xff)
        out.write(v and 0xff)
    }

    /**
     * Encrypts a plain zip in place (same semantics as truebackupd `EncryptZipInPlace`).
     */
    fun encryptInPlace(plainZip: File, password: String): Boolean {
        if (!plainZip.isFile || password.isEmpty()) return false
        if (isTbk1(plainZip)) return true

        val tmp = File(plainZip.absolutePath + ".enc_tmp")
        val modeBits = runCatching { Os.stat(plainZip.absolutePath).st_mode and 0xfff }.getOrDefault(0)

        return runCatching {
            val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
            val wrapIv = ByteArray(WRAP_IV_LEN).also { SecureRandom().nextBytes(it) }
            val payloadIv = ByteArray(PAYLOAD_IV_LEN).also { SecureRandom().nextBytes(it) }
            val masterKey = ByteArray(MASTER_KEY_LEN).also { SecureRandom().nextBytes(it) }

            val unlockKey = deriveKey(password, salt, PBKDF2_ITERATIONS)
            val wrappedMaster = wrapMasterKey(unlockKey, wrapIv, masterKey)
            cleanse(unlockKey)

            BufferedOutputStream(FileOutputStream(tmp)).use { bout ->
                bout.write(MAGIC)
                bout.write(VERSION.toInt())
                writeU32Be(bout, PBKDF2_ITERATIONS)
                bout.write(salt)
                bout.write(wrapIv)
                bout.write(payloadIv)
                bout.write(wrappedMaster)

                val payloadCipher = Cipher.getInstance("AES/GCM/NoPadding")
                val mkSpec = SecretKeySpec(masterKey, "AES")
                cleanse(masterKey)
                payloadCipher.init(Cipher.ENCRYPT_MODE, mkSpec, GCMParameterSpec(GCM_TAG_LEN * 8, payloadIv))

                BufferedInputStream(FileInputStream(plainZip)).use { fin ->
                    CipherOutputStream(bout, payloadCipher).use { cos ->
                        fin.copyTo(cos)
                    }
                }
            }

            if (!plainZip.delete()) {
                tmp.delete()
                return@runCatching false
            }
            if (!tmp.renameTo(plainZip)) {
                return@runCatching false
            }
            if (modeBits != 0) {
                runCatching { Os.chmod(plainZip.absolutePath, modeBits) }
            }
            true
        }.getOrElse {
            tmp.delete()
            false
        }
    }

    /**
     * Decrypts TBK1 [encFile] to a new plain file (same semantics as truebackupd `DecryptZipToFile`).
     */
    fun decryptToFile(encFile: File, outPlain: File, password: String): Boolean {
        if (!encFile.isFile || password.isEmpty()) return false
        outPlain.parentFile?.mkdirs()

        return runCatching {
            RandomAccessFile(encFile, "r").use { raf ->
                val magic = ByteArray(4)
                if (raf.read(magic) != 4 || !magic.contentEquals(MAGIC)) return@runCatching false
                if (raf.readByte() != VERSION) return@runCatching false
                val iterations = readU32BeRaf(raf)
                if (iterations <= 0) return@runCatching false
                val salt = ByteArray(SALT_LEN)
                val wrapIv = ByteArray(WRAP_IV_LEN)
                val payloadIv = ByteArray(PAYLOAD_IV_LEN)
                val wrappedMaster = ByteArray(WRAPPED_MASTER_LEN)
                if (raf.read(salt) != SALT_LEN || raf.read(wrapIv) != WRAP_IV_LEN ||
                    raf.read(payloadIv) != PAYLOAD_IV_LEN || raf.read(wrappedMaster) != WRAPPED_MASTER_LEN
                ) {
                    return@runCatching false
                }

                val headerEnd = raf.filePointer
                val fileSize = raf.length()
                val totalCipher = fileSize - headerEnd
                if (totalCipher < GCM_TAG_LEN) return@runCatching false
                val ctLen = totalCipher - GCM_TAG_LEN

                val unlockKey = deriveKey(password, salt, iterations)
                val masterKey = unwrapMasterKey(unlockKey, wrapIv, wrappedMaster)
                cleanse(unlockKey)
                cleanse(wrappedMaster)

                val payloadCipher = Cipher.getInstance("AES/GCM/NoPadding")
                val mkSpec = SecretKeySpec(masterKey, "AES")
                cleanse(masterKey)
                payloadCipher.init(Cipher.DECRYPT_MODE, mkSpec, GCMParameterSpec(GCM_TAG_LEN * 8, payloadIv))

                outPlain.delete()
                FileOutputStream(outPlain).use { fos ->
                    val buf = ByteArray(64 * 1024)
                    var remaining = ctLen
                    while (remaining > buf.size) {
                        val n = raf.read(buf, 0, buf.size)
                        if (n != buf.size) return@runCatching false
                        val dec = payloadCipher.update(buf, 0, n)
                        if (dec.isNotEmpty()) fos.write(dec)
                        remaining -= n.toLong()
                    }
                    if (remaining > 0) {
                        val n = raf.read(buf, 0, remaining.toInt())
                        if (n != remaining.toInt()) return@runCatching false
                        val tag = ByteArray(GCM_TAG_LEN)
                        if (raf.read(tag) != GCM_TAG_LEN) return@runCatching false
                        val p1 = payloadCipher.update(buf, 0, n)
                        if (p1.isNotEmpty()) fos.write(p1)
                        val p2 = try {
                            payloadCipher.doFinal(tag)
                        } catch (_: AEADBadTagException) {
                            return@runCatching false
                        }
                        if (p2.isNotEmpty()) fos.write(p2)
                    } else {
                        val tag = ByteArray(GCM_TAG_LEN)
                        if (raf.read(tag) != GCM_TAG_LEN) return@runCatching false
                        val p2 = try {
                            payloadCipher.doFinal(tag)
                        } catch (_: AEADBadTagException) {
                            return@runCatching false
                        }
                        if (p2.isNotEmpty()) fos.write(p2)
                    }
                    if (raf.filePointer != raf.length()) return@runCatching false
                }
                true
            }
        }.getOrElse {
            outPlain.delete()
            false
        }
    }

    private fun readU32BeRaf(raf: RandomAccessFile): Int {
        val b = ByteArray(4)
        if (raf.read(b) != 4) return -1
        return ((b[0].toInt() and 0xff) shl 24) or ((b[1].toInt() and 0xff) shl 16) or
            ((b[2].toInt() and 0xff) shl 8) or (b[3].toInt() and 0xff)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, MASTER_KEY_LEN * 8)
        return factory.generateSecret(spec).encoded
    }

    private fun wrapMasterKey(unlockKey: ByteArray, wrapIv: ByteArray, masterKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = SecretKeySpec(unlockKey, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, spec, GCMParameterSpec(GCM_TAG_LEN * 8, wrapIv))
        return cipher.doFinal(masterKey)
    }

    private fun unwrapMasterKey(unlockKey: ByteArray, wrapIv: ByteArray, wrapped: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = SecretKeySpec(unlockKey, "AES")
        cipher.init(Cipher.DECRYPT_MODE, spec, GCMParameterSpec(GCM_TAG_LEN * 8, wrapIv))
        return cipher.doFinal(wrapped)
    }

    private fun cleanse(b: ByteArray) {
        java.util.Arrays.fill(b, 0.toByte())
    }
}
