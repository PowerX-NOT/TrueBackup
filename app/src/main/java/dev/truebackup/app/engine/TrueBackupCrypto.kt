package dev.truebackup.app.engine

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * TBK1 encryption engine — byte-for-byte compatible with truebackupd.cpp.
 *
 * Format: [magic:4][ver:1][iters_BE:4][salt:16][wrapIv:12][payloadIv:12]
 *         [wrappedMaster:48][encPayload...][payloadTag:16]
 */
object TrueBackupCrypto {

    private const val TAG = "TrueBackupCrypto"
    private val MAGIC = byteArrayOf('T'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())
    private const val VERSION: Byte = 1
    private const val PBKDF2_ITERS = 120_000
    private const val SALT_LEN = 16
    private const val WRAP_IV_LEN = 12
    private const val PAYLOAD_IV_LEN = 12
    private const val MASTER_KEY_LEN = 32
    private const val GCM_TAG_LEN = 16
    private const val WRAPPED_MASTER_LEN = MASTER_KEY_LEN + GCM_TAG_LEN // 48
    private val rng = SecureRandom()

    fun isTbk1(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false
        return try {
            val buf = ByteArray(4)
            FileInputStream(file).use { it.read(buf) }
            buf.contentEquals(MAGIC)
        } catch (_: Exception) { false }
    }

    fun encryptInPlace(plainFile: File, password: String): Boolean {
        if (!plainFile.isFile || password.isEmpty()) return false
        if (isTbk1(plainFile)) return true
        val tmpFile = File(plainFile.parent, plainFile.name + ".tbk1_tmp")
        return try {
            if (!encryptToFile(plainFile, tmpFile, password)) { tmpFile.delete(); return false }
            plainFile.delete()
            tmpFile.renameTo(plainFile)
            true
        } catch (e: Exception) {
            Log.e(TAG, "encryptInPlace failed", e)
            tmpFile.delete(); false
        }
    }

    fun decryptToFile(encFile: File, outFile: File, password: String): Boolean {
        if (!encFile.isFile || password.isEmpty()) return false
        return try {
            decryptInternal(encFile, outFile, password)
        } catch (e: Exception) {
            Log.e(TAG, "decryptToFile failed", e)
            outFile.delete(); false
        }
    }

    private fun encryptToFile(src: File, dst: File, password: String): Boolean {
        val salt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
        val wrapIv = ByteArray(WRAP_IV_LEN).also { rng.nextBytes(it) }
        val payloadIv = ByteArray(PAYLOAD_IV_LEN).also { rng.nextBytes(it) }
        val masterKey = ByteArray(MASTER_KEY_LEN).also { rng.nextBytes(it) }
        val unlockKey = deriveKey(password, salt, PBKDF2_ITERS)
        val wrappedMaster = aesGcmEncrypt(masterKey, unlockKey, wrapIv) ?: return false
        if (wrappedMaster.size != WRAPPED_MASTER_LEN) return false

        FileOutputStream(dst).use { out ->
            out.write(MAGIC); out.write(VERSION.toInt())
            out.write(intBE(PBKDF2_ITERS))
            out.write(salt); out.write(wrapIv); out.write(payloadIv); out.write(wrappedMaster)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"),
                GCMParameterSpec(GCM_TAG_LEN * 8, payloadIv))
            FileInputStream(src).use { inp ->
                val buf = ByteArray(64 * 1024)
                var n: Int
                while (inp.read(buf).also { n = it } > 0) { cipher.update(buf, 0, n)?.let { out.write(it) } }
            }
            out.write(cipher.doFinal())
        }
        masterKey.fill(0); unlockKey.fill(0)
        return true
    }

    private fun decryptInternal(src: File, dst: File, password: String): Boolean {
        FileInputStream(src).use { inp ->
            if (!inp.readNBytes(4).contentEquals(MAGIC)) return false
            if (inp.read() != VERSION.toInt()) return false
            val iters = beInt(inp.readNBytes(4))
            if (iters <= 0 || iters > 1_000_000) return false
            val salt = inp.readNBytes(SALT_LEN)
            val wrapIv = inp.readNBytes(WRAP_IV_LEN)
            val payloadIv = inp.readNBytes(PAYLOAD_IV_LEN)
            val wrappedMaster = inp.readNBytes(WRAPPED_MASTER_LEN)
            val unlockKey = deriveKey(password, salt, iters)
            val masterKey = aesGcmDecrypt(wrappedMaster, unlockKey, wrapIv) ?: return false
            if (masterKey.size != MASTER_KEY_LEN) return false
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(masterKey, "AES"),
                GCMParameterSpec(GCM_TAG_LEN * 8, payloadIv))
            masterKey.fill(0); unlockKey.fill(0)
            FileOutputStream(dst).use { out ->
                val buf = ByteArray(64 * 1024); var n: Int
                while (inp.read(buf).also { n = it } > 0) { cipher.update(buf, 0, n)?.let { out.write(it) } }
                out.write(cipher.doFinal())
            }
        }
        return true
    }

    private fun deriveKey(pw: String, salt: ByteArray, iters: Int): ByteArray {
        val spec = PBEKeySpec(pw.toCharArray(), salt, iters, MASTER_KEY_LEN * 8)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun aesGcmEncrypt(plain: ByteArray, key: ByteArray, iv: ByteArray): ByteArray? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LEN * 8, iv))
        c.doFinal(plain)
    } catch (_: Exception) { null }

    private fun aesGcmDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LEN * 8, iv))
        c.doFinal(data)
    } catch (_: Exception) { null }

    private fun intBE(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun beInt(b: ByteArray) = ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
}
