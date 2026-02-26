package io.celox.clipvault.licensing

import io.celox.clipvault.security.KeyStoreManager
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class LicenseManager(private val keyStoreManager: KeyStoreManager) {

    companion object {
        private const val MAX_FREE_CLIPS = 10

        // Obfuscated secret via XOR — not stored as plain string in binary
        private val SECRET_A = byteArrayOf(
            0xE2.toByte(), 0xC5.toByte(), 0xD3.toByte(), 0xF9.toByte(),
            0x8C.toByte(), 0x65, 0x25, 0x73,
            0xE7.toByte(), 0xD3.toByte(), 0x47, 0xB7.toByte(),
            0xB3.toByte(), 0x77, 0x34, 0xB3.toByte()
        )
        private val SECRET_B = byteArrayOf(
            0xA1.toByte(), 0xB3.toByte(), 0xF7.toByte(), 0x92.toByte(),
            0xDE.toByte(), 0x5C, 0x48, 0x23,
            0x9F.toByte(), 0xE1.toByte(), 0x0B, 0xC6.toByte(),
            0x84.toByte(), 0x19, 0x63, 0xD5.toByte()
        )

        fun getMaxFreeClips(): Int = MAX_FREE_CLIPS
    }

    private fun getSecret(): ByteArray {
        return ByteArray(SECRET_A.size) { i ->
            (SECRET_A[i].toInt() xor SECRET_B[i].toInt()).toByte()
        }
    }

    /**
     * Generates the expected license key for a given email.
     * Format: XXXX-XXXX-XXXX-XXXX (16 hex chars uppercase)
     */
    private fun generateKey(email: String): String {
        val secret = getSecret()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val hash = mac.doFinal(email.lowercase().trim().toByteArray(Charsets.UTF_8))

        // Take first 8 bytes -> 16 hex chars
        val hex = hash.take(8).joinToString("") { "%02X".format(it) }

        // Format as XXXX-XXXX-XXXX-XXXX
        return hex.chunked(4).joinToString("-")
    }

    /**
     * Validates the key against the email, and if valid, stores license data.
     * @return true if the license was validated and activated successfully
     */
    fun validateAndActivate(email: String, key: String): Boolean {
        val expectedKey = generateKey(email)
        val normalizedKey = key.uppercase().trim()

        if (normalizedKey != expectedKey) return false

        keyStoreManager.storeLicenseData(email.trim(), normalizedKey, true)
        return true
    }

    fun isActivated(): Boolean {
        return keyStoreManager.isLicenseActivated()
    }

    fun getActivatedEmail(): String? {
        return if (isActivated()) keyStoreManager.getLicenseEmail() else null
    }
}
