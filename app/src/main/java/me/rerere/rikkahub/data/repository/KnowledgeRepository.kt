package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.db.dao.KnowledgeBaseDAO
import me.rerere.rikkahub.data.db.dao.KnowledgeChunkDAO
import me.rerere.rikkahub.data.db.dao.KnowledgeItemDAO
import me.rerere.rikkahub.data.db.entity.KnowledgeBaseEntity
import me.rerere.rikkahub.data.db.entity.KnowledgeChunkEntity
import me.rerere.rikkahub.data.db.entity.KnowledgeItemEntity
import me.rerere.rikkahub.data.model.KnowledgeBase
import me.rerere.rikkahub.data.model.KnowledgeChunk
import me.rerere.rikkahub.data.model.KnowledgeItem
import me.rerere.rikkahub.data.model.KnowledgeItemType
import me.rerere.rikkahub.data.model.ProcessingStatus
import me.rerere.rikkahub.utils.JsonInstant
import java.time.Instant
import kotlin.uuid.Uuid

class KnowledgeRepository(
    private val knowledgeBaseDAO: KnowledgeBaseDAO,
    private val knowledgeItemDAO: KnowledgeItemDAO,
    private val knowledgeChunkDAO: KnowledgeChunkDAO,
) {
    // ============== Knowledge Base ==============

    fun getAllBases(): Flow<List<KnowledgeBase>> {
        return knowledgeBaseDAO.getAll().map { list ->
            list.map { entityToBase(it) }
        }
    }

    suspend fun getAllBasesSync(): List<KnowledgeBase> {
        return knowledgeBaseDAO.getAllSync().map { entityToBase(it) }
    }

    fun getBaseById(id: Uuid): Flow<KnowledgeBase?> {
        return knowledgeBaseDAO.getById(id.toString()).map { entity ->
            entity?.let { entityToBase(it) }
        }
    }

    suspend fun getBaseByIdSync(id: Uuid): KnowledgeBase? {
        return knowledgeBaseDAO.getByIdSync(id.toString())?.let { entityToBase(it) }
    }

    suspend fun insertBase(base: KnowledgeBase) {
        knowledgeBaseDAO.insert(baseToEntity(base))
    }

    suspend fun updateBase(base: KnowledgeBase) {
        knowledgeBaseDAO.update(baseToEntity(base.copy(updatedAt = Instant.now())))
    }

    suspend fun deleteBase(id: Uuid) {
        knowledgeBaseDAO.deleteById(id.toString())
    }

    // ============== Knowledge Item ==============

    fun getItemsByBaseId(baseId: Uuid): Flow<List<KnowledgeItem>> {
        return knowledgeItemDAO.getByBaseId(baseId.toString()).map { list ->
            list.map { entityToItem(it) }
        }
    }

    suspend fun getItemsByBaseIdSync(baseId: Uuid): List<KnowledgeItem> {
        return knowledgeItemDAO.getByBaseIdSync(baseId.toString()).map { entityToItem(it) }
    }

    fun getItemsByBaseIdAndType(baseId: Uuid, type: KnowledgeItemType): Flow<List<KnowledgeItem>> {
        return knowledgeItemDAO.getByBaseIdAndType(baseId.toString(), type.name).map { list ->
            list.map { entityToItem(it) }
        }
    }

    suspend fun getItemById(id: Uuid): KnowledgeItem? {
        return knowledgeItemDAO.getById(id.toString())?.let { entityToItem(it) }
    }

    suspend fun getPendingItems(): List<KnowledgeItem> {
        return knowledgeItemDAO.getByStatus(ProcessingStatus.PENDING.name).map { entityToItem(it) }
    }

    fun getItemCount(baseId: Uuid): Flow<Int> {
        return knowledgeItemDAO.getCountByBaseId(baseId.toString())
    }

    fun getItemCountByStatus(baseId: Uuid, status: ProcessingStatus): Flow<Int> {
        return knowledgeItemDAO.getCountByBaseIdAndStatus(baseId.toString(), status.name)
    }

    suspend fun insertItem(item: KnowledgeItem) {
        knowledgeItemDAO.insert(itemToEntity(item))
    }

    suspend fun insertItems(items: List<KnowledgeItem>) {
        knowledgeItemDAO.insertAll(items.map { itemToEntity(it) })
    }

    suspend fun updateItem(item: KnowledgeItem) {
        knowledgeItemDAO.update(itemToEntity(item.copy(updatedAt = Instant.now())))
    }

    suspend fun updateItemStatus(id: Uuid, status: ProcessingStatus, errorMessage: String? = null) {
        knowledgeItemDAO.updateStatus(
            id = id.toString(),
            status = status.name,
            errorMessage = errorMessage,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun deleteItem(id: Uuid) {
        knowledgeItemDAO.deleteById(id.toString())
    }

    // ============== Knowledge Chunk ==============

    suspend fun getChunksByBaseId(baseId: Uuid): List<KnowledgeChunk> {
        return knowledgeChunkDAO.getByBaseId(baseId.toString()).map { entityToChunk(it) }
    }

    suspend fun getChunksByItemId(itemId: Uuid): List<KnowledgeChunk> {
        return knowledgeChunkDAO.getByItemId(itemId.toString()).map { entityToChunk(it) }
    }

    suspend fun getChunkCount(baseId: Uuid): Int {
        return knowledgeChunkDAO.getCountByBaseId(baseId.toString())
    }

    suspend fun insertChunk(chunk: KnowledgeChunk) {
        knowledgeChunkDAO.insert(chunkToEntity(chunk))
    }

    suspend fun insertChunks(chunks: List<KnowledgeChunk>) {
        knowledgeChunkDAO.insertAll(chunks.map { chunkToEntity(it) })
    }

    suspend fun deleteChunksByItemId(itemId: Uuid) {
        knowledgeChunkDAO.deleteByItemId(itemId.toString())
    }

    suspend fun deleteChunksByBaseId(baseId: Uuid) {
        knowledgeChunkDAO.deleteByBaseId(baseId.toString())
    }

    // ============== Entity Conversions ==============

    private fun entityToBase(entity: KnowledgeBaseEntity): KnowledgeBase {
        return KnowledgeBase(
            id = Uuid.parse(entity.id),
            name = entity.name,
            description = entity.description,
            embeddingProviderId = Uuid.parse(entity.embeddingProviderId),
            embeddingModelId = entity.embeddingModelId,
            chunkSize = entity.chunkSize,
            chunkOverlap = entity.chunkOverlap,
            documentCount = entity.documentCount,
            threshold = entity.threshold,
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            updatedAt = Instant.ofEpochMilli(entity.updatedAt),
        )
    }

    private fun baseToEntity(base: KnowledgeBase): KnowledgeBaseEntity {
        return KnowledgeBaseEntity(
            id = base.id.toString(),
            name = base.name,
            description = base.description,
            embeddingProviderId = base.embeddingProviderId.toString(),
            embeddingModelId = base.embeddingModelId,
            chunkSize = base.chunkSize,
            chunkOverlap = base.chunkOverlap,
            documentCount = base.documentCount,
            threshold = base.threshold,
            createdAt = base.createdAt.toEpochMilli(),
            updatedAt = base.updatedAt.toEpochMilli(),
        )
    }

    private fun entityToItem(entity: KnowledgeItemEntity): KnowledgeItem {
        return KnowledgeItem(
            id = Uuid.parse(entity.id),
            baseId = Uuid.parse(entity.baseId),
            type = KnowledgeItemType.valueOf(entity.type),
            name = entity.name,
            content = entity.content,
            filePath = entity.filePath,
            url = entity.url,
            status = ProcessingStatus.valueOf(entity.status),
            errorMessage = entity.errorMessage,
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            updatedAt = Instant.ofEpochMilli(entity.updatedAt),
        )
    }

    private fun itemToEntity(item: KnowledgeItem): KnowledgeItemEntity {
        return KnowledgeItemEntity(
            id = item.id.toString(),
            baseId = item.baseId.toString(),
            type = item.type.name,
            name = item.name,
            content = item.content,
            filePath = item.filePath,
            url = item.url,
            status = item.status.name,
            errorMessage = item.errorMessage,
            createdAt = item.createdAt.toEpochMilli(),
            updatedAt = item.updatedAt.toEpochMilli(),
        )
    }

    private fun entityToChunk(entity: KnowledgeChunkEntity): KnowledgeChunk {
        return KnowledgeChunk(
            id = Uuid.parse(entity.id),
            itemId = Uuid.parse(entity.itemId),
            baseId = Uuid.parse(entity.baseId),
            content = entity.content,
            embedding = JsonInstant.decodeFromString(entity.embedding),
            metadata = entity.metadata?.let { JsonInstant.decodeFromString(it) },
            chunkIndex = entity.chunkIndex,
        )
    }

    private fun chunkToEntity(chunk: KnowledgeChunk): KnowledgeChunkEntity {
        return KnowledgeChunkEntity(
            id = chunk.id.toString(),
            itemId = chunk.itemId.toString(),
            baseId = chunk.baseId.toString(),
            content = chunk.content,
            embedding = JsonInstant.encodeToString(chunk.embedding.toList()),
            metadata = chunk.metadata?.let { JsonInstant.encodeToString(it) },
            chunkIndex = chunk.chunkIndex,
        )
    }
}
