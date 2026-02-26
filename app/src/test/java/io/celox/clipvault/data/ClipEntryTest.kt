package io.celox.clipvault.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipEntryTest {

    @Test
    fun `default id is zero`() {
        val entry = ClipEntry(content = "test")
        assertEquals(0L, entry.id)
    }

    @Test
    fun `default pinned is false`() {
        val entry = ClipEntry(content = "test")
        assertFalse(entry.pinned)
    }

    @Test
    fun `timestamp is set to current time by default`() {
        val before = System.currentTimeMillis()
        val entry = ClipEntry(content = "test")
        val after = System.currentTimeMillis()
        assertTrue(entry.timestamp in before..after)
    }

    @Test
    fun `explicit values are preserved`() {
        val entry = ClipEntry(
            id = 42,
            content = "Hello World",
            timestamp = 1000L,
            pinned = true
        )
        assertEquals(42L, entry.id)
        assertEquals("Hello World", entry.content)
        assertEquals(1000L, entry.timestamp)
        assertTrue(entry.pinned)
    }

    @Test
    fun `copy toggles pinned`() {
        val entry = ClipEntry(content = "test", pinned = false)
        val toggled = entry.copy(pinned = !entry.pinned)
        assertTrue(toggled.pinned)
        assertEquals(entry.content, toggled.content)
    }
}
