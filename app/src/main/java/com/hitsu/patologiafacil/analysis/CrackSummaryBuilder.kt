package com.hitsu.patologiafacil.analysis

import android.util.Log
import com.hitsu.patologiafacil.analysis.remote.RemoteDetection
import com.hitsu.patologiafacil.domain.AnalysisResult
import java.util.Locale

object CrackSummaryBuilder {

    private const val INCLINADA_LABEL = "inclinada (~45\u00B0)"

    private const val TAG = "PF-CrackSummary"

    fun build(
        detection: RemoteDetection,
        context: CrackContextInfo,
        imagePath: String
    ): AnalysisResult {
        if (!detection.detected) {
            val notes = detection.notes.ifEmpty { listOf("Nenhuma fissura/trinca identificada na imagem.") }
            return AnalysisResult(
                detected = false,
                tipo = "",
                orientacao = "indefinida",
                larguraMm = "nao estimavel",
                comprimentoMm = "nao estimavel",
                causaProvavel = "",
                severidade = "",
                observacoes = notes,
                localInformado = context.normalizedLocal,
                localOriginal = context.originalLocal,
                tempoInformado = context.tempoDisplay,
                tempoOriginal = context.originalTempo,
                interpretacaoContexto = "Sem indicios de abertura significativa.",
                atividadeAparente = context.activity.name.lowercase(Locale.ROOT),
                characterization = "Fissura nao detectada.",
                advice = emptyList(),
                imagePath = imagePath,
                generatedAt = com.hitsu.patologiafacil.util.DateFmt.today(),
                rawWidthMm = null,
                rawLengthMm = null,
                hasScale = detection.hasScale,
                rawResponseJson = detection.rawJson
            )
        }

        val orientation = normalizeOrientation(detection.orientation)
        val widthMm = if (detection.hasScale && detection.widthMm != null && detection.widthMm > 0) detection.widthMm else null
        val lengthMm = if (detection.hasScale && detection.lengthMm != null && detection.lengthMm > 0) detection.lengthMm else null

        val widthDisplay = widthMm?.let { formatMm(it) } ?: "nao estimavel"
        val lengthDisplay = lengthMm?.let { formatMm(it) } ?: "nao estimavel"

        val tipo = determineTipo(widthMm, detection.relativeWidth)
        val severidade = when (tipo) {
            "rachadura" -> "alta"
            "trinca" -> "media"
            else -> "baixa"
        }

        val causeResult = determineCause(orientation, context)

        val observacoes = mutableListOf<String>()
        observacoes += detection.notes
        if (!detection.hasScale) {
            observacoes += "Sem escala, medidas em mm nao estimadas."
        }
        if (context.activity != ActivityStatus.INDEFINIDA) {
            observacoes += "Relato indica manifestacao ${context.activity.name.lowercase(Locale.ROOT)}."
        }

        val characterization = buildString {
            append(tipo.replaceFirstChar { it.uppercase() })
            append(" (severidade ${severidade}). ")
            append("Orientacao: ${orientation}. ")
            append("Largura: ${widthDisplay}. Comprimento: ${lengthDisplay}. ")
            append("Local: ${context.normalizedLocal}. Tempo: ${context.tempoDisplay}.")
        }

        val advice = buildAdvice(severidade, causeResult.key)

        return AnalysisResult(
            detected = true,
            tipo = tipo,
            orientacao = orientation,
            larguraMm = widthDisplay,
            comprimentoMm = lengthDisplay,
            causaProvavel = causeResult.description,
            severidade = severidade,
            observacoes = observacoes.filter { it.isNotBlank() },
            localInformado = context.normalizedLocal,
            localOriginal = context.originalLocal,
            tempoInformado = context.tempoDisplay,
            tempoOriginal = context.originalTempo,
            interpretacaoContexto = causeResult.interpretation,
            atividadeAparente = context.activity.name.lowercase(Locale.ROOT),
            characterization = characterization,
            advice = advice,
            imagePath = imagePath,
            generatedAt = com.hitsu.patologiafacil.util.DateFmt.today(),
            rawWidthMm = widthMm,
            rawLengthMm = lengthMm,
            hasScale = detection.hasScale,
            rawResponseJson = detection.rawJson
        )
    }

