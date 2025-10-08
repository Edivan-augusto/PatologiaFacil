package com.hitsu.patologiafacil.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analysis")
data class AnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageUri: String,
    val local: String,
    val tempoEvento: String,
    val date: String,
    val resultJson: String,
    val severity: String,
    val topCause: String
)
