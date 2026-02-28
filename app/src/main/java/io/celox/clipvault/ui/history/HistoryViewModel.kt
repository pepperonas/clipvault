package io.celox.clipvault.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.celox.clipvault.data.ClipEntry
import io.celox.clipvault.data.ClipRepository
import io.celox.clipvault.util.ContentType
import io.celox.clipvault.util.SortOrder
import io.celox.clipvault.util.detectContentType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: ClipRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked

    private val _selectedContentType = MutableStateFlow<ContentType?>(null)
    val selectedContentType: StateFlow<ContentType?> = _selectedContentType

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    // --- Batch selection ---
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    @OptIn(ExperimentalCoroutinesApi::class)
    private val unfilteredEntries: StateFlow<List<ClipEntry>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) repository.allEntries
            else repository.search(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val entries: StateFlow<List<ClipEntry>> = combine(
        unfilteredEntries,
        _selectedContentType,
        _sortOrder
    ) { entries, type, sort ->
        val filtered = if (type == null) entries
        else entries.filter { detectContentType(it.content) == type }

        // Separate pinned and non-pinned, sort non-pinned
        val pinned = filtered.filter { it.pinned }
        val nonPinned = filtered.filter { !it.pinned }

        val sortedNonPinned = when (sort) {
            SortOrder.NEWEST -> nonPinned.sortedByDescending { it.timestamp }
            SortOrder.OLDEST -> nonPinned.sortedBy { it.timestamp }
            SortOrder.A_Z -> nonPinned.sortedBy { it.content.lowercase() }
            SortOrder.Z_A -> nonPinned.sortedByDescending { it.content.lowercase() }
            SortOrder.LONGEST -> nonPinned.sortedByDescending { it.content.length }
            SortOrder.SHORTEST -> nonPinned.sortedBy { it.content.length }
        }

        pinned + sortedNonPinned
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contentTypeCounts: StateFlow<Map<ContentType, Int>> = unfilteredEntries
        .map { entries ->
            entries.groupBy { detectContentType(it.content) }
                .mapValues { it.value.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setContentTypeFilter(type: ContentType?) {
        _selectedContentType.value = type
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun unlock() {
        _isUnlocked.value = true
    }

    fun lock() {
        _isUnlocked.value = false
    }

    fun togglePin(entry: ClipEntry) {
        viewModelScope.launch { repository.togglePin(entry) }
    }

    fun delete(entry: ClipEntry) {
        repository.setDeleteCooldown(entry.content)
        viewModelScope.launch { repository.delete(entry) }
    }

    fun reInsert(entry: ClipEntry) {
        viewModelScope.launch { repository.reInsert(entry) }
    }

    fun deleteAllUnpinned() {
        // Set cooldown for the latest entry to prevent re-insertion by polling
        val latest = entries.value.firstOrNull { !it.pinned }
        if (latest != null) {
            repository.setDeleteCooldown(latest.content)
        }
        viewModelScope.launch { repository.deleteAllUnpinned() }
    }

    // --- Batch operations ---

    fun enterSelectionMode(firstId: Long) {
        _selectionMode.value = true
        _selectedIds.value = setOf(firstId)
    }

    fun exitSelectionMode() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(id: Long) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    fun selectAll() {
        _selectedIds.value = entries.value.map { it.id }.toSet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        // Set cooldown for clipboard content
        entries.value.firstOrNull { it.id in _selectedIds.value }?.let {
            repository.setDeleteCooldown(it.content)
        }
        viewModelScope.launch { repository.deleteBatch(ids) }
        exitSelectionMode()
    }

    fun pinSelected() {
        val entriesToPin = entries.value.filter { it.id in _selectedIds.value && !it.pinned }
        viewModelScope.launch { entriesToPin.forEach { repository.togglePin(it) } }
        exitSelectionMode()
    }

    fun unpinSelected() {
        val entriesToUnpin = entries.value.filter { it.id in _selectedIds.value && it.pinned }
        viewModelScope.launch { entriesToUnpin.forEach { repository.togglePin(it) } }
        exitSelectionMode()
    }

    class Factory(private val repository: ClipRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(repository) as T
        }
    }
}
