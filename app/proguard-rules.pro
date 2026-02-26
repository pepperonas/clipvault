# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# LicenseManager (protect obfuscated secret)
-keep class io.celox.clipvault.licensing.LicenseManager { *; }
