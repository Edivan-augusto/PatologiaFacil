package com.hitsu.patologiafacil.analysis.remote

data class RemoteDetection(
    val detected: Boolean,
    val orientation: String?,
    val hasScale: Boolean,
    val widthMm: Double?,
    val lengthMm: Double?,
    val relativeWidth: String?,
    val notes: List<String>,
    val rawJson: String
)
