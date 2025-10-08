package com.hitsu.patologiafacil.ui

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import coil.load
import com.hitsu.patologiafacil.R
import com.hitsu.patologiafacil.data.AnalysisEntity
import com.hitsu.patologiafacil.data.AnalysisResultJson
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class HistoryAdapter(
    private val inflater: LayoutInflater,
    private val onDetails: (AnalysisEntity) -> Unit,
    private val onDelete: (AnalysisEntity) -> Unit,
) : BaseAdapter() {

    private val items = mutableListOf<AnalysisEntity>()

    fun submit(newItems: List<AnalysisEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): AnalysisEntity = items[position]
    override fun getItemId(position: Int): Long = items[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.item_history, parent, false)
        val item = getItem(position)
        val parsed = AnalysisResultJson.fromJson(item.resultJson)
        val ctx = view.context

        fun <T : View> find(vararg ids: Int): T? {
            for (id in ids) {
                if (id != 0) {
                    val v = view.findViewById<T>(id)
                    if (v != null) return v
                }
            }
            return null
        }

        val img = find<ImageView>(
            R.id.imgCard,
            ctx.resources.getIdentifier("img", "id", ctx.packageName)
        )
        val tvTitle = find<TextView>(
            R.id.tvTitle,
            ctx.resources.getIdentifier("title", "id", ctx.packageName)
        )
        val tvLocation = find<TextView>(
            R.id.tvLocation,
            ctx.resources.getIdentifier("location", "id", ctx.packageName)
        )
        val tvDate = find<TextView>(
            R.id.tvDate,
            ctx.resources.getIdentifier("date", "id", ctx.packageName)
        )
        val tvSummary = find<TextView>(
            R.id.tvSummary,
            ctx.resources.getIdentifier("summary", "id", ctx.packageName),
            ctx.resources.getIdentifier("description", "id", ctx.packageName)
        )
        val badge = find<TextView>(
            R.id.badgeSeverity,
            ctx.resources.getIdentifier("badge", "id", ctx.packageName)
        )
        val btnDetails = find<View>(
            R.id.btnDetails,
            ctx.resources.getIdentifier("btnMore", "id", ctx.packageName)
        )
        val btnDelete = find<View>(
            R.id.btnDelete,
            ctx.resources.getIdentifier("btnRemove", "id", ctx.packageName)
        )

        val imageSource = parsed?.imagePath?.takeIf { it.isNotBlank() } ?: item.imageUri
        img?.load(imageSource)

        val causeTitle = parsed?.causaProvavel?.ifBlank { null }
        val titleText = causeTitle ?: item.topCause.ifBlank { ctx.getString(R.string.diagnosis_title) }
        tvTitle?.text = titleText

        val locationText = parsed?.localInformado?.ifBlank { null } ?: item.local
        tvLocation?.text = locationText

        val dateText = parsed?.generatedAt?.ifBlank { null }
        tvDate?.text = dateText ?: formatDatePtBr(item.date)

        tvSummary?.apply {
            val observations = parsed?.observacoes?.joinToString(" - ")?.takeIf { it.isNotBlank() }
            val interpretation = parsed?.interpretacaoContexto?.takeIf { it.isNotBlank() }
            text = observations ?: interpretation ?: extractSummary(item.resultJson)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        }

        badge?.apply {
            val sevNorm = normalizeSeverity(parsed?.severidade ?: item.severity)
            text = when (sevNorm) {
                "LEVE" -> "Baixa"
                "MODERADA" -> "Moderada"
                "GRAVE" -> "Alta"
                else -> parsed?.severidade ?: item.severity
            }
            setTextColor(ContextCompat.getColor(ctx, R.color.pf_onPrimary))
            when (sevNorm) {
                "LEVE" -> setBackgroundResource(R.drawable.pf_badge_light)
                "MODERADA" -> setBackgroundResource(R.drawable.pf_badge_moderate)
                "GRAVE" -> setBackgroundResource(R.drawable.pf_badge_critical)
                else -> setBackgroundResource(R.drawable.bg_badge)
            }
        }

        btnDetails?.setOnClickListener { onDetails(item) }
        btnDelete?.setOnClickListener { onDelete(item) }

        return view
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

    private fun formatDatePtBr(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy",
            "yyyy-MM-dd"
        )
        patterns.forEach { pattern ->
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(raw)
                if (date != null) {
                    val out = SimpleDateFormat("dd MMMM yyyy", Locale("pt", "BR"))
                    return out.format(date)
                }
            } catch (_: Exception) { }
        }
        return raw
    }

    private fun extractSummary(json: String?): String {
        if (json.isNullOrBlank()) return ""
        val parsed = AnalysisResultJson.fromJson(json)
        if (parsed != null) {
            val obs = parsed.observacoes.joinToString(" - ").takeIf { it.isNotBlank() }
            if (obs != null) return obs
            if (parsed.interpretacaoContexto.isNotBlank()) return parsed.interpretacaoContexto
        }
        return ""
    }
}

