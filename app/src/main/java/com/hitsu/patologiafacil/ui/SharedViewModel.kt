package com.hitsu.patologiafacil.ui

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitsu.patologiafacil.BuildConfig
import com.hitsu.patologiafacil.analysis.CrackContextNormalizer
import com.hitsu.patologiafacil.analysis.CrackSummaryBuilder
import com.hitsu.patologiafacil.analysis.remote.OpenAiVisionClient
import com.hitsu.patologiafacil.analysis.remote.RemoteAnalysisException
import com.hitsu.patologiafacil.domain.AnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SharedViewModel : ViewModel() {

    // Mantemos os nomes para compatibilidade com reflexão usada em outras telas
    private val _imagePath = MutableLiveData<String?>(null)
    val imagePath: LiveData<String?> = _imagePath

    private val _lastResult = MutableLiveData<AnalysisResult?>(null)
    val lastResult: LiveData<AnalysisResult?> = _lastResult

    private val _analysisMessage = MutableLiveData<String?>(null)
    val analysisMessage: LiveData<String?> = _analysisMessage

    private val _isAnalyzing = MutableLiveData(false)
    val isAnalyzing: LiveData<Boolean> = _isAnalyzing

    // Novo: sinaliza erro de conexão para a UI reagir (voltar para captura)
    private val _connectionError = MutableLiveData(false)
    val connectionError: LiveData<Boolean> = _connectionError

    fun setSelectedImage(path: String) { _imagePath.value = path }
    fun setImagePath(path: String) = setSelectedImage(path)

    fun runAnalysis(resolver: ContentResolver, localInput: String?, tempoInput: String?) {
        val path = _imagePath.value
        if (path.isNullOrBlank()) {
            _analysisMessage.value = "Selecione uma imagem antes de analisar."
            return
        }
        val apiKey = BuildConfig.OPENAI_API_KEY.ifBlank { System.getenv("OPENAI_API_KEY") ?: "" }
        if (apiKey.isBlank()) {
            _analysisMessage.value = "Configure a chave da API para executar a analise."
            return
        }
        _isAnalyzing.value = true
        _lastResult.postValue(null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = resolver.openInputStream(Uri.parse(path))?.use { it.readBytes() }
                if (bytes == null) {
                    _analysisMessage.postValue("Falha ao ler a imagem selecionada.")
                    return@launch
                }
                val contextInfo = CrackContextNormalizer.normalize(localInput, tempoInput)
                val detection = OpenAiVisionClient.analyze(bytes, contextInfo, apiKey)
                val result = CrackSummaryBuilder.build(detection, contextInfo, path)
                _lastResult.postValue(result)
            } catch (ex: RemoteAnalysisException) {
                val msg = ex.message ?: "Falha na analise, tente novamente."
                val isConn = (ex.cause is java.io.IOException) ||
                        msg.contains("conectar", true) || msg.contains("conex", true)
                if (isConn) {
                    _connectionError.postValue(true)
                    _analysisMessage.postValue("Erro de conexão")
                } else {
                    _analysisMessage.postValue(msg)
                }
            } catch (ex: Exception) {
                Log.e("PF-SharedVM", "analysis error", ex)
                _analysisMessage.postValue("Falha na analise, tente novamente.")
            } finally {
                _isAnalyzing.postValue(false)
            }
        }
    }

    fun consumeAnalysisMessage() { _analysisMessage.value = null }
    fun consumeConnectionError() { _connectionError.value = false }

    fun clear() {
        _imagePath.postValue(null)
        _lastResult.postValue(null)
        _analysisMessage.postValue(null)
        _connectionError.postValue(false)
    }
}

