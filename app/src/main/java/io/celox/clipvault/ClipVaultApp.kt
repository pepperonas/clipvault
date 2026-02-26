package io.celox.clipvault

import android.app.Application
import android.util.Log
import io.celox.clipvault.data.ClipDatabase
import io.celox.clipvault.data.ClipRepository
import io.celox.clipvault.data.DatabaseMigrationHelper
import io.celox.clipvault.licensing.LicenseManager
import io.celox.clipvault.security.KeyStoreManager
import net.sqlcipher.database.SQLiteDatabase

class ClipVaultApp : Application() {

    companion object {
        private const val TAG = "ClipVaultApp"
    }

    lateinit var keyStoreManager: KeyStoreManager
        private set
    lateinit var licenseManager: LicenseManager
        private set

    var database: ClipDatabase? = null
        private set
    var repository: ClipRepository? = null
        private set

    val isEncryptionEnabled: Boolean
        get() = keyStoreManager.isEncryptionEnabled()

    override fun onCreate() {
        super.onCreate()
        SQLiteDatabase.loadLibs(this)
        keyStoreManager = KeyStoreManager(this)
        licenseManager = LicenseManager(keyStoreManager)
        openDatabase()
    }

    /**
     * Opens the database in the appropriate mode (plain or encrypted).
     * Always results in an open DB after this call.
     */
    fun openDatabase() {
        if (database != null) return

        if (keyStoreManager.isEncryptionEnabled()) {
            val passphrase = keyStoreManager.retrievePassword()
            if (passphrase != null) {
                openEncryptedDatabase(passphrase)
            } else {
                // Encryption enabled but no password — fall back to plain
                Log.w(TAG, "Encryption enabled but no password found, opening plain DB")
                keyStoreManager.setEncryptionEnabled(false)
                openPlainDatabase()
            }
        } else {
            openPlainDatabase()
        }
    }

    /**
     * Enables encryption: closes current DB, stores password, migrates to encrypted, reopens.
     */
    fun enableEncryption(passphrase: String): Boolean {
        return try {
            ClipDatabase.closeAndReset()
            database = null
            repository = null

            keyStoreManager.storePassword(passphrase)

            val passphraseBytes = passphrase.toByteArray(Charsets.UTF_8)
            DatabaseMigrationHelper.migrateIfNeeded(this, passphraseBytes)

            keyStoreManager.setEncryptionEnabled(true)
            openEncryptedDatabase(passphrase)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable encryption", e)
            false
        }
    }

    /**
     * Disables encryption: closes current DB, decrypts, removes password, reopens plain.
     */
    fun disableEncryption(): Boolean {
        return try {
            val passphrase = keyStoreManager.retrievePassword() ?: return false
            val passphraseBytes = passphrase.toByteArray(Charsets.UTF_8)

            ClipDatabase.closeAndReset()
            database = null
            repository = null

            DatabaseMigrationHelper.decryptIfNeeded(this, passphraseBytes)
            keyStoreManager.clearPassword()

            openPlainDatabase()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable encryption", e)
            false
        }
    }

    /**
     * Changes the encryption password: decrypt with old, re-encrypt with new.
     */
    fun changePassword(oldPassword: String, newPassword: String): Boolean {
        return try {
            val storedPassword = keyStoreManager.retrievePassword()
            if (storedPassword != oldPassword) return false

            val oldBytes = oldPassword.toByteArray(Charsets.UTF_8)

            ClipDatabase.closeAndReset()
            database = null
            repository = null

            // Decrypt with old password
            DatabaseMigrationHelper.decryptIfNeeded(this, oldBytes)

            // Store new password and re-encrypt
            keyStoreManager.storePassword(newPassword)
            val newBytes = newPassword.toByteArray(Charsets.UTF_8)
            DatabaseMigrationHelper.migrateIfNeeded(this, newBytes)

            openEncryptedDatabase(newPassword)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change password", e)
            false
        }
    }

    private fun openPlainDatabase() {
        try {
            val db = ClipDatabase.getInstance(this)
            database = db
            repository = ClipRepository(db.clipDao(), licenseManager)
            Log.i(TAG, "Plain database opened successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open plain database", e)
        }
    }

    private fun openEncryptedDatabase(passphrase: String) {
        try {
            val passphraseBytes = passphrase.toByteArray(Charsets.UTF_8)
            val db = ClipDatabase.getInstance(this, passphraseBytes)
            database = db
            repository = ClipRepository(db.clipDao(), licenseManager)
            Log.i(TAG, "Encrypted database opened successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open encrypted database", e)
        }
    }
}
