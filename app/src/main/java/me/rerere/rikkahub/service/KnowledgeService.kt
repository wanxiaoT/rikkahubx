package me.rerere.rikkahub.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.EmbeddingProvider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.providers.OpenAIProvider
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.knowledge.VectorUtils
import me.rerere.rikkahub.data.knowledge.chunker.TextChunker
import me.rerere.rikkahub.data.knowledge.loader.DocumentLoaderFactory
import me.rerere.rikkahub.data.knowledge.loader.NoteLoader
import me.rerere.rikkahub.data.knowledge.loader.UrlLoader
import me.rerere.rikkahub.data.model.KnowledgeBase
import me.rerere.rikkahub.data.model.KnowledgeChunk
import me.rerere.rikkahub.data.model.KnowledgeItem
import me.rerere.rikkahub.data.model.KnowledgeItemType
import me.rerere.rikkahub.data.model.KnowledgeSearchResult
import me.rerere.rikkahub.data.model.ProcessingStatus
import me.rerere.rikkahub.data.repository.KnowledgeRepository
import okhttp3.OkHttpClient
import java.io.File
import kotlin.uuid.Uuid

private const val TAG = "KnowledgeService"

class KnowledgeService(
    private val context: Context,
    private val repository: KnowledgeRepository,
    private val settingsStore: SettingsStore,
    private val client: OkHttpClient,
) {
    private val openAIProvider = OpenAIProvider(client)

    /**
     * Process a pending knowledge item: load document, chunk, embed, and store
     */
    suspend fun processItem(item: KnowledgeItem): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Update status to processing
            repository.updateItemStatus(item.id, ProcessingStatus.PROCESSING)

            // Get the knowledge base configuration
            val base = repository.getBaseByIdSync(item.baseId)
                ?: return@withContext Result.failure(Exception("Knowledge base not found"))

            // Get provider settings
            val settings = settingsStore.settingsFlow.first()
            val providerSetting = settings.providers.find { it.id == base.embeddingProviderId }
                ?: return@withContext Result.failure(Exception("Embedding provider not found"))

            // Load document content
            val documentChunks = when (item.type) {
                KnowledgeItemType.FILE -> {
                    val file = item.filePath?.let { File(it) }
                    if (file == null || !file.exists()) {
                        return@withContext Result.failure(Exception("File not found: ${item.filePath}"))
                    }
                    val loader = DocumentLoaderFactory.createLoader(file)
                        ?: return@withContext Result.failure(Exception("Unsupported file type: ${file.extension}"))
                    loader.load()
                }
                KnowledgeItemType.NOTE -> {
                    val content = item.content ?: return@withContext Result.failure(Exception("Note content is empty"))
                    NoteLoader(content, item.name).load()
                }
                KnowledgeItemType.URL -> {
                    val url = item.url ?: return@withContext Result.failure(Exception("URL is empty"))
                    UrlLoader(url, client).load()
                }
            }

            if (documentChunks.isEmpty()) {
                return@withContext Result.failure(Exception("No content to process"))
            }

            // Combine all document content
            val fullContent = documentChunks.joinToString("\n\n") { it.content }

            // Create text chunker and split content
            val chunker = TextChunker(
                chunkSize = base.chunkSize,
                chunkOverlap = base.chunkOverlap
            )
            val textChunks = chunker.chunkByParagraphs(fullContent)

            if (textChunks.isEmpty()) {
                return@withContext Result.failure(Exception("No chunks generated"))
            }

            Log.d(TAG, "Processing item ${item.id}: ${textChunks.size} chunks")

            // Generate embeddings
            val embeddings = generateEmbeddings(
                providerSetting = providerSetting,
                texts = textChunks,
                model = base.embeddingModelId
            )

            // Create and store chunks
            val knowledgeChunks = textChunks.mapIndexed { index, text ->
                KnowledgeChunk(
                    id = Uuid.random(),
                    itemId = item.id,
                    baseId = item.baseId,
                    content = text,
                    embedding = embeddings[index],
                    metadata = documentChunks.firstOrNull()?.metadata,
                    chunkIndex = index,
                )
            }

            repository.insertChunks(knowledgeChunks)

            // Update status to completed
            repository.updateItemStatus(item.id, ProcessingStatus.COMPLETED)

            Log.d(TAG, "Successfully processed item ${item.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process item ${item.id}", e)
            repository.updateItemStatus(item.id, ProcessingStatus.FAILED, e.message)
            Result.failure(e)
        }
    }

    /**
     * Search the knowledge base for relevant chunks
     */
    suspend fun search(
        baseId: Uuid,
        query: String,
        topK: Int? = null,
    ): List<KnowledgeSearchResult> = withContext(Dispatchers.IO) {
        try {
            // Get the knowledge base configuration
            val base = repository.getBaseByIdSync(baseId)
                ?: return@withContext emptyList()

            val effectiveTopK = topK ?: base.documentCount

            // Get provider settings
            val settings = settingsStore.settingsFlow.first()
            val providerSetting = settings.providers.find { it.id == base.embeddingProviderId }
                ?: return@withContext emptyList()

            // Generate query embedding
            val queryEmbedding = generateEmbedding(
                providerSetting = providerSetting,
                text = query,
                model = base.embeddingModelId
            )

            // Get all chunks from the knowledge base
            val chunks = repository.getChunksByBaseId(baseId)

            if (chunks.isEmpty()) {
                return@withContext emptyList()
            }

            // Calculate similarity scores and sort
            val results = chunks.map { chunk ->
                val score = VectorUtils.cosineSimilarity(queryEmbedding, chunk.embedding)
                KnowledgeSearchResult(
                    chunk = chunk,
                    score = score,
                    item = repository.getItemById(chunk.itemId)
                )
            }
                .filter { it.score >= base.threshold }
                .sortedByDescending { it.score }
                .take(effectiveTopK)

            Log.d(TAG, "Search found ${results.size} results for query: $query")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            emptyList()
        }
    }

    /**
     * Search multiple knowledge bases
     */
    suspend fun searchMultiple(
        baseIds: List<Uuid>,
        query: String,
        topK: Int = 6,
    ): List<KnowledgeSearchResult> = withContext(Dispatchers.IO) {
        val allResults = baseIds.flatMap { baseId ->
            search(baseId, query, topK)
        }

        // Sort by score and take top K
        allResults
            .sortedByDescending { it.score }
            .take(topK)
    }

    /**
     * Search across multiple knowledge bases by their IDs
     * This is a convenience method for chat integration
     */
    suspend fun searchAcrossKnowledgeBases(
        knowledgeBaseIds: List<Uuid>,
        query: String,
        limit: Int = 5,
    ): List<KnowledgeSearchResult> {
        if (knowledgeBaseIds.isEmpty() || query.isBlank()) {
            return emptyList()
        }
        return searchMultiple(knowledgeBaseIds, query, limit)
    }

    /**
     * Add a file to the knowledge base
     */
    suspend fun addFile(
        baseId: Uuid,
        file: File,
    ): Result<KnowledgeItem> = withContext(Dispatchers.IO) {
        try {
            val item = KnowledgeItem(
                baseId = baseId,
                type = KnowledgeItemType.FILE,
                name = file.name,
                filePath = file.absolutePath,
                status = ProcessingStatus.PENDING,
            )
            repository.insertItem(item)
            Result.success(item)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add a note to the knowledge base
     */
    suspend fun addNote(
        baseId: Uuid,
        name: String,
        content: String,
    ): Result<KnowledgeItem> = withContext(Dispatchers.IO) {
        try {
            val item = KnowledgeItem(
                baseId = baseId,
                type = KnowledgeItemType.NOTE,
                name = name,
                content = content,
                status = ProcessingStatus.PENDING,
            )
            repository.insertItem(item)
            Result.success(item)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add a URL to the knowledge base
     */
    suspend fun addUrl(
        baseId: Uuid,
        url: String,
        name: String? = null,
    ): Result<KnowledgeItem> = withContext(Dispatchers.IO) {
        try {
            val item = KnowledgeItem(
                baseId = baseId,
                type = KnowledgeItemType.URL,
                name = name ?: url,
                url = url,
                status = ProcessingStatus.PENDING,
            )
            repository.insertItem(item)
            Result.success(item)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update a note in the knowledge base
     */
    suspend fun updateNote(
        itemId: Uuid,
        name: String,
        content: String,
    ): Result<KnowledgeItem> = withContext(Dispatchers.IO) {
        try {
            val item = repository.getItemById(itemId)
                ?: return@withContext Result.failure(Exception("Item not found"))

            if (item.type != KnowledgeItemType.NOTE) {
                return@withContext Result.failure(Exception("Item is not a note"))
            }

            // Delete old chunks
            repository.deleteChunksByItemId(itemId)

            // Update item with new content and reset status to pending
            val updatedItem = item.copy(
                name = name,
                content = content,
                status = ProcessingStatus.PENDING
            )
            repository.updateItem(updatedItem)

            Result.success(updatedItem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update a URL in the knowledge base
     */
    suspend fun updateUrl(
        itemId: Uuid,
        url: String,
        name: String? = null,
    ): Result<KnowledgeItem> = withContext(Dispatchers.IO) {
        try {
            val item = repository.getItemById(itemId)
                ?: return@withContext Result.failure(Exception("Item not found"))

            if (item.type != KnowledgeItemType.URL) {
                return@withContext Result.failure(Exception("Item is not a URL"))
            }

            // Delete old chunks
            repository.deleteChunksByItemId(itemId)

            // Update item with new URL and reset status to pending
            val updatedItem = item.copy(
                name = name ?: url,
                url = url,
                status = ProcessingStatus.PENDING
            )
            repository.updateItem(updatedItem)

            Result.success(updatedItem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove an item and its chunks from the knowledge base
     */
    suspend fun removeItem(itemId: Uuid): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            repository.deleteChunksByItemId(itemId)
            repository.deleteItem(itemId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Process all pending items
     */
    suspend fun processAllPending(): List<Result<Unit>> = withContext(Dispatchers.IO) {
        val pendingItems = repository.getPendingItems()
        pendingItems.map { item ->
            processItem(item)
        }
    }

    /**
     * Process all pending items in a knowledge base
     */
    suspend fun processPendingInBase(baseId: Uuid): List<Result<Unit>> = withContext(Dispatchers.IO) {
        val items = repository.getItemsByBaseIdSync(baseId)
            .filter { it.status == ProcessingStatus.PENDING }
        items.map { item ->
            processItem(item)
        }
    }

    private suspend fun generateEmbeddings(
        providerSetting: ProviderSetting,
        texts: List<String>,
        model: String,
    ): List<FloatArray> {
        return when (providerSetting) {
            is ProviderSetting.OpenAI -> {
                // Batch in groups of 100 to avoid API limits
                val batchSize = 100
                texts.chunked(batchSize).flatMap { batch ->
                    openAIProvider.embed(providerSetting, batch, model)
                }
            }
            else -> {
                throw IllegalArgumentException("Provider does not support embeddings: ${providerSetting::class.simpleName}")
            }
        }
    }

    private suspend fun generateEmbedding(
        providerSetting: ProviderSetting,
        text: String,
        model: String,
    ): FloatArray {
        return generateEmbeddings(providerSetting, listOf(text), model).first()
    }
}
