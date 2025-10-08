package com.hitsu.patologiafacil.domain

import com.google.gson.annotations.SerializedName

data class AnalysisResult(
    @SerializedName("detected") val detected: Boolean = false,
    @SerializedName("tipo") val tipo: String = "",
    @SerializedName("orientacao") val orientacao: String = "",
    @SerializedName("larguraMm") val larguraMm: String = "",
    @SerializedName("comprimentoMm") val comprimentoMm: String = "",
    @SerializedName("causaProvavel") val causaProvavel: String = "",
    @SerializedName("severidade") val severidade: String = "",
    @SerializedName("observacoes") val observacoes: List<String> = emptyList(),
    @SerializedName("localInformado") val localInformado: String = "",
    @SerializedName("localOriginal") val localOriginal: String = "",
    @SerializedName("tempoInformado") val tempoInformado: String = "",
    @SerializedName("tempoOriginal") val tempoOriginal: String = "",
    @SerializedName("interpretacaoContexto") val interpretacaoContexto: String = "",
    @SerializedName("atividadeAparente") val atividadeAparente: String = "",
    @SerializedName("characterization") val characterization: String = "",
    @SerializedName("advice") val advice: List<String> = emptyList(),
    @SerializedName("imagePath") val imagePath: String = "",
    @SerializedName("generatedAt") val generatedAt: String = "",
    @SerializedName("rawWidthMm") val rawWidthMm: Double? = null,
    @SerializedName("rawLengthMm") val rawLengthMm: Double? = null,
    @SerializedName("hasScale") val hasScale: Boolean = false,
    @SerializedName("rawResponseJson") val rawResponseJson: String? = null
)
