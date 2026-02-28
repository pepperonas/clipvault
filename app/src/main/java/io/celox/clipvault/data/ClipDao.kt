package io.celox.clipvault.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {

    @Query("SELECT * FROM clip_entries ORDER BY pinned DESC, timestamp DESC")
    fun getAllEntries(): Flow<List<ClipEntry>>

    @Query("SELECT * FROM clip_entries WHERE content LIKE '%' || :query || '%' ORDER BY pinned DESC, timestamp DESC")
    fun search(query: String): Flow<List<ClipEntry>>

    @Query("SELECT * FROM clip_entries ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEntry(): ClipEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClipEntry): Long

    @Update
    suspend fun update(entry: ClipEntry)

    @Delete
    suspend fun delete(entry: ClipEntry)

    @Query("DELETE FROM clip_entries WHERE pinned = 0")
    suspend fun deleteAllUnpinned()

    @Query("DELETE FROM clip_entries WHERE content = :content AND id != :excludeId")
    suspend fun deleteOlderDuplicates(content: String, excludeId: Long)

    @Query("SELECT COUNT(*) FROM clip_entries")
    suspend fun getCount(): Int

    @Query("SELECT * FROM clip_entries ORDER BY timestamp DESC")
    suspend fun getAllEntriesSnapshot(): List<ClipEntry>

    @Query("SELECT * FROM clip_entries WHERE content = :content AND timestamp = :timestamp LIMIT 1")
    suspend fun findByContentAndTimestamp(content: String, timestamp: Long): ClipEntry?
}
