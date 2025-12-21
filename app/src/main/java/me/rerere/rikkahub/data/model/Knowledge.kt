package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.util.InstantSerializer
import java.time.Instant
import kotlin.uuid.Uuid

enum class KnowledgeItemType {
    FILE,
    URL,
    NOTE
}

enum class ProcessingStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

@Serializable
data class KnowledgeBase(
    val id: Uuid = Uuid.random(),
    val name: String,
    val description: String? = null,
    val embeddingProviderId: Uuid,
    val embeddingModelId: String,
    val chunkSize: Int = 500,
    val chunkOverlap: Int = 100,
    val documentCount: Int = 6,
    val threshold: Float = 0.0f,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now(),
)

@Serializable
data class KnowledgeItem(
    val id: Uuid = Uuid.random(),
    val baseId: Uuid,
    val type: KnowledgeItemType,
    val name: String,
    val content: String? = null, // for note
    val filePath: String? = null, // for file
    val url: String? = null, // for url
    val status: ProcessingStatus = ProcessingStatus.PENDING,
    val errorMessage: String? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now(),
)

@Serializable
data class KnowledgeChunk(
    val id: Uuid = Uuid.random(),
    val itemId: Uuid,
    val baseId: Uuid,
    val content: String,
    val embedding: FloatArray,
    val metadata: Map<String, String>? = null,
    val chunkIndex: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KnowledgeChunk

        if (id != other.id) return false
        if (itemId != other.itemId) return false
        if (baseId != other.baseId) return false
        if (content != other.content) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (metadata != other.metadata) return false
        if (chunkIndex != other.chunkIndex) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + itemId.hashCode()
        result = 31 * result + baseId.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + (metadata?.hashCode() ?: 0)
        result = 31 * result + chunkIndex
        return result
    }
}

data class KnowledgeSearchResult(
    val chunk: KnowledgeChunk,
    val score: Float,
    val item: KnowledgeItem? = null,
) {
    val similarity: Float get() = score
    val itemName: String get() = item?.name ?: "Unknown"
}
