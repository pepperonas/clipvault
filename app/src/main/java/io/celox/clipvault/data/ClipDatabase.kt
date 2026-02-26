package io.celox.clipvault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.sqlcipher.database.SupportFactory

@Database(entities = [ClipEntry::class], version = 1, exportSchema = false)
abstract class ClipDatabase : RoomDatabase() {

    abstract fun clipDao(): ClipDao

    companion object {
        const val DB_NAME = "clipvault.db"

        @Volatile
        private var INSTANCE: ClipDatabase? = null

        fun getInstance(context: Context, passphrase: ByteArray): ClipDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildEncryptedDatabase(context, passphrase).also { INSTANCE = it }
            }
        }

        fun closeAndReset() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        private fun buildEncryptedDatabase(context: Context, passphrase: ByteArray): ClipDatabase {
            val factory: SupportSQLiteOpenHelper.Factory = SupportFactory(passphrase, null, false)
            return Room.databaseBuilder(
                context.applicationContext,
                ClipDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .build()
        }
    }
}
