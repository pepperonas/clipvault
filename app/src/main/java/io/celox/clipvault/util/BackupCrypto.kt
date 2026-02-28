package io.celox.clipvault.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {

    private val MAGIC = byteArrayOf('C'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION = 1
    private const val IV_LENGTH = 12
    private const val SALT_LENGTH = 16
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val GCM_TAG_LENGTH = 128

    fun encrypt(json: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(json)

        // [4 magic] [4 version] [12 iv] [16 salt] [encrypted data]
        val versionBytes = ByteArray(4).apply {
            this[0] = (VERSION shr 24).toByte()
            this[1] = (VERSION shr 16).toByte()
            this[2] = (VERSION shr 8).toByte()
            this[3] = VERSION.toByte()
        }

        return MAGIC + versionBytes + iv + salt + encrypted
    }

    fun decrypt(data: ByteArray, password: String): ByteArray {
        if (data.size < MAGIC.size + 4 + IV_LENGTH + SALT_LENGTH) {
            throw IllegalArgumentException("Invalid backup file")
        }

        // Validate magic
        for (i in MAGIC.indices) {
            if (data[i] != MAGIC[i]) throw IllegalArgumentException("Invalid backup file")
        }

        // Read version
        var offset = MAGIC.size
        val version = (data[offset].toInt() and 0xFF shl 24) or
                (data[offset + 1].toInt() and 0xFF shl 16) or
                (data[offset + 2].toInt() and 0xFF shl 8) or
                (data[offset + 3].toInt() and 0xFF)
        offset += 4

        if (version != VERSION) throw IllegalArgumentException("Unsupported backup version: $version")

        val iv = data.copyOfRange(offset, offset + IV_LENGTH)
        offset += IV_LENGTH

        val salt = data.copyOfRange(offset, offset + SALT_LENGTH)
        offset += SALT_LENGTH

        val encrypted = data.copyOfRange(offset, data.size)

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(encrypted)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }
}
