package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_chunk",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = KnowledgeBaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["base_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("item_id"), Index("base_id")]
)
data class KnowledgeChunkEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("item_id")
    val itemId: String,
    @ColumnInfo("base_id")
    val baseId: String,
    @ColumnInfo("content")
    val content: String,
    @ColumnInfo("embedding")
    val embedding: String, // JSON serialized FloatArray
    @ColumnInfo("metadata")
    val metadata: String?, // JSON metadata
    @ColumnInfo("chunk_index")
    val chunkIndex: Int,
)
