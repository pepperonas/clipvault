package io.celox.clipvault.data

import android.content.Context
import android.util.Log
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

object DatabaseMigrationHelper {

    private const val TAG = "DBMigration"
    private const val DB_NAME = "clipvault.db"

    /**
     * Migrates an existing unencrypted Room DB to an encrypted SQLCipher DB.
     * No-op if the unencrypted DB doesn't exist or is already encrypted.
     */
    fun migrateIfNeeded(context: Context, passphrase: ByteArray) {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            Log.d(TAG, "No existing DB found, skipping encryption migration")
            return
        }

        if (!isUnencrypted(dbFile)) {
            Log.d(TAG, "DB is already encrypted or empty, skipping encryption migration")
            return
        }

        Log.i(TAG, "Migrating unencrypted DB to encrypted")
        val tempFile = File(dbFile.parentFile, "clipvault_encrypted.db")

        try {
            SQLiteDatabase.loadLibs(context)

            val unencryptedDb = SQLiteDatabase.openDatabase(
                dbFile.absolutePath, "", null, SQLiteDatabase.OPEN_READWRITE
            )

            val passphraseStr = String(passphrase, Charsets.UTF_8).replace("'", "''")
            unencryptedDb.rawExecSQL("ATTACH DATABASE '${tempFile.absolutePath}' AS encrypted KEY '$passphraseStr'")
            unencryptedDb.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            unencryptedDb.rawExecSQL("DETACH DATABASE encrypted")
            unencryptedDb.close()

            dbFile.delete()
            tempFile.renameTo(dbFile)

            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()

            Log.i(TAG, "Encryption migration complete")
        } catch (e: Exception) {
            Log.e(TAG, "Encryption migration failed", e)
            tempFile.delete()
        }
    }

    /**
     * Re-encrypts an existing encrypted DB from oldPassphrase to newPassphrase.
     * Used for migrating from user-set password (v1/v2) to auto-generated passphrase (v3).
     */
    fun reEncryptDatabase(context: Context, oldPassphrase: ByteArray, newPassphrase: ByteArray) {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            Log.d(TAG, "No existing DB found, skipping re-encryption")
            return
        }

        Log.i(TAG, "Re-encrypting DB with new passphrase")
        val tempFile = File(dbFile.parentFile, "clipvault_reencrypt.db")

        try {
            SQLiteDatabase.loadLibs(context)

            val oldStr = String(oldPassphrase, Charsets.UTF_8).replace("'", "''")
            val newStr = String(newPassphrase, Charsets.UTF_8).replace("'", "''")

            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath, oldStr, null, SQLiteDatabase.OPEN_READWRITE
            )

            db.rawExecSQL("ATTACH DATABASE '${tempFile.absolutePath}' AS reencrypted KEY '$newStr'")
            db.rawExecSQL("SELECT sqlcipher_export('reencrypted')")
            db.rawExecSQL("DETACH DATABASE reencrypted")
            db.close()

            dbFile.delete()
            tempFile.renameTo(dbFile)

            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()

            Log.i(TAG, "Re-encryption complete")
        } catch (e: Exception) {
            Log.e(TAG, "Re-encryption failed", e)
            tempFile.delete()
            throw e
        }
    }

    private fun isUnencrypted(dbFile: File): Boolean {
        return try {
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath, "", null, SQLiteDatabase.OPEN_READONLY
            )
            db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
                cursor.moveToFirst()
            }
            db.close()
            true
        } catch (_: Exception) {
            false
        }
    }
}
