package com.hitsu.patologiafacil.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import android.content.ClipData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitsu.patologiafacil.BuildConfig
import com.hitsu.patologiafacil.data.AnalysisEntity
import com.hitsu.patologiafacil.data.AnalysisRepository
import com.hitsu.patologiafacil.data.AnalysisResultJson
import com.hitsu.patologiafacil.domain.AnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

class DiagnosisViewModel(
    private val repo: AnalysisRepository
) : ViewModel() {

    private val TAG = "PF-DiagnosisVM"

    fun saveCurrentAnalysis(
        context: Context,
        result: AnalysisResult?,
        userAction: Boolean = false
    ) {
        if (!userAction) return
        if (result == null) {
            Toast.makeText(context, "Nenhuma análise carregada.", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (!result.detected) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Nenhuma fissura detectada para salvar.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            try {
                val persistedPath = try {
                    persistImage(context, result.imagePath)
                } catch (t: Throwable) {
                    Log.w(TAG, "persistImage failed: ${'$'}{t.message}")
                    result.imagePath
                }

                val normalizedSeverity = normalizeSeverity(result.severidade)
                val normalized = result.copy(
                    imagePath = persistedPath,
                    severidade = normalizedSeverity
                )

                val json = AnalysisResultJson.toJson(normalized)
                val key = makeContentKey(persistedPath, normalized)
                val dup = runCatching { repo.countByKey(key) }.getOrElse {
                    Log.w(TAG, "countByKey error: ${'$'}{it.message}")
                    0
                }

                if (dup > 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Este diagnóstico já está salvo.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val entity = AnalysisEntity(
                    imageUri = persistedPath,
                    local = normalized.localInformado.ifBlank { normalized.localOriginal },
                    tempoEvento = key,
                    date = normalized.generatedAt,
                    resultJson = json,
                    severity = normalizedSeverity,
                    topCause = normalized.causaProvavel
                )

                repo.insert(entity)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Salvo no histórico.", Toast.LENGTH_SHORT).show()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "save error", t)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Falha ao salvar diagnóstico.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun shareCurrentAnalysis(context: Context, result: AnalysisResult?) {
        if (result == null) {
            Toast.makeText(context, "Nenhuma analise para compartilhar.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!result.detected) {
            Toast.makeText(context, "Nenhuma fissura detectada para compartilhar.", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val file = runCatching {
                val path = result.imagePath
                if (path.isBlank()) {
                    null
                } else {
                    val candidate = File(path)
                    if (candidate.exists()) {
                        candidate
                    } else {
                        val uri = runCatching { Uri.parse(path) }.getOrNull()
                        if (uri == null) {
                            null
                        } else {
                            val cacheDir = File(context.cacheDir, "share").apply { if (!exists()) mkdirs() }
                            val out = File(cacheDir, "diag_${System.currentTimeMillis()}.jpg")
                            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(out).use { output -> input.copyTo(output) }
                                out
                            }
                            copied
                        }
                    }
                }
            }.getOrElse { null }

            val tipo = result.tipo.ifBlank { "-" }
            val severidade = result.severidade.ifBlank { "-" }
            val orientacao = result.orientacao.ifBlank { "-" }
            val largura = result.larguraMm.ifBlank { "nao estimavel" }
            val comprimento = result.comprimentoMm.ifBlank { "nao estimavel" }
            val causa = result.causaProvavel.ifBlank { "-" }
            val local = result.localInformado.ifBlank { "nao informado" }
            val tempo = result.tempoInformado.ifBlank { "nao informado" }

            val summary = buildString {
                append("\uD83D\uDDFE Detalhes do diagnostico\n\n") // 🧾
                append("\uD83D\uDCCC Titulo: ${causa.ifBlank { "-" }}\n") // 📌
                append("\uD83D\uDCC5 Data: ${result.generatedAt.ifBlank { "-" }}\n") // 🗓️
                append("\uD83C\uDFAF Criticidade: ${severidade.ifBlank { "-" }}\n\n") // 🎯

                append("\uD83E\uDDEE Descricao\n") // 🧩
                // Bullets com círculo azul; omitimos valores "nao estimavel"
                append("\uD83D\uDD35 Tipo: ${tipo}\n")
                append("\uD83D\uDD35 Orientacao: ${orientacao}\n")
                if (!largura.equals("nao estimavel", true) && largura.isNotBlank())
                    append("\uD83D\uDD35 Largura: ${largura}\n")
                if (!comprimento.equals("nao estimavel", true) && comprimento.isNotBlank())
                    append("\uD83D\uDD35 Comprimento: ${comprimento}\n")
                if (!local.equals("nao informado", true) && local.isNotBlank())
                    append("\uD83D\uDD35 Local: ${local}\n")
                if (!tempo.equals("nao informado", true) && tempo.isNotBlank())
                    append("\uD83D\uDD35 Tempo: ${tempo}\n")

                if (causa.isNotBlank()) {
                    append("\n\u26A0\uFE0F Causas provaveis\n") // ⚠️
                    append("\u26A0\uFE0F ${causa}\n")
                }
            }

            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, summary)
                    if (file != null && file.exists()) {
                        val authority = context.packageName + ".provider"
                        val uri = FileProvider.getUriForFile(context, authority, file)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = "image/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newUri(context.contentResolver, "image", uri)
                    } else {
                        type = "text/plain"
                    }
                }
                context.startActivity(Intent.createChooser(intent, "Compartilhar diagnostico"))
            }
        }
    }

    private fun normalizeSeverity(value: String): String {
        val v = value.trim().lowercase(Locale.ROOT)
        return when {
            listOf("grave", "alta", "elevada", "critical", "critica", "critico").any { v.contains(it) } -> "GRAVE"
            listOf("moderada", "media", "moderate").any { v.contains(it) } -> "MODERADA"
            listOf("leve", "baixa", "low").any { v.contains(it) } -> "LEVE"
            else -> value.uppercase(Locale.ROOT)
        }
    }

    private fun persistImage(context: Context, sourcePath: String): String {
        if (sourcePath.isBlank()) return sourcePath
        val src = File(sourcePath)
        if (!src.exists()) return sourcePath
        val dstDir = File(context.filesDir, "images")
        if (!dstDir.exists()) dstDir.mkdirs()
        val dst = File(dstDir, src.name)
        src.inputStream().use { input ->
            FileOutputStream(dst).use { output -> input.copyTo(output) }
        }
        return dst.absolutePath
    }

    private fun makeContentKey(path: String, result: AnalysisResult): String {
        val imgBytes = runCatching {
            val f = File(path)
            if (f.exists()) f.readBytes() else ByteArray(0)
        }.getOrElse { ByteArray(0) }
        val meta = "|${'$'}{result.tipo}|${result.orientacao}|${result.localInformado}|${result.causaProvavel}|${result.severidade}|${result.generatedAt}".toByteArray()
        return sha256(imgBytes + meta)
    }

    private fun sha256(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(data).joinToString("") { "%02x".format(it) }
    }
}
