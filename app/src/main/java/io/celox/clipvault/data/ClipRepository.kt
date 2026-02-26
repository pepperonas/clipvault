package io.celox.clipvault.data

import io.celox.clipvault.licensing.LicenseManager
import kotlinx.coroutines.flow.Flow

class ClipRepository(
    private val dao: ClipDao,
    private val licenseManager: LicenseManager? = null
) {

    val allEntries: Flow<List<ClipEntry>> = dao.getAllEntries()

    fun search(query: String): Flow<List<ClipEntry>> = dao.search(query)

    suspend fun getLatestEntry(): ClipEntry? = dao.getLatestEntry()

    suspend fun getCount(): Int = dao.getCount()

    suspend fun insert(content: String): Long {
        // Check clip limit for unlicensed users
        if (licenseManager != null && !licenseManager.isActivated()) {
            val count = dao.getCount()
            if (count >= LicenseManager.getMaxFreeClips()) return -1
        }

        // Avoid duplicates: don't insert if last entry has same content
        val latest = dao.getLatestEntry()
        if (latest?.content == content) return latest.id

        // Insert new entry, then remove older duplicates
        val id = dao.insert(ClipEntry(content = content))
        dao.deleteOlderDuplicates(content, id)
        return id
    }

    suspend fun togglePin(entry: ClipEntry) {
        dao.update(entry.copy(pinned = !entry.pinned))
    }

    suspend fun delete(entry: ClipEntry) = dao.delete(entry)

    suspend fun deleteAllUnpinned() = dao.deleteAllUnpinned()
}
