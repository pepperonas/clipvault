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
     * Migrates an existing encrypted SQLCipher DB to an unencrypted plain DB.
     * No-op if the DB doesn't exist or is already unencrypted.
     */
    fun decryptIfNeeded(context: Context, passphrase: ByteArray) {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            Log.d(TAG, "No existing DB found, skipping decryption migration")
            return
        }

        if (isUnencrypted(dbFile)) {
            Log.d(TAG, "DB is already unencrypted, skipping decryption migration")
            return
        }

        Log.i(TAG, "Migrating encrypted DB to plain")
        val tempFile = File(dbFile.parentFile, "clipvault_plain.db")

        try {
            SQLiteDatabase.loadLibs(context)

            val passphraseStr = String(passphrase, Charsets.UTF_8).replace("'", "''")
            val encryptedDb = SQLiteDatabase.openDatabase(
                dbFile.absolutePath, passphraseStr, null, SQLiteDatabase.OPEN_READWRITE
            )

            encryptedDb.rawExecSQL("ATTACH DATABASE '${tempFile.absolutePath}' AS plaintext KEY ''")
            encryptedDb.rawExecSQL("SELECT sqlcipher_export('plaintext')")
            encryptedDb.rawExecSQL("DETACH DATABASE plaintext")
            encryptedDb.close()

            dbFile.delete()
            tempFile.renameTo(dbFile)

            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()

            Log.i(TAG, "Decryption migration complete")
        } catch (e: Exception) {
            Log.e(TAG, "Decryption migration failed", e)
            tempFile.delete()
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
