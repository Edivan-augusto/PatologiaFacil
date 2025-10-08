package com.hitsu.patologiafacil.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import coil.load
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.hitsu.patologiafacil.R
import com.hitsu.patologiafacil.data.AnalysisRepository
import com.hitsu.patologiafacil.data.AppDatabase
import com.hitsu.patologiafacil.domain.AnalysisResult
import java.util.Locale

class DiagnosisFragment : Fragment(R.layout.fragment_diagnosis) {

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var vm: DiagnosisViewModel
    private var progressOverlay: View? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val dao = AppDatabase.instance(requireContext()).analysisDao()
                val repo = AnalysisRepository(dao)
                @Suppress("UNCHECKED_CAST")
                return DiagnosisViewModel(repo) as T
            }
        })[DiagnosisViewModel::class.java]

        val root = view as ViewGroup
        val imgAnalyzed = view.findViewById<ImageView>(R.id.imgAnalyzed)
        val txtCharacterization = view.findViewById<TextView>(R.id.txtCharacterization)
        val txtDate = view.findViewById<TextView>(R.id.txtDate)
        val txtSeverity = view.findViewById<TextView>(R.id.txtSeverity)
        val listCauses = view.findViewById<LinearLayout>(R.id.listCauses)
        val listAdvice = view.findViewById<LinearLayout>(R.id.listAdvice)
        val btnSave = view.findViewById<View>(R.id.btnSave)
        val btnShare = view.findViewById<View>(R.id.btnShare)

        // Inicia desabilitado até existir resultado e haver detecção
        btnSave.isEnabled = false
        btnShare.isEnabled = false
        btnSave.alpha = 0.5f
        btnShare.alpha = 0.5f

        fun addBullet(container: LinearLayout, text: String, emoji: String) {
            val clean = text.trim()
            if (clean.isEmpty()) return
            val tv = TextView(requireContext())
            tv.text = "$emoji $clean"
            tv.textSize = 14f
            tv.setPadding(0, resources.getDimensionPixelSize(R.dimen.space_4), 0, 0)
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.pf_onSurfaceVariant))
            container.addView(tv)
        }

        fun bindResult(res: AnalysisResult?) {
            val hasData = res != null
            listCauses.isVisible = hasData
            listAdvice.isVisible = hasData

            if (res == null) {
                txtCharacterization.text = "Processando analise..."
                txtSeverity.text = "-"
                txtDate.text = "-"
                return
            }

            imgAnalyzed.load(res.imagePath)

            // Ações: se não detectar, "Salvar" vira "Voltar"; compartilhar permanece desabilitado
            if (res.detected) {
                btnSave.isEnabled = true
                btnSave.alpha = 1f
                try { (btnSave as? android.widget.TextView)?.text = getString(R.string.btn_save_history) } catch (_: Exception) {}
                btnShare.isEnabled = true
                btnShare.alpha = 1f
            } else {
                btnSave.isEnabled = true
                btnSave.alpha = 1f
                try { (btnSave as? android.widget.TextView)?.text = getString(R.string.btn_back) } catch (_: Exception) {}
                btnShare.isEnabled = false
                btnShare.alpha = 0.5f
            }

            // Caracterização em tópicos com emotes; filtra campos "nao estimavel"
            val bulletsChar = mutableListOf<String>()
            if (res.detected) {
                val sevNormDisp = when (normalizeSeverity(res.severidade)) {
                    "LEVE" -> "Baixa"
                    "MODERADA" -> "Moderada"
                    "GRAVE" -> "Alta"
                    else -> res.severidade.ifBlank { null }
                }
                bulletsChar += "Tipo: ${res.tipo.ifBlank { "-" }}"
                sevNormDisp?.let { bulletsChar += "Severidade: $it" }
                if (res.orientacao.isNotBlank()) bulletsChar += "Orientacao: ${res.orientacao}"
                val width = res.larguraMm.trim()
                if (width.isNotBlank() && !width.equals("nao estimavel", ignoreCase = true)) {
                    bulletsChar += "Largura: $width"
                }
                val length = res.comprimentoMm.trim()
                if (length.isNotBlank() && !length.equals("nao estimavel", ignoreCase = true)) {
                    bulletsChar += "Comprimento: $length"
                }
                val local = res.localInformado.ifBlank { res.localOriginal }.trim()
                if (local.isNotBlank() && !local.equals("nao informado", true)) bulletsChar += "Local: $local"
                val tempo = res.tempoInformado.ifBlank { res.tempoOriginal }.trim()
                if (tempo.isNotBlank() && !tempo.equals("nao informado", true)) bulletsChar += "Tempo: $tempo"
            } else {
                bulletsChar += "Fissura nao detectada."
            }
            txtCharacterization.text = bulletsChar.joinToString(separator = "\n") { line -> "\uD83D\uDD35 $line" } // 🔵
            txtCharacterization.textSize = 15f
            txtCharacterization.typeface = Typeface.SANS_SERIF
            txtDate.text = res.generatedAt.ifBlank { "-" }

            val severityNorm = normalizeSeverity(res.severidade)
            val severityDisplay = when (severityNorm) {
                "LEVE" -> "Baixa"
                "MODERADA" -> "Moderada"
                "GRAVE" -> "Alta"
                else -> if (res.detected) res.severidade else "-"
            }
            txtSeverity.text = severityDisplay
            when (severityNorm) {
                "LEVE" -> txtSeverity.setBackgroundResource(R.drawable.pf_badge_light)
                "MODERADA" -> txtSeverity.setBackgroundResource(R.drawable.pf_badge_moderate)
                "GRAVE" -> txtSeverity.setBackgroundResource(R.drawable.pf_badge_critical)
                else -> txtSeverity.setBackgroundResource(R.drawable.bg_badge)
            }

            listCauses.removeAllViews()
            if (res.detected) {
                val causa = res.causaProvavel.ifBlank { "-" }
                addBullet(listCauses, "Causa provavel: $causa", "\u26A0\uFE0F") // ⚠️
                val interp = res.interpretacaoContexto.trim()
                if (interp.isNotBlank()) addBullet(listCauses, interp, "\u26A0\uFE0F")
            } else {
                addBullet(listCauses, "Fissura nao detectada.", "\u26A0\uFE0F")
            }

            listAdvice.removeAllViews()
            // Apenas as ações selecionadas
            val keep = setOf(
                "registrar nova foto em 30 dias",
                "consultar profissional habilitado para avaliacoes complementares",
                "revisar distribuicao de cargas e evitar sobrecargas adicionais"
            )
            res.advice
                .filter { a ->
                    val t = a.trim().lowercase(Locale.ROOT)
                    keep.any { key -> t.contains(key) }
                }
                .distinct()
                .forEach { addBullet(listAdvice, it, "\uD83D\uDD35") } // 🔵
        }

        bindResult(sharedViewModel.lastResult.value)

        sharedViewModel.lastResult.observe(viewLifecycleOwner) { res ->
            bindResult(res)
        }

        sharedViewModel.analysisMessage.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                sharedViewModel.consumeAnalysisMessage()
            }
        }

        // Em erro de conexão, volta para a tela de captura
        sharedViewModel.connectionError.observe(viewLifecycleOwner) { isErr ->
            if (isErr == true) {
                sharedViewModel.consumeConnectionError()
                // Tenta voltar ao fragmento de captura
                try {
                    findNavController().popBackStack(R.id.captureFragment, false)
                } catch (_: Exception) {
                    try { findNavController().navigate(R.id.captureFragment) } catch (_: Exception) {}
                }
            }
        }

        sharedViewModel.isAnalyzing.observe(viewLifecycleOwner) { analyzing ->
            toggleOverlay(root, analyzing == true)
        }

        btnSave.setOnClickListener {
            val current = sharedViewModel.lastResult.value
            if (current?.detected == true) {
                val sanitized = sanitizeResult(current)
                vm.saveCurrentAnalysis(requireContext(), sanitized, userAction = true)
                sharedViewModel.clear()
                findNavController().navigate(R.id.historyFragment)
            } else {
                // Botão atua como Voltar para captura
                try {
                    findNavController().popBackStack(R.id.captureFragment, false)
                } catch (_: Exception) {
                    try { findNavController().navigate(R.id.captureFragment) } catch (_: Exception) {}
                }
            }
        }

        btnShare.setOnClickListener {
            val current = sharedViewModel.lastResult.value
            if (current == null || current.detected != true) {
                Toast.makeText(requireContext(), "Nenhuma fissura detectada para compartilhar.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            vm.shareCurrentAnalysis(requireContext(), current)
        }

        observeImagePathForPreview(imgAnalyzed)
    }

    private fun toggleOverlay(parent: ViewGroup, show: Boolean) {
        if (show) {
            if (progressOverlay == null) {
                progressOverlay = buildOverlay(parent)
                parent.addView(progressOverlay)
            }
        } else {
            progressOverlay?.let { parent.removeView(it) }
            progressOverlay = null
        }
    }

    private fun buildOverlay(parent: ViewGroup): View {
        val overlay = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#88000000"))
            isClickable = true
            isFocusable = true
        }
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val indicator = CircularProgressIndicator(requireContext()).apply {
            isIndeterminate = true
        }
        val label = TextView(requireContext()).apply {
            text = "Analisando imagem..."
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.SANS_SERIF
            setPadding(0, resources.getDimensionPixelSize(R.dimen.space_8), 0, 0)
            gravity = Gravity.CENTER
        }
        box.addView(indicator)
        box.addView(label)
        overlay.addView(box, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return overlay
    }

    private fun sanitize(text: String?): String = text?.replace('\uFFFD', ' ')?.trim().orEmpty()

    private fun sanitizeResult(res: AnalysisResult): AnalysisResult {
        return res.copy(
            tipo = sanitize(res.tipo),
            orientacao = sanitize(res.orientacao),
            larguraMm = sanitize(res.larguraMm),
            comprimentoMm = sanitize(res.comprimentoMm),
            causaProvavel = sanitize(res.causaProvavel),
            severidade = sanitize(res.severidade),
            observacoes = res.observacoes.map(::sanitize),
            localInformado = sanitize(res.localInformado),
            localOriginal = sanitize(res.localOriginal),
            tempoInformado = sanitize(res.tempoInformado),
            tempoOriginal = sanitize(res.tempoOriginal),
            interpretacaoContexto = sanitize(res.interpretacaoContexto),
            atividadeAparente = sanitize(res.atividadeAparente),
            characterization = sanitize(res.characterization),
            advice = res.advice.map(::sanitize)
        )
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

    private fun observeImagePathForPreview(target: ImageView) {
        try {
            val method = sharedViewModel::class.java.methods.firstOrNull {
                it.name == "getImagePath" && it.returnType.name.contains("LiveData")
            }
            val liveData = if (method != null) method.invoke(sharedViewModel) as? androidx.lifecycle.LiveData<String?> else null
            liveData?.observe(viewLifecycleOwner) { path ->
                if (!path.isNullOrBlank()) target.load(path)
            }
        } catch (_: Exception) { }

        try {
            val field = sharedViewModel::class.java.getDeclaredField("_imagePath")
            field.isAccessible = true
            val liveData = field.get(sharedViewModel) as? androidx.lifecycle.MutableLiveData<String?>
            liveData?.observe(viewLifecycleOwner) { path ->
                if (!path.isNullOrBlank()) target.load(path)
            }
        } catch (_: Exception) { }
    }
}
