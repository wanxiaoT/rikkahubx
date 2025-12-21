package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import me.rerere.rikkahub.data.db.entity.KnowledgeChunkEntity

@Dao
interface KnowledgeChunkDAO {
    @Query("SELECT * FROM knowledge_chunk WHERE base_id = :baseId")
    suspend fun getByBaseId(baseId: String): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunk WHERE item_id = :itemId ORDER BY chunk_index ASC")
    suspend fun getByItemId(itemId: String): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunk WHERE id = :id")
    suspend fun getById(id: String): KnowledgeChunkEntity?

    @Query("SELECT COUNT(*) FROM knowledge_chunk WHERE base_id = :baseId")
    suspend fun getCountByBaseId(baseId: String): Int

    @Insert
    suspend fun insert(entity: KnowledgeChunkEntity)

    @Insert
    suspend fun insertAll(entities: List<KnowledgeChunkEntity>)

    @Delete
    suspend fun delete(entity: KnowledgeChunkEntity)

    @Query("DELETE FROM knowledge_chunk WHERE item_id = :itemId")
    suspend fun deleteByItemId(itemId: String)

    @Query("DELETE FROM knowledge_chunk WHERE base_id = :baseId")
    suspend fun deleteByBaseId(baseId: String)
}
