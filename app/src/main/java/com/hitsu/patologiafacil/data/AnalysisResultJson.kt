package com.hitsu.patologiafacil.data

import com.google.gson.Gson
import com.hitsu.patologiafacil.domain.AnalysisResult

object AnalysisResultJson {

    private val gson = Gson()

    fun toJson(result: AnalysisResult): String = gson.toJson(result)

    fun fromJson(json: String?): AnalysisResult? {
        if (json.isNullOrBlank()) return null
        return try {
            gson.fromJson(json, AnalysisResult::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
