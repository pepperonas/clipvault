package io.celox.clipvault.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ClipRepository(
    private val dao: ClipDao
) {

    // Serialize insert operations to prevent TOCTOU race conditions
    private val insertMutex = Mutex()

    // Track recently deleted content to prevent re-insertion by clipboard polling
    @Volatile
    private var recentlyDeletedContent: String? = null

    @Volatile
    private var recentlyDeletedTimestamp: Long = 0L

    companion object {
        const val DELETE_COOLDOWN_MS = 10_000L // 10 seconds
        const val SKIPPED_COOLDOWN = -2L
    }

    val allEntries: Flow<List<ClipEntry>> = dao.getAllEntries()

    fun search(query: String): Flow<List<ClipEntry>> = dao.search(query)

    suspend fun getLatestEntry(): ClipEntry? = dao.getLatestEntry()

    suspend fun getCount(): Int = dao.getCount()

    suspend fun insert(content: String): Long = insertMutex.withLock {
        // Skip re-insertion of recently deleted content
        val deleted = recentlyDeletedContent
        if (deleted == content && System.currentTimeMillis() - recentlyDeletedTimestamp < DELETE_COOLDOWN_MS) {
            return@withLock SKIPPED_COOLDOWN
        }

        // Avoid duplicates: don't insert if last entry has same content
        val latest = dao.getLatestEntry()
        if (latest?.content == content) return@withLock latest.id

        // Insert new entry, then remove older duplicates
        val id = dao.insert(ClipEntry(content = content))
        dao.deleteOlderDuplicates(content, id)
        id
    }

    suspend fun togglePin(entry: ClipEntry) {
        dao.update(entry.copy(pinned = !entry.pinned))
    }

    suspend fun delete(entry: ClipEntry) {
        recentlyDeletedContent = entry.content
        recentlyDeletedTimestamp = System.currentTimeMillis()
        dao.delete(entry)
    }

    suspend fun reInsert(entry: ClipEntry) {
        // Clear delete cooldown so undo works
        recentlyDeletedContent = null
        recentlyDeletedTimestamp = 0L
        dao.insert(entry)
    }

    suspend fun deleteAllUnpinned() {
        // Set cooldown for all content currently on clipboard
        val latest = dao.getLatestEntry()
        if (latest != null && !latest.pinned) {
            recentlyDeletedContent = latest.content
            recentlyDeletedTimestamp = System.currentTimeMillis()
        }
        dao.deleteAllUnpinned()
    }
}
