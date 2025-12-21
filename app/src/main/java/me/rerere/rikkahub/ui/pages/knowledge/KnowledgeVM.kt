package me.rerere.rikkahub.ui.pages.knowledge

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.KnowledgeBase
import me.rerere.rikkahub.data.model.KnowledgeItem
import me.rerere.rikkahub.data.model.KnowledgeSearchResult
import me.rerere.rikkahub.data.model.ProcessingStatus
import me.rerere.rikkahub.data.repository.KnowledgeRepository
import me.rerere.rikkahub.service.KnowledgeService
import me.rerere.rikkahub.worker.KnowledgeProcessingWorker
import java.io.File
import kotlin.uuid.Uuid

class KnowledgeVM(
    private val repository: KnowledgeRepository,
    private val knowledgeService: KnowledgeService,
    private val settingsStore: SettingsStore,
    private val context: Application,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(init = true, providers = emptyList()))

    val knowledgeBases = repository.getAllBases()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedBaseId = MutableStateFlow<Uuid?>(null)
    val selectedBaseId = _selectedBaseId.asStateFlow()

    private val _currentItems = MutableStateFlow<List<KnowledgeItem>>(emptyList())
    val currentItems = _currentItems.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _searchResults = MutableStateFlow<List<KnowledgeSearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    fun selectBase(id: Uuid?) {
        _selectedBaseId.value = id
        if (id != null) {
            loadItems(id)
        } else {
            _currentItems.value = emptyList()
        }
    }

    private fun loadItems(baseId: Uuid) {
        viewModelScope.launch {
            repository.getItemsByBaseId(baseId).collect { items ->
                _currentItems.value = items
            }
        }
    }

    fun createBase(base: KnowledgeBase) {
        viewModelScope.launch {
            repository.insertBase(base)
        }
    }

    fun updateBase(base: KnowledgeBase) {
        viewModelScope.launch {
            repository.updateBase(base)
        }
    }

    fun deleteBase(id: Uuid) {
        viewModelScope.launch {
            repository.deleteBase(id)
            if (_selectedBaseId.value == id) {
                _selectedBaseId.value = null
                _currentItems.value = emptyList()
            }
        }
    }

    fun addFile(baseId: Uuid, file: File) {
        viewModelScope.launch {
            val result = knowledgeService.addFile(baseId, file)
            result.onSuccess { item ->
                // Schedule background processing
                KnowledgeProcessingWorker.enqueue(context, item.id, baseId)
            }
            loadItems(baseId)
        }
    }

    fun addNote(baseId: Uuid, name: String, content: String) {
        viewModelScope.launch {
            val result = knowledgeService.addNote(baseId, name, content)
            result.onSuccess { item ->
                // Schedule background processing
                KnowledgeProcessingWorker.enqueue(context, item.id, baseId)
            }
            loadItems(baseId)
        }
    }

    fun addUrl(baseId: Uuid, url: String, name: String? = null) {
        viewModelScope.launch {
            val result = knowledgeService.addUrl(baseId, url, name)
            result.onSuccess { item ->
                // Schedule background processing
                KnowledgeProcessingWorker.enqueue(context, item.id, baseId)
            }
            loadItems(baseId)
        }
    }

    fun updateNote(itemId: Uuid, baseId: Uuid, name: String, content: String) {
        viewModelScope.launch {
            val result = knowledgeService.updateNote(itemId, name, content)
            result.onSuccess { item ->
                // Schedule background processing
                KnowledgeProcessingWorker.enqueue(context, item.id, baseId)
            }
            loadItems(baseId)
        }
    }

    fun updateUrl(itemId: Uuid, baseId: Uuid, url: String, name: String? = null) {
        viewModelScope.launch {
            val result = knowledgeService.updateUrl(itemId, url, name)
            result.onSuccess { item ->
                // Schedule background processing
                KnowledgeProcessingWorker.enqueue(context, item.id, baseId)
            }
            loadItems(baseId)
        }
    }

    fun removeItem(itemId: Uuid) {
        viewModelScope.launch {
            knowledgeService.removeItem(itemId)
            _selectedBaseId.value?.let { loadItems(it) }
        }
    }

    fun processItem(item: KnowledgeItem) {
        // Use WorkManager for background processing
        KnowledgeProcessingWorker.enqueue(context, item.id, item.baseId)
    }

    fun processAllPending(baseId: Uuid) {
        val pendingItems = _currentItems.value
            .filter { it.status == ProcessingStatus.PENDING || it.status == ProcessingStatus.FAILED }
            .map { it.id }

        KnowledgeProcessingWorker.enqueueAllPending(context, baseId, pendingItems)
    }

    fun search(baseId: Uuid, query: String) {
        viewModelScope.launch {
            val results = knowledgeService.search(baseId, query)
            _searchResults.value = results
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    fun getItemsByStatus(status: ProcessingStatus): List<KnowledgeItem> {
        return _currentItems.value.filter { it.status == status }
    }
}
