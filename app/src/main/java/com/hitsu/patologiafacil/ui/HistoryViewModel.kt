package com.hitsu.patologiafacil.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitsu.patologiafacil.data.AnalysisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class HistoryViewModel(private val repo: AnalysisRepository) : ViewModel() {

    private val TAG = "PF-HistoryVM"

    private val _filter = MutableStateFlow(setOf("LEVE", "MODERADA", "GRAVE")) // default: tudo

    private val allFlow = repo.all().onEach { list ->
        Log.d(TAG, "repo.all() emitted size=${list.size}")
    }
    private val filterFlow = _filter.onEach { f ->
        Log.d(TAG, "filter changed -> $f")
    }

    val historicoFiltrado = allFlow
        .combine(filterFlow) { list, f ->
            val res = list.filter { e -> normalize(e.severity) in f }
            Log.d(TAG, "filtered size=${res.size} from total=${list.size}")
            res
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setFilter(f: Set<String>) { _filter.value = f }

    fun delete(id: Long) {
        Log.d(TAG, "delete($id)")
        viewModelScope.launch(Dispatchers.IO) { repo.delete(id) }
    }

    private fun normalize(value: String): String {
        val v = value.trim().lowercase(Locale.ROOT)
        return when {
            listOf("grave","alta","elevada","severe","high","crítica","critica","crítico","critico","critical")
                .any { v.contains(it) } -> "GRAVE"
            listOf("moderada","média","media","moderate","medium")
                .any { v.contains(it) } -> "MODERADA"
            listOf("leve","baixa","low","minor")
                .any { v.contains(it) } -> "LEVE"
            else -> value.uppercase(Locale.ROOT)
        }
    }
}
