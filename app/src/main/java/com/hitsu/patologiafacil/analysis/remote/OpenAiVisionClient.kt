package com.hitsu.patologiafacil.analysis.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.hitsu.patologiafacil.analysis.CrackContextInfo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class RemoteAnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)

object OpenAiVisionClient {

    private const val TAG = "PF-OpenAiVision"
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(90, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    fun analyze(imageBytes: ByteArray, context: CrackContextInfo, apiKey: String): RemoteDetection {
        val base64 = prepareBase64(imageBytes)
        val payload = buildPayload(base64, context)
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (ioe: IOException) {
            Log.e(TAG, "network error: ${ioe.message}")
            throw RemoteAnalysisException("Falha ao conectar com o analisador.", ioe)
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string()
                Log.w(TAG, "API error ${resp.code}: $errorBody")
                val message = when (resp.code) {
                    401 -> "Chave de API invalida ou ausente."
                    429 -> "Limite de uso do modelo atingido."
                    else -> "Resposta inesperada do modelo (${resp.code})."
                }
                throw RemoteAnalysisException(message)
            }
            val text = resp.body?.string() ?: throw RemoteAnalysisException("Resposta vazia do modelo.")
            try {
                return parseResponse(text)
            } catch (ex: JSONException) {
                Log.e(TAG, "json parse error", ex)
                throw RemoteAnalysisException("Formato de resposta invalido.", ex)
            }
        }
    }

    private fun buildPayload(imageBase64: String, context: CrackContextInfo): JSONObject {
        val systemPrompt = buildString {
            append("Voce e um assistente de visao que identifica fissuras, trincas e rachaduras em imagens de paredes.\n")
            append("Responda apenas em JSON seguindo o schema informado.\n")
            append("Classifique orientacao e dimensoes apenas se a imagem permitir escalas confiaveis.\n")
        }

        val userPrompt = buildString {
            append("Analise a imagem e informe se ha fissuras/trincas/rachaduras.\n")
            append("Contexto do usuario:\n")
            append("- Local informado: ")
            append(if (context.originalLocal.isBlank()) "nao informado" else context.originalLocal)
            append(" (categoria normalizada: ${context.normalizedLocal}).\n")
            append("- Tempo informado: ")
            append(if (context.originalTempo.isBlank()) "nao informado" else context.originalTempo)
            append(" (normalizacao: ${context.tempoDisplay}).\n")
            append("- Atividade aparente: ${context.activity}.\n")
            if (context.tempoHints.isNotEmpty()) {
                append("- Observacoes sobre tempo: ${context.tempoHints.joinToString("; ")}.\n")
            }
            append("Retorne notas breves quando nao houver escala ou a deteccao for incerta.")
        }

        val schema = JSONObject(
            """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "detected": {"type": "boolean"},
                "orientation": {
                  "type": "string",
                  "enum": ["horizontal", "vertical", "diagonal", "inclinada", "multipla", "indefinida"]
                },
                "has_scale": {"type": "boolean"},
                "width_mm": {"type": ["number", "null"]},
                "length_mm": {"type": ["number", "null"]},
                "relative_width": {
                  "type": ["string", "null"],
                  "enum": ["muito fina", "fina", "moderada", "larga", "indefinida", null]
                },
                "notes": {
                  "type": "array",
                  "items": {"type": "string"}
                }
              },
              "required": ["detected", "orientation", "has_scale" ]
            }
            """.trimIndent()
        )

        val systemMessage = JSONObject().apply {
            put("role", "system")
            put("content", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", systemPrompt)
                })
            })
        }

        val userMessage = JSONObject().apply {
            put("role", "user")
            put("content", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", userPrompt)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$imageBase64")
                    })
                })
            })
        }

        return JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", 0.0)
            put("messages", JSONArray().apply {
                put(systemMessage)
                put(userMessage)
            })
            put("response_format", JSONObject().apply {
                put("type", "json_schema")
                put("json_schema", JSONObject().apply {
                    put("name", "crack_detection")
                    put("schema", schema)
                })
            })
        }
    }

    private fun parseResponse(body: String): RemoteDetection {
        val root = JSONObject(body)
        val choices = root.optJSONArray("choices") ?: throw JSONException("choices ausente")
        if (choices.length() == 0) throw JSONException("choices vazio")
        val message = choices.getJSONObject(0).getJSONObject("message")
        val contentArray = message.optJSONArray("content")
        var payload: JSONObject? = null
        if (contentArray != null) {
            for (i in 0 until contentArray.length()) {
                val item = contentArray.getJSONObject(i)
                val type = item.optString("type")
                if (type == "output_json" && item.has("json")) {
                    payload = item.getJSONObject("json")
                    break
                }
                if (type == "text" && item.has("text")) {
                    val text = item.getString("text")
                    payload = JSONObject(text)
                    break
                }
            }
        }
        if (payload == null && message.has("content")) {
            val text = message.getString("content")
            payload = JSONObject(text)
        }
        val json = payload ?: throw JSONException("conteudo json nao encontrado")

        val notesArray = json.optJSONArray("notes")
        val notes = mutableListOf<String>()
        if (notesArray != null) {
            for (i in 0 until notesArray.length()) {
                notes += notesArray.optString(i)
            }
        }

        val width = if (json.isNull("width_mm")) null else json.optDouble("width_mm")
        val length = if (json.isNull("length_mm")) null else json.optDouble("length_mm")
        val relative = json.optString("relative_width", null)

        return RemoteDetection(
            detected = json.optBoolean("detected", false),
            orientation = json.optString("orientation", "indefinida"),
            hasScale = json.optBoolean("has_scale", false),
            widthMm = width,
            lengthMm = length,
            relativeWidth = relative,
            notes = notes,
            rawJson = json.toString()
        )
    }

    private fun prepareBase64(bytes: ByteArray): String {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw RemoteAnalysisException("Nao foi possivel decodificar a imagem.")
        val scaled = scaleBitmap(bitmap)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        if (scaled !== bitmap) {
            scaled.recycle()
        }
        val compressed = stream.toByteArray()
        return Base64.encodeToString(compressed, Base64.NO_WRAP)
    }

    private fun scaleBitmap(source: Bitmap): Bitmap {
        val maxSide = 1024
        val largest = maxOf(source.width, source.height)
        if (largest <= maxSide) return source
        val scale = maxSide.toDouble() / largest.toDouble()
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }
}
