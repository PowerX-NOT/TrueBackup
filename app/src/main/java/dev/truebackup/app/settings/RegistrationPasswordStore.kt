package dev.truebackup.app.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Same at-rest registration password scheme as [com.android.server.TrueBackupService]:
 * AES-256-GCM via AndroidKeyStore alias `truebackup_registration_password_v1`, blob prefix `tbpw1:`.
 * ROM `truebackupd` stores the blob under `/data/system/truebackup/registration_password.bin`; here we
 * use an app-private file so the plaintext password is recoverable for on-device decrypt/rekey of encrypted archives.
 *
 * **Backups:** `.tar.enc` uses the same on-disk format as **OpenSSL** `enc -aes-256-cbc -salt -pbkdf2` (UTF-8 passphrase), implemented in-app.
 */
class RegistrationPasswordStore(private val context: Context) {

    private val blobFile: File
        get() = File(context.filesDir, "truebackup_registration_password.blob")

    fun readPlaintext(): String? {
        val stored = blobFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        return decryptRegisteredPassword(stored) ?: stored
    }

    fun writePlaintext(plain: String): Boolean {
        val stored = encryptRegisteredPassword(plain) ?: return false
        return runCatching {
            blobFile.parentFile?.mkdirs()
            blobFile.writeText(stored)
            true
        }.getOrDefault(false)
    }

    /** Returns false if current password does not match [oldPlain]. */
    fun changePlaintext(oldPlain: String, newPlain: String): Boolean {
        val cur = readPlaintext() ?: return false
        if (cur != oldPlain) return false
        return writePlaintext(newPlain)
    }

    fun clear(): Boolean = runCatching { blobFile.delete() }.getOrDefault(false)

    fun isConfigured(): Boolean = blobFile.isFile && readPlaintext() != null

    private fun getOrCreateRegPasswordKey(): SecretKey {
        val ks = KeyStore.getInstance(REG_PW_KEYSTORE)
        ks.load(null)
        val entry = ks.getEntry(REG_PW_KEY_ALIAS, null)
        if (entry is KeyStore.SecretKeyEntry) {
            return entry.secretKey
        }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, REG_PW_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            REG_PW_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        kg.init(spec)
        return kg.generateKey()
    }

    private fun encryptRegisteredPassword(plain: String): String? {
        return runCatching {
            val key = getOrCreateRegPasswordKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ct = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
            val blob = ByteArray(1 + iv.size + ct.size)
            blob[0] = iv.size.toByte()
            System.arraycopy(iv, 0, blob, 1, iv.size)
            System.arraycopy(ct, 0, blob, 1 + iv.size, ct.size)
            REG_PW_BLOB_PREFIX + Base64.getEncoder().encodeToString(blob)
        }.getOrNull()
    }

    private fun decryptRegisteredPassword(stored: String): String? {
        if (!stored.startsWith(REG_PW_BLOB_PREFIX)) return null
        return runCatching {
            val b64 = stored.substring(REG_PW_BLOB_PREFIX.length)
            val blob = Base64.getDecoder().decode(b64)
            if (blob.size < 2) return@runCatching null
            val ivLen = blob[0].toInt() and 0xff
            if (ivLen <= 0 || 1 + ivLen >= blob.size) return@runCatching null
            val iv = ByteArray(ivLen)
            System.arraycopy(blob, 1, iv, 0, ivLen)
            val ctLen = blob.size - 1 - ivLen
            val ct = ByteArray(ctLen)
            System.arraycopy(blob, 1 + ivLen, ct, 0, ctLen)
            val key = getOrCreateRegPasswordKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(ct)
            String(plain, StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private companion object {
        private const val REG_PW_KEYSTORE = "AndroidKeyStore"
        private const val REG_PW_KEY_ALIAS = "truebackup_registration_password_v1"
        private const val REG_PW_BLOB_PREFIX = "tbpw1:"
    }
}
