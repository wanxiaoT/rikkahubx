package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.KnowledgeBaseEntity

@Dao
interface KnowledgeBaseDAO {
    @Query("SELECT * FROM knowledge_base ORDER BY updated_at DESC")
    fun getAll(): Flow<List<KnowledgeBaseEntity>>

    @Query("SELECT * FROM knowledge_base ORDER BY updated_at DESC")
    suspend fun getAllSync(): List<KnowledgeBaseEntity>

    @Query("SELECT * FROM knowledge_base WHERE id = :id")
    fun getById(id: String): Flow<KnowledgeBaseEntity?>

    @Query("SELECT * FROM knowledge_base WHERE id = :id")
    suspend fun getByIdSync(id: String): KnowledgeBaseEntity?

    @Insert
    suspend fun insert(entity: KnowledgeBaseEntity)

    @Update
    suspend fun update(entity: KnowledgeBaseEntity)

    @Delete
    suspend fun delete(entity: KnowledgeBaseEntity)

    @Query("DELETE FROM knowledge_base WHERE id = :id")
    suspend fun deleteById(id: String)
}
