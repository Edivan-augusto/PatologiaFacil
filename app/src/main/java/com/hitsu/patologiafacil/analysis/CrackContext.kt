package com.hitsu.patologiafacil.analysis

import java.util.Locale

/**
 * Normaliza dados de contexto fornecidos pelo usuario (local e tempo) para apoiar a interpretacao da analise remota.
 */
object CrackContextNormalizer {

    fun normalize(rawLocal: String?, rawTempo: String?): CrackContextInfo {
        val localSanitized = rawLocal?.trim().orEmpty()
        val tempoSanitized = rawTempo?.trim().orEmpty()

        val localData = normalizeLocal(localSanitized)
        val tempoData = normalizeTempo(tempoSanitized)

        val moistureHint = localData.category in setOf(
            CrackLocationCategory.BASE_RODAPE,
            CrackLocationCategory.PLATIBANDA
        ) || tempoData.hintsMoisture

        val settlementHint = tempoData.hintsSettlement || localData.category == CrackLocationCategory.PROXIMO_ESTRUTURA
        val overloadHint = localData.category == CrackLocationCategory.PROXIMO_ESTRUTURA

        val activity = when (val days = tempoData.daysApprox) {
            null -> ActivityStatus.INDEFINIDA
            in 0..60 -> ActivityStatus.ATIVA
            in 61..210 -> ActivityStatus.INDEFINIDA
            else -> ActivityStatus.INATIVA
        }

        return CrackContextInfo(
            originalLocal = localSanitized,
            normalizedLocal = localData.display,
            localCategory = localData.category,
            originalTempo = tempoSanitized,
            tempoDisplay = tempoData.display,
            daysApprox = tempoData.daysApprox,
            activity = activity,
            mentionsMoisture = moistureHint,
            mentionsSettlement = settlementHint,
            mentionsOverload = overloadHint,
            tempoHints = tempoData.hints
        )
    }

    private fun normalizeLocal(input: String): LocalData {
        if (input.isBlank()) {
            return LocalData(
                display = "nao informado",
                category = CrackLocationCategory.NAO_INFORMADO
            )
        }
        val lower = input.lowercase(Locale.ROOT)
        val category = when {
            listOf("rodape", "rodap", "base", "pe da parede").any { lower.contains(it) } ->
                CrackLocationCategory.BASE_RODAPE
            lower.contains("platibanda") -> CrackLocationCategory.PLATIBANDA
            listOf("janela", "porta", "vao", "esquadria").any { lower.contains(it) } ->
                CrackLocationCategory.JUNTO_VAO
            listOf("canto", "quina").any { lower.contains(it) } -> CrackLocationCategory.CANTO
            listOf("meio", "centro", "painel").any { lower.contains(it) } -> CrackLocationCategory.MEIO_DO_PANO
            listOf("encontro", "junta", "uniao").any { lower.contains(it) } -> CrackLocationCategory.ENCONTRO_MATERIAIS
            listOf("pilar", "viga", "coluna", "laje", "apoio").any { lower.contains(it) } ->
                CrackLocationCategory.PROXIMO_ESTRUTURA
            listOf("fachada", "externa", "lado de fora").any { lower.contains(it) } ->
                CrackLocationCategory.FACHADA_EXTERNA
            listOf("interna", "interior").any { lower.contains(it) } -> CrackLocationCategory.PAREDE_INTERNA
            else -> CrackLocationCategory.OUTRO
        }
        val display = when (category) {
            CrackLocationCategory.BASE_RODAPE -> "base/rodape"
            CrackLocationCategory.PLATIBANDA -> "platibanda"
            CrackLocationCategory.JUNTO_VAO -> "junto a vao (janela/porta)"
            CrackLocationCategory.CANTO -> "canto"
            CrackLocationCategory.MEIO_DO_PANO -> "meio do pano"
            CrackLocationCategory.ENCONTRO_MATERIAIS -> "encontro de materiais"
            CrackLocationCategory.PROXIMO_ESTRUTURA -> "proximo a pilar/viga"
            CrackLocationCategory.FACHADA_EXTERNA -> "fachada externa"
            CrackLocationCategory.PAREDE_INTERNA -> "parede interna"
            CrackLocationCategory.OUTRO -> input
            CrackLocationCategory.NAO_INFORMADO -> "nao informado"
        }
        return LocalData(display, category)
    }

