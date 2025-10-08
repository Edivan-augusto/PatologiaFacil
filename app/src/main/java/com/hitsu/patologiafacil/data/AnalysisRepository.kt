package com.hitsu.patologiafacil.data

import kotlinx.coroutines.flow.Flow

class AnalysisRepository(private val dao: AnalysisDao) {
    fun all(): Flow<List<AnalysisEntity>> = dao.all()
    suspend fun insert(e: AnalysisEntity) = dao.insert(e)
    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun countByKey(key: String): Int = dao.countByKey(key)

    suspend fun listOnce(): List<AnalysisEntity> = dao.allOnce()
}
