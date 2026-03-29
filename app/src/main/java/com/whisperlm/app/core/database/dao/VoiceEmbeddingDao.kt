package com.whisperlm.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whisperlm.app.core.database.entity.VoiceEmbeddingEntity

@Dao
interface VoiceEmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: VoiceEmbeddingEntity): Long

    @Query("SELECT * FROM voice_embeddings WHERE person_id = :personId")
    suspend fun getEmbeddingsForPerson(personId: Long): List<VoiceEmbeddingEntity>

    // Returns all embeddings across all persons for similarity search
    @Query("SELECT * FROM voice_embeddings")
    suspend fun getAllEmbeddings(): List<VoiceEmbeddingEntity>

    @Query("SELECT COUNT(*) FROM voice_embeddings WHERE person_id = :personId")
    suspend fun getEmbeddingCountForPerson(personId: Long): Int

    @Query("DELETE FROM voice_embeddings WHERE person_id = :personId")
    suspend fun deleteEmbeddingsForPerson(personId: Long)
}
