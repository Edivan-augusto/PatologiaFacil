package com.hitsu.patologiafacil.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: AnalysisEntity): Long

    @Query("SELECT * FROM analysis ORDER BY id DESC")
    fun all(): Flow<List<AnalysisEntity>>

    @Query("DELETE FROM analysis WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ✅ dedup robusta via contentKey salvo em tempoEvento
    @Query("SELECT COUNT(*) FROM analysis WHERE tempoEvento = :key")
    suspend fun countByKey(key: String): Int

    @Query("SELECT * FROM analysis ORDER BY id DESC")
    suspend fun allOnce(): List<AnalysisEntity>
}
