package io.celox.clipvault.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyStoreManager(context: Context) {

    companion object {
        private const val KEYSTORE_ALIAS = "clipvault_db_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PREFS_NAME = "clipvault_secure_prefs"
        private const val KEY_ENCRYPTED_PASSPHRASE = "encrypted_passphrase"
        private const val KEY_IV = "passphrase_iv"
        private const val KEY_ENCRYPTION_ENABLED = "encryption_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_LICENSE_EMAIL = "license_email"
        private const val KEY_LICENSE_KEY = "license_key"
        private const val KEY_LICENSE_ACTIVATED = "license_activated"
        private const val AES_GCM_TAG_LENGTH = 128
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Encryption prefs ---

    fun isEncryptionEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENCRYPTION_ENABLED, false)
    }

    fun setEncryptionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENCRYPTION_ENABLED, enabled).apply()
    }

    // --- Biometric prefs ---

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    // --- Password management ---

    fun isPasswordSet(): Boolean {
        return prefs.contains(KEY_ENCRYPTED_PASSPHRASE)
    }

    fun storePassword(passphrase: String) {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val encrypted = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv

        prefs.edit()
            .putString(KEY_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    fun retrievePassword(): String? {
        val encryptedB64 = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null) ?: return null
        val ivB64 = prefs.getString(KEY_IV, null) ?: return null

        return try {
            val encrypted = Base64.decode(encryptedB64, Base64.NO_WRAP)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)

            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(AES_GCM_TAG_LENGTH, iv))

            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun clearPassword() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_PASSPHRASE)
            .remove(KEY_IV)
            .putBoolean(KEY_ENCRYPTION_ENABLED, false)
            .apply()
    }

    // --- License storage ---

    fun storeLicenseData(email: String, key: String, activated: Boolean) {
        prefs.edit()
            .putString(KEY_LICENSE_EMAIL, email)
            .putString(KEY_LICENSE_KEY, key)
            .putBoolean(KEY_LICENSE_ACTIVATED, activated)
            .apply()
    }

    fun isLicenseActivated(): Boolean {
        return prefs.getBoolean(KEY_LICENSE_ACTIVATED, false)
    }

    fun getLicenseEmail(): String? {
        return prefs.getString(KEY_LICENSE_EMAIL, null)
    }

    // --- KeyStore key management ---

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        keyStore.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }
}
