package io.celox.clipvault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clip_entries")
data class ClipEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val pinned: Boolean = false
)
