package io.celox.clipvault.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ClipRepositoryTest {

    private lateinit var dao: ClipDao
    private lateinit var repository: ClipRepository

    @Before
    fun setup() {
        dao = mock()
        repository = ClipRepository(dao)
    }

    @Test
    fun `insert new content returns new id`() = runTest {
        whenever(dao.getLatestEntry()).thenReturn(null)
        whenever(dao.insert(any())).thenReturn(1L)

        val id = repository.insert("Hello")
        assertEquals(1L, id)
        verify(dao).insert(any())
        verify(dao).deleteOlderDuplicates("Hello", 1L)
    }

    @Test
    fun `insert duplicate content returns existing id`() = runTest {
        val existing = ClipEntry(id = 5, content = "Hello")
        whenever(dao.getLatestEntry()).thenReturn(existing)

        val id = repository.insert("Hello")
        assertEquals(5L, id)
        verify(dao, never()).insert(any())
    }

    @Test
    fun `insert different content does not deduplicate`() = runTest {
        val existing = ClipEntry(id = 5, content = "Hello")
        whenever(dao.getLatestEntry()).thenReturn(existing)
        whenever(dao.insert(any())).thenReturn(6L)

        val id = repository.insert("World")
        assertEquals(6L, id)
        verify(dao).insert(any())
    }

    @Test
    fun `setDeleteCooldown prevents re-insert`() = runTest {
        val entry = ClipEntry(id = 1, content = "DeleteMe")
        whenever(dao.getLatestEntry()).thenReturn(null)
        whenever(dao.insert(any())).thenReturn(2L)

        // First insert works
        val id1 = repository.insert("DeleteMe")
        assertNotEquals(ClipRepository.SKIPPED_COOLDOWN, id1)

        // Set cooldown before delete (as ViewModel does)
        repository.setDeleteCooldown(entry.content)
        repository.delete(entry)
        verify(dao).delete(entry)

        // Re-insert within cooldown is skipped
        val id2 = repository.insert("DeleteMe")
        assertEquals(ClipRepository.SKIPPED_COOLDOWN, id2)
    }

    @Test
    fun `reInsert clears cooldown`() = runTest {
        val entry = ClipEntry(id = 1, content = "UndoMe")
        whenever(dao.getLatestEntry()).thenReturn(null)
        whenever(dao.insert(any())).thenReturn(2L)

        // Set cooldown and delete
        repository.setDeleteCooldown(entry.content)
        repository.delete(entry)
        val skipped = repository.insert("UndoMe")
        assertEquals(ClipRepository.SKIPPED_COOLDOWN, skipped)

        // reInsert clears cooldown
        repository.reInsert(entry)

        // Now insert should work again
        val id = repository.insert("UndoMe")
        assertNotEquals(ClipRepository.SKIPPED_COOLDOWN, id)
    }

    @Test
    fun `deleteAllUnpinned with cooldown blocks re-insert`() = runTest {
        val latest = ClipEntry(id = 3, content = "Latest", pinned = false)
        whenever(dao.getLatestEntry()).thenReturn(latest)

        // Set cooldown before deleteAll (as ViewModel does)
        repository.setDeleteCooldown(latest.content)
        repository.deleteAllUnpinned()
        verify(dao).deleteAllUnpinned()

        // Re-insert of deleted content is blocked
        val id = repository.insert("Latest")
        assertEquals(ClipRepository.SKIPPED_COOLDOWN, id)
    }

    @Test
    fun `deleteAllUnpinned does not set cooldown for pinned latest`() = runTest {
        val latest = ClipEntry(id = 3, content = "Pinned", pinned = true)
        whenever(dao.getLatestEntry()).thenReturn(latest)
        whenever(dao.insert(any())).thenReturn(4L)

        repository.deleteAllUnpinned()

        // Re-insert should work (no cooldown for pinned)
        val id = repository.insert("Pinned")
        assertNotEquals(ClipRepository.SKIPPED_COOLDOWN, id)
    }

    @Test
    fun `togglePin updates entry with toggled pinned state`() = runTest {
        val entry = ClipEntry(id = 1, content = "test", pinned = false)
        repository.togglePin(entry)
        verify(dao).update(entry.copy(pinned = true))
    }
}
