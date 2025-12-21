package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "knowledge_base")
data class KnowledgeBaseEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("description")
    val description: String?,
    @ColumnInfo("embedding_provider_id")
    val embeddingProviderId: String,
    @ColumnInfo("embedding_model_id")
    val embeddingModelId: String,
    @ColumnInfo("chunk_size", defaultValue = "500")
    val chunkSize: Int,
    @ColumnInfo("chunk_overlap", defaultValue = "100")
    val chunkOverlap: Int,
    @ColumnInfo("document_count", defaultValue = "6")
    val documentCount: Int,
    @ColumnInfo("threshold", defaultValue = "0.0")
    val threshold: Float,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)
