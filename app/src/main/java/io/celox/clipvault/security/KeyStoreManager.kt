package io.celox.clipvault.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyStoreManager(context: Context) {

    companion object {
        private const val KEYSTORE_ALIAS = "clipvault_db_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PREFS_NAME = "clipvault_secure_prefs"

        // DB auto-passphrase (always encrypted, user never sees)
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
        private const val KEY_DB_PASSPHRASE_IV = "db_passphrase_iv"

        // App lock (optional UI lock)
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_APP_LOCK_PASSWORD = "app_lock_password"
        private const val KEY_APP_LOCK_PASSWORD_IV = "app_lock_password_iv"
        private const val KEY_APP_LOCK_BIOMETRIC = "app_lock_biometric"
        private const val KEY_APP_LOCK_PW_GENERATED = "app_lock_pw_generated"

        // Display preferences
        private const val KEY_AMOLED_MODE = "amoled_mode"

        // Auto-cleanup
        private const val KEY_AUTO_CLEANUP_DAYS = "auto_cleanup_days"

        // Legacy keys (v1/v2 migration — read-only, then cleared)
        private const val KEY_LEGACY_PASSWORD = "encrypted_passphrase"
        private const val KEY_LEGACY_IV = "passphrase_iv"
        private const val KEY_LEGACY_ENCRYPTION_ENABLED = "encryption_enabled"
        private const val KEY_LEGACY_BIOMETRIC = "biometric_enabled"
        private const val KEY_LEGACY_PW_GENERATED = "password_generated"

        // Migration flag
        private const val KEY_V3_MIGRATED = "v3_migrated"

        private const val AES_GCM_TAG_LENGTH = 128
        private const val DB_PASSPHRASE_LENGTH = 64
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- DB Passphrase (auto-managed, user never sees) ---

    fun getOrCreateDbPassphrase(): String {
        val existing = retrieveEncrypted(KEY_DB_PASSPHRASE, KEY_DB_PASSPHRASE_IV)
        if (existing != null) return existing

        val passphrase = generateRandomPassphrase(DB_PASSPHRASE_LENGTH)
        storeEncrypted(passphrase, KEY_DB_PASSPHRASE, KEY_DB_PASSPHRASE_IV)
        return passphrase
    }

    fun setDbPassphrase(passphrase: String) {
        storeEncrypted(passphrase, KEY_DB_PASSPHRASE, KEY_DB_PASSPHRASE_IV)
    }

    // --- Display preferences ---

    fun isAmoledMode(): Boolean = prefs.getBoolean(KEY_AMOLED_MODE, false)

    fun setAmoledMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED_MODE, enabled).apply()
    }

    // --- Auto-cleanup ---

    fun getAutoCleanupDays(): Int = prefs.getInt(KEY_AUTO_CLEANUP_DAYS, 0)

    fun setAutoCleanupDays(days: Int) {
        prefs.edit().putInt(KEY_AUTO_CLEANUP_DAYS, days).apply()
    }

    // --- App Lock (optional UI lock) ---

    fun isAppLockEnabled(): Boolean = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
    }

    fun isAppLockBiometricEnabled(): Boolean = prefs.getBoolean(KEY_APP_LOCK_BIOMETRIC, true)

    fun setAppLockBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_BIOMETRIC, enabled).apply()
    }

    fun isAppLockPasswordGenerated(): Boolean = prefs.getBoolean(KEY_APP_LOCK_PW_GENERATED, false)

    fun setAppLockPasswordGenerated(generated: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_PW_GENERATED, generated).apply()
    }

    fun storeAppLockPassword(password: String) {
        storeEncrypted(password, KEY_APP_LOCK_PASSWORD, KEY_APP_LOCK_PASSWORD_IV)
    }

    fun retrieveAppLockPassword(): String? {
        return retrieveEncrypted(KEY_APP_LOCK_PASSWORD, KEY_APP_LOCK_PASSWORD_IV)
    }

    fun clearAppLock() {
        prefs.edit()
            .remove(KEY_APP_LOCK_PASSWORD)
            .remove(KEY_APP_LOCK_PASSWORD_IV)
            .putBoolean(KEY_APP_LOCK_ENABLED, false)
            .putBoolean(KEY_APP_LOCK_PW_GENERATED, false)
            .apply()
    }

    // --- Legacy migration helpers (v1/v2 → v3) ---

    fun isV3Migrated(): Boolean = prefs.getBoolean(KEY_V3_MIGRATED, false)

    fun setV3Migrated() {
        prefs.edit().putBoolean(KEY_V3_MIGRATED, true).apply()
    }

    fun hasLegacyPassword(): Boolean = prefs.contains(KEY_LEGACY_PASSWORD)

    fun retrieveLegacyPassword(): String? = retrieveEncrypted(KEY_LEGACY_PASSWORD, KEY_LEGACY_IV)

    fun isLegacyBiometricEnabled(): Boolean = prefs.getBoolean(KEY_LEGACY_BIOMETRIC, true)

    fun isLegacyPasswordGenerated(): Boolean = prefs.getBoolean(KEY_LEGACY_PW_GENERATED, false)

    fun clearLegacyKeys() {
        prefs.edit()
            .remove(KEY_LEGACY_PASSWORD)
            .remove(KEY_LEGACY_IV)
            .remove(KEY_LEGACY_ENCRYPTION_ENABLED)
            .remove(KEY_LEGACY_BIOMETRIC)
            .remove(KEY_LEGACY_PW_GENERATED)
            .apply()
    }

    // --- Internal encryption helpers ---

    private fun storeEncrypted(value: String, keyData: String, keyIv: String) {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val bytes = value.toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(bytes)
        val iv = cipher.iv

        Arrays.fill(bytes, 0.toByte())

        prefs.edit()
            .putString(keyData, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(keyIv, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    private fun retrieveEncrypted(keyData: String, keyIv: String): String? {
        val encryptedB64 = prefs.getString(keyData, null) ?: return null
        val ivB64 = prefs.getString(keyIv, null) ?: return null

        return try {
            val encrypted = Base64.decode(encryptedB64, Base64.NO_WRAP)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)

            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(AES_GCM_TAG_LENGTH, iv))

            val decrypted = cipher.doFinal(encrypted)
            val result = String(decrypted, Charsets.UTF_8)
            Arrays.fill(decrypted, 0.toByte())
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun generateRandomPassphrase(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&*+-=?"
        val random = SecureRandom()
        return (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    // --- KeyStore key management (StrongBox preferred) ---

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        keyStore.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )

        // Try StrongBox (dedicated secure element) on API 28+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                keyGenerator.init(
                    KeyGenParameterSpec.Builder(
                        KEYSTORE_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setIsStrongBoxBacked(true)
                        .build()
                )
                return keyGenerator.generateKey()
            } catch (_: Exception) {
                // StrongBox unavailable, fall through to TEE
            }
        }

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
