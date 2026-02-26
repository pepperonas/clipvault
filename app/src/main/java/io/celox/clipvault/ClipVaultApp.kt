package io.celox.clipvault

import android.app.Application
import android.util.Log
import io.celox.clipvault.data.ClipDatabase
import io.celox.clipvault.data.ClipRepository
import io.celox.clipvault.data.DatabaseMigrationHelper
import io.celox.clipvault.security.KeyStoreManager
import net.sqlcipher.database.SQLiteDatabase
import java.util.Arrays

class ClipVaultApp : Application() {

    companion object {
        private const val TAG = "ClipVaultApp"
    }

    lateinit var keyStoreManager: KeyStoreManager
        private set

    var database: ClipDatabase? = null
        private set
    var repository: ClipRepository? = null
        private set

    val isAppLockEnabled: Boolean
        get() = keyStoreManager.isAppLockEnabled()

    override fun onCreate() {
        super.onCreate()
        SQLiteDatabase.loadLibs(this)
        keyStoreManager = KeyStoreManager(this)

        if (!keyStoreManager.isV3Migrated()) {
            migrateToV3()
        }

        openDatabase()
    }

    /**
     * Migrates from v1/v2 (user-managed encryption) to v3 (always-encrypted with auto passphrase).
     * Handles three cases:
     * 1. DB encrypted with user password → adopt that password as the DB passphrase (no re-encryption)
     * 2. Plain DB → encrypt with auto passphrase
     * 3. No DB → nothing to migrate
     */
    private fun migrateToV3() {
        val dbFile = getDatabasePath(ClipDatabase.DB_NAME)

        try {
            if (keyStoreManager.hasLegacyPassword()) {
                // v1 or v2 with encryption: DB is encrypted with user password.
                // Adopt the existing password as the DB passphrase — no re-encryption needed.
                val legacyPassword = keyStoreManager.retrieveLegacyPassword()

                if (legacyPassword != null) {
                    Log.i(TAG, "Adopting legacy password as DB passphrase")
                    keyStoreManager.setDbPassphrase(legacyPassword)

                    // Preserve app lock from old encryption settings
                    keyStoreManager.storeAppLockPassword(legacyPassword)
                    keyStoreManager.setAppLockEnabled(true)
                    keyStoreManager.setAppLockBiometricEnabled(keyStoreManager.isLegacyBiometricEnabled())
                    keyStoreManager.setAppLockPasswordGenerated(keyStoreManager.isLegacyPasswordGenerated())
                    Log.i(TAG, "App lock migrated from legacy encryption settings")
                }

                keyStoreManager.clearLegacyKeys()
            } else if (dbFile.exists()) {
                // v2 without encryption: plain DB → encrypt with auto passphrase
                Log.i(TAG, "Migrating plain DB to auto-encrypted")
                val autoPassphrase = keyStoreManager.getOrCreateDbPassphrase()
                val autoBytes = autoPassphrase.toByteArray(Charsets.UTF_8)
                DatabaseMigrationHelper.migrateIfNeeded(this, autoBytes)
                Arrays.fill(autoBytes, 0.toByte())
            }
            // else: fresh install, no DB yet — getOrCreateDbPassphrase() handles it
        } catch (e: Exception) {
            Log.e(TAG, "V3 migration failed", e)
            return // Don't set migrated flag, will retry next launch
        }

        keyStoreManager.setV3Migrated()
    }

    /**
     * Opens the always-encrypted database with the auto-generated passphrase.
     */
    fun openDatabase() {
        if (database != null) return

        val passphrase = keyStoreManager.getOrCreateDbPassphrase()
        val passphraseBytes = passphrase.toByteArray(Charsets.UTF_8)

        try {
            val db = ClipDatabase.getInstance(this, passphraseBytes)
            database = db
            repository = ClipRepository(db.clipDao())
            Log.i(TAG, "Database opened successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open database", e)
        } finally {
            Arrays.fill(passphraseBytes, 0.toByte())
        }
    }
}
