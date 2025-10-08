package com.hitsu.patologiafacil.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.hitsu.patologiafacil.BuildConfig
import com.hitsu.patologiafacil.R
import com.hitsu.patologiafacil.data.AnalysisEntity
import com.hitsu.patologiafacil.data.AnalysisRepository
import com.hitsu.patologiafacil.data.AnalysisResultJson
import com.hitsu.patologiafacil.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class HistoryDetailDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_history_details, null, false)

        val img = view.findViewById<ImageView>(R.id.imgDetail)
        val tvTitle = view.findViewById<TextView>(R.id.tvDetailTitle)
        val tvDate = view.findViewById<TextView>(R.id.tvDetailDate)
        val tvSeverity = view.findViewById<TextView>(R.id.tvDetailSeverity)
        val tvDesc = view.findViewById<TextView>(R.id.tvDetailDesc)
        val tvCauses = view.findViewById<TextView>(R.id.tvDetailCauses)

        val id = requireArguments().getLong(ARG_ID)
        val repo = AnalysisRepository(AppDatabase.instance(ctx).analysisDao())

        lifecycleScope.launch(Dispatchers.IO) {
            val list = repo.listOnce()
            val item = list.firstOrNull { it.id == id }
            withContext(Dispatchers.Main) {
                if (item != null) bind(item, img, tvTitle, tvDate, tvSeverity, tvDesc, tvCauses)
            }
        }

        // Horizontal action buttons in layout
        view.findViewById<View>(R.id.btnOk)?.setOnClickListener { dismiss() }

        view.findViewById<View>(R.id.btnDelete)?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                repo.delete(id)
                withContext(Dispatchers.Main) { dismiss() }
            }
        }

        view.findViewById<View>(R.id.btnShare)?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val list = repo.listOnce()
                val item = list.firstOrNull { it.id == id }
                if (item != null) {
                    val result = AnalysisResultJson.fromJson(item.resultJson)
                    if (result != null) {
                        // Monta resumo completo com tópicos e emojis
                        val sevDisp = when (normalizeSeverity(result.severidade)) {
                            "LEVE" -> "Baixa"
                            "MODERADA" -> "Moderada"
                            "GRAVE" -> "Alta"
                            else -> result.severidade
                        }
                        val title = (result.causaProvavel.ifBlank { null }) ?: item.topCause
                        val date = result.generatedAt.ifBlank { item.date }
                        val bulletsDesc = mutableListOf<String>().apply {
                            add("Tipo: ${result.tipo.ifBlank { "-" }}")
                            if (sevDisp.isNotBlank()) add("Severidade: $sevDisp")
                            if (result.orientacao.isNotBlank()) add("Orientacao: ${result.orientacao}")
                            val w = result.larguraMm.trim(); if (w.isNotEmpty() && !w.equals("nao estimavel", true)) add("Largura: $w")
                            val l = result.comprimentoMm.trim(); if (l.isNotEmpty() && !l.equals("nao estimavel", true)) add("Comprimento: $l")
                            val loc = result.localInformado.ifBlank { result.localOriginal }.trim(); if (loc.isNotBlank() && !loc.equals("nao informado", true)) add("Local: $loc")
                            val tmp = result.tempoInformado.ifBlank { result.tempoOriginal }.trim(); if (tmp.isNotBlank() && !tmp.equals("nao informado", true)) add("Tempo: $tmp")
                        }
                        val bulletsDescText = bulletsDesc.joinToString("\n") { "\uD83D\uDD35 $it" }
                        val causesText = buildString {
                            result.causaProvavel.takeIf { it.isNotBlank() }?.let { append("\u26A0\uFE0F Causa provavel: $it\n") }
                            result.interpretacaoContexto.takeIf { it.isNotBlank() }?.let { append("\u26A0\uFE0F $it") }
                        }.trim()

                        val summary = buildString {
                            append("\uD83D\uDDFE Detalhes do diagnostico\n\n") // 🧾
                            append("\uD83D\uDCCC Titulo: ${title.ifBlank { "-" }}\n") // 📌
                            append("\uD83D\uDCC5 Data: ${date.ifBlank { "-" }}\n") // 🗓️
                            append("\uD83C\uDFAF Criticidade: ${sevDisp.ifBlank { "-" }}\n\n") // 🎯
                            append("\uD83E\uDDEE Descricao\n") // 🧩
                            append(bulletsDescText)
                            if (causesText.isNotBlank()) {
                                append("\n\n\u26A0\uFE0F Causas provaveis\n") // ⚠️
                                append(causesText)
                            }
                        }

                        // Include image if possible (file or copy from content URI to cache)
                        val shareUri = run {
                            val path = result.imagePath
                            val f = java.io.File(path)
                            if (f.exists()) {
                                val authority = ctx.packageName + ".provider"
                                androidx.core.content.FileProvider.getUriForFile(ctx, authority, f)
                            } else {
                                val parsed = runCatching { android.net.Uri.parse(path) }.getOrNull()
                                if (parsed != null) {
                                    val cacheDir = java.io.File(ctx.cacheDir, "share").apply { if (!exists()) mkdirs() }
                                    val outFile = java.io.File(cacheDir, "diag_${System.currentTimeMillis()}.jpg")
                                    val copied = runCatching {
                                        ctx.contentResolver.openInputStream(parsed)?.use { input ->
                                            java.io.FileOutputStream(outFile).use { output -> input.copyTo(output) }
                                            outFile
                                        }
                                    }.getOrNull()
                                    if (copied != null && copied.exists()) {
                                        val authority = ctx.packageName + ".provider"
                                        androidx.core.content.FileProvider.getUriForFile(ctx, authority, copied)
                                    } else null
                                } else null
                            }
                        }

                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = if (shareUri != null) "image/*" else "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, summary)
                            if (shareUri != null) {
                                putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                clipData = android.content.ClipData.newUri(ctx.contentResolver, "image", shareUri)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            startActivity(android.content.Intent.createChooser(intent, getString(R.string.share_diagnosis)))
                        }
                    }
                }
            }
        }

        return MaterialAlertDialogBuilder(ctx)
            .setView(view)
            .create()
    }

    private fun bind(
        entity: AnalysisEntity,
        img: ImageView,
        tvTitle: TextView,
        tvDate: TextView,
        tvSeverity: TextView,
        tvDesc: TextView,
        tvCauses: TextView
    ) {
        val result = AnalysisResultJson.fromJson(entity.resultJson)
        val imageSource = result?.imagePath?.takeIf { it.isNotBlank() } ?: entity.imageUri
        img.load(imageSource)

        val title = result?.causaProvavel?.ifBlank { null } ?: entity.topCause
        tvTitle.text = title.ifBlank { getString(R.string.diagnosis_title) }
        tvDate.text = result?.generatedAt ?: entity.date

        val sevNorm = normalizeSeverity(result?.severidade ?: entity.severity)
        val display = when (sevNorm) {
            "LEVE" -> "Baixa"
            "MODERADA" -> "Moderada"
            "GRAVE" -> "Alta"
            else -> result?.severidade ?: entity.severity
        }
        tvSeverity.text = display
        when (sevNorm) {
            "LEVE" -> tvSeverity.setBackgroundResource(R.drawable.pf_badge_light)
            "MODERADA" -> tvSeverity.setBackgroundResource(R.drawable.pf_badge_moderate)
            "GRAVE" -> tvSeverity.setBackgroundResource(R.drawable.pf_badge_critical)
            else -> tvSeverity.setBackgroundResource(R.drawable.bg_badge)
        }

        // Descricao em topicos com emojis e sem "nao estimavel"
        val bulletsDesc = mutableListOf<String>()
        if (result != null) {
            val sevDisp = when (normalizeSeverity(result.severidade)) {
                "LEVE" -> "Baixa"
                "MODERADA" -> "Moderada"
                "GRAVE" -> "Alta"
                else -> result.severidade
            }
            bulletsDesc += "Tipo: ${result.tipo.ifBlank { "-" }}"
            if (sevDisp.isNotBlank()) bulletsDesc += "Severidade: $sevDisp"
            if (result.orientacao.isNotBlank()) bulletsDesc += "Orientacao: ${result.orientacao}"
            val w = result.larguraMm.trim()
            if (w.isNotEmpty() && !w.equals("nao estimavel", true)) bulletsDesc += "Largura: $w"
            val l = result.comprimentoMm.trim()
            if (l.isNotEmpty() && !l.equals("nao estimavel", true)) bulletsDesc += "Comprimento: $l"
            val local = result.localInformado.ifBlank { result.localOriginal }.trim()
            if (local.isNotBlank() && !local.equals("nao informado", true)) bulletsDesc += "Local: $local"
            val tempo = result.tempoInformado.ifBlank { result.tempoOriginal }.trim()
            if (tempo.isNotBlank() && !tempo.equals("nao informado", true)) bulletsDesc += "Tempo: $tempo"
        }
        tvDesc.text = bulletsDesc.joinToString("\n") { "\uD83D\uDD35 $it" }

        tvCauses.text = buildString {
            result?.causaProvavel?.takeIf { it.isNotBlank() }?.let { append("\u26A0\uFE0F Causa provavel: $it\n") }
            result?.interpretacaoContexto?.takeIf { it.isNotBlank() }?.let { append("\u26A0\uFE0F $it") }
        }.trim()
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

    companion object {
        private const val ARG_ID = "id"
        fun newInstance(id: Long): HistoryDetailDialog = HistoryDetailDialog().apply {
            arguments = Bundle().apply { putLong(ARG_ID, id) }
        }
    }
}