    private fun determineTipo(widthMm: Double?, relative: String?): String {
        widthMm?.let {
            return when {
                it <= 0.5 -> "fissura"
                it <= 3.0 -> "trinca"
                else -> "rachadura"
            }
        }
        val rel = relative?.lowercase(Locale.ROOT)
        return when (rel) {
            "muito fina", "fina" -> "fissura"
            "moderada" -> "trinca"
            "larga" -> "rachadura"
            else -> "fissura"
        }
    }

    private fun determineCause(orientation: String, context: CrackContextInfo): CauseResult {
        val moisture = context.mentionsMoisture
        val settlement = context.mentionsSettlement
        val local = context.localCategory
        return when {
            orientation == "horizontal" && (local == CrackLocationCategory.BASE_RODAPE || local == CrackLocationCategory.PLATIBANDA || moisture) -> {
                CauseResult(
                    key = CauseKey.HYGROSCOPIC,
                    description = "Movimentacao higroscopica (umidade) na base da parede",
                    interpretation = "Orientacao horizontal na ${context.normalizedLocal} somada ao contexto de umidade sugere movimentacao higroscopica."
                )
            }
            orientation == "horizontal" -> {
                CauseResult(
                    key = CauseKey.THERMAL,
                    description = "Variacoes termicas no pano da parede",
                    interpretation = "Fissura horizontal sem indicios fortes de umidade indica dilatacao/contracao termica."
                )
            }
            orientation == "vertical" -> {
                CauseResult(
                    key = CauseKey.OVERLOAD,
                    description = "Sobrecarga ou redistribuicao de cargas",
                    interpretation = "Abertura vertical alinhada ao plano pode decorrer de sobrecarga ou acomodacao estrutural."
                )
            }
            orientation == INCLINADA_LABEL && local == CrackLocationCategory.JUNTO_VAO -> {
                CauseResult(
                    key = CauseKey.OPENING_TENSION,
                    description = "Concentracao de tensoes nos cantos do vao",
                    interpretation = "Trinca inclinada emergindo do vao condiz com concentracao de tensoes nas esquadrias."
                )
            }
            orientation == INCLINADA_LABEL && (settlement || local == CrackLocationCategory.PROXIMO_ESTRUTURA || local == CrackLocationCategory.BASE_RODAPE) -> {
                CauseResult(
                    key = CauseKey.SETTLEMENT,
                    description = "Recalque diferencial ou cisalhamento no apoio",
                    interpretation = "Trinca inclinada com relato contextual sugere recalque diferencial/cisalhamento."
                )
            }
            orientation == INCLINADA_LABEL -> {
                CauseResult(
                    key = CauseKey.OPENING_TENSION,
                    description = "Concentracao de tensoes no painel",
                    interpretation = "Trinca inclinada sem contexto de recalque indica tensoes concentradas."
                )
            }
            else -> {
                CauseResult(
                    key = CauseKey.GENERIC,
                    description = "Movimentacoes construtivas diversas",
                    interpretation = "Contexto insuficiente para apontar causa unica; manter monitoramento."
                )
            }
        }
    }

    private fun normalizeOrientation(raw: String?): String {
        if (raw.isNullOrBlank()) return "indefinida"
        val lower = raw.lowercase(Locale.ROOT)
        return when {
            lower.contains("vertical") -> "vertical"
            lower.contains("horizontal") -> "horizontal"
            lower.contains("diag") || lower.contains("incl") -> INCLINADA_LABEL
            else -> "indefinida"
        }
    }

    private fun formatMm(value: Double): String {
        return String.format(Locale("pt", "BR"), "%.1f mm", value)
    }

    private fun buildAdvice(severity: String, cause: CauseKey): List<String> {
        // Mantemos apenas as três ações solicitadas, para todos os diagnósticos
        return listOf(
            "Registrar nova foto em 30 dias para monitorar evolucao.",
            "Consultar profissional habilitado para avaliacoes complementares.",
            "Revisar distribuicao de cargas e evitar sobrecargas adicionais."
        )
    }

    private data class CauseResult(
        val key: CauseKey,
        val description: String,
        val interpretation: String
    )

    private enum class CauseKey {
        HYGROSCOPIC,
        THERMAL,
        OVERLOAD,
        OPENING_TENSION,
        SETTLEMENT,
        GENERIC
    }
}



