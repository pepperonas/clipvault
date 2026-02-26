package io.celox.clipvault.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.celox.clipvault.data.ClipEntry
import io.celox.clipvault.data.ClipRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: ClipRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<ClipEntry>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) repository.allEntries
            else repository.search(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        viewModelScope.launch { repository.delete(entry) }
    }

    fun reInsert(entry: ClipEntry) {
        viewModelScope.launch { repository.reInsert(entry) }
    }

    fun deleteAllUnpinned() {
        viewModelScope.launch { repository.deleteAllUnpinned() }
    }

    class Factory(private val repository: ClipRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(repository) as T
        }
    }
}
