package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_item",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeBaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["base_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("base_id")]
)
data class KnowledgeItemEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("base_id")
    val baseId: String,
    @ColumnInfo("type")
    val type: String, // file, url, note
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("content")
    val content: String?, // for note type
    @ColumnInfo("file_path")
    val filePath: String?, // for file type
    @ColumnInfo("url")
    val url: String?, // for url type
    @ColumnInfo("status")
    val status: String, // pending, processing, completed, failed
    @ColumnInfo("error_message")
    val errorMessage: String?,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)
