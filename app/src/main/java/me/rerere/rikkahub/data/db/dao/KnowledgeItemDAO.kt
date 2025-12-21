package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.KnowledgeItemEntity

@Dao
interface KnowledgeItemDAO {
    @Query("SELECT * FROM knowledge_item WHERE base_id = :baseId ORDER BY created_at DESC")
    fun getByBaseId(baseId: String): Flow<List<KnowledgeItemEntity>>

    @Query("SELECT * FROM knowledge_item WHERE base_id = :baseId ORDER BY created_at DESC")
    suspend fun getByBaseIdSync(baseId: String): List<KnowledgeItemEntity>

    @Query("SELECT * FROM knowledge_item WHERE id = :id")
    suspend fun getById(id: String): KnowledgeItemEntity?

    @Query("SELECT * FROM knowledge_item WHERE base_id = :baseId AND type = :type ORDER BY created_at DESC")
    fun getByBaseIdAndType(baseId: String, type: String): Flow<List<KnowledgeItemEntity>>

    @Query("SELECT * FROM knowledge_item WHERE status = :status ORDER BY created_at ASC")
    suspend fun getByStatus(status: String): List<KnowledgeItemEntity>

    @Query("SELECT COUNT(*) FROM knowledge_item WHERE base_id = :baseId")
    fun getCountByBaseId(baseId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM knowledge_item WHERE base_id = :baseId AND status = :status")
    fun getCountByBaseIdAndStatus(baseId: String, status: String): Flow<Int>

    @Insert
    suspend fun insert(entity: KnowledgeItemEntity)

    @Insert
    suspend fun insertAll(entities: List<KnowledgeItemEntity>)

    @Update
    suspend fun update(entity: KnowledgeItemEntity)

    @Query("UPDATE knowledge_item SET status = :status, error_message = :errorMessage, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, errorMessage: String?, updatedAt: Long)

    @Delete
    suspend fun delete(entity: KnowledgeItemEntity)

    @Query("DELETE FROM knowledge_item WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM knowledge_item WHERE base_id = :baseId")
    suspend fun deleteByBaseId(baseId: String)
}
