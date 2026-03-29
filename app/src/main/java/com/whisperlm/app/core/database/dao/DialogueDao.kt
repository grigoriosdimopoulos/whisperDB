package com.whisperlm.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.whisperlm.app.core.database.entity.DialogueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DialogueDao {

    @Query("SELECT * FROM dialogues ORDER BY recorded_at DESC")
    fun getAllDialoguesNewestFirst(): Flow<List<DialogueEntity>>

    @Query("SELECT * FROM dialogues ORDER BY recorded_at ASC")
    fun getAllDialoguesOldestFirst(): Flow<List<DialogueEntity>>

    @Query("SELECT * FROM dialogues WHERE id = :id")
    suspend fun getDialogueById(id: Long): DialogueEntity?

    @Query("""
        SELECT d.* FROM dialogues d
        INNER JOIN dialogue_participants dp ON d.id = dp.dialogue_id
        WHERE dp.person_id = :personId
        ORDER BY d.recorded_at DESC
    """)
    fun getDialoguesForPerson(personId: Long): Flow<List<DialogueEntity>>

    @Query("SELECT * FROM dialogues")
    suspend fun getAllDialoguesSnapshot(): List<DialogueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDialogue(dialogue: DialogueEntity): Long

    @Update
    suspend fun updateDialogue(dialogue: DialogueEntity)

    @Delete
    suspend fun deleteDialogue(dialogue: DialogueEntity)

    @Query("DELETE FROM dialogues WHERE id = :id")
    suspend fun deleteDialogueById(id: Long)

    @Query("SELECT COUNT(*) FROM dialogues")
    suspend fun getDialogueCount(): Int
}