    private fun normalizeTempo(input: String): TempoData {
        if (input.isBlank()) {
            return TempoData("nao informado", null, hintsMoisture = false, hintsSettlement = false, hints = emptyList())
        }
        val lower = input.lowercase(Locale.ROOT)
        val hints = mutableListOf<String>()
        val mentionsMoisture = listOf("chuva", "chuv", "umid", "vazamento", "infiltra").any { lower.contains(it) }.also {
            if (it) hints += "relato de umidade/chuvas"
        }
        val mentionsSettlement = listOf("recalque", "afund", "cede", "cedeu", "obra", "escava").any { lower.contains(it) }.also {
            if (it) hints += "relato de recalque/obras"
        }

        parseRelative(lower)?.let {
            return TempoData(it.display, it.days, mentionsMoisture, mentionsSettlement, hints)
        }

        val display = input
        return TempoData(display, null, mentionsMoisture, mentionsSettlement, hints)
    }

    private fun parseRelative(lower: String): RelativeResult? {
        val dayMatch = Regex("(\\d+)\\s*(dia|dias)").find(lower)
        if (dayMatch != null) {
            val value = dayMatch.groupValues[1].toInt()
            return RelativeResult("ha ~${value} dias", value)
        }
        val weekMatch = Regex("(\\d+)\\s*(semana|semanas)").find(lower)
        if (weekMatch != null) {
            val value = weekMatch.groupValues[1].toInt()
            val days = value * 7
            return RelativeResult("ha ~${value} semanas", days)
        }
        val monthMatch = Regex("(\\d+)\\s*(mes|meses)").find(lower)
        if (monthMatch != null) {
            val value = monthMatch.groupValues[1].toInt()
            val days = value * 30
            return RelativeResult("ha ~${value} meses", days)
        }
        val yearMatch = Regex("(\\d+)\\s*(ano|anos)").find(lower)
        if (yearMatch != null) {
            val value = yearMatch.groupValues[1].toInt()
            val days = value * 365
            return RelativeResult("ha ~${value} anos", days)
        }
        val sinceMatch = Regex("desde\\s*(\\d{4})").find(lower)
        if (sinceMatch != null) {
            val year = sinceMatch.groupValues[1].toInt()
            val currentYear = java.time.LocalDate.now().year
            val years = (currentYear - year).coerceAtLeast(0)
            val days = if (years == 0) null else years * 365
            return RelativeResult("desde ${year}", days)
        }
        val isoMatch = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(lower)
        if (isoMatch != null) {
            return try {
                val date = java.time.LocalDate.parse(isoMatch.value)
                val now = java.time.LocalDate.now()
                val period = java.time.Period.between(date, now)
                val days = period.days + period.months * 30 + period.years * 365
                RelativeResult("desde ${date}", days.coerceAtLeast(0))
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private data class LocalData(
        val display: String,
        val category: CrackLocationCategory
    )

    private data class TempoData(
        val display: String,
        val daysApprox: Int?,
        val hintsMoisture: Boolean,
        val hintsSettlement: Boolean,
        val hints: List<String>
    )

    private data class RelativeResult(val display: String, val days: Int?)
}

data class CrackContextInfo(
    val originalLocal: String,
    val normalizedLocal: String,
    val localCategory: CrackLocationCategory,
    val originalTempo: String,
    val tempoDisplay: String,
    val daysApprox: Int?,
    val activity: ActivityStatus,
    val mentionsMoisture: Boolean,
    val mentionsSettlement: Boolean,
    val mentionsOverload: Boolean,
    val tempoHints: List<String>
)

enum class CrackLocationCategory {
    BASE_RODAPE,
    PLATIBANDA,
    JUNTO_VAO,
    CANTO,
    MEIO_DO_PANO,
    ENCONTRO_MATERIAIS,
    PROXIMO_ESTRUTURA,
    FACHADA_EXTERNA,
    PAREDE_INTERNA,
    OUTRO,
    NAO_INFORMADO
}

enum class ActivityStatus {
    ATIVA,
    INDEFINIDA,
    INATIVA
}

