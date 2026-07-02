package com.example.network

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // Retrieve default list of models for each provider as a fallback
    fun getDefaultModels(provider: String): List<String> {
        return when (provider) {
            "Google" -> listOf(
                "gemini-2.5-flash",
                "gemini-2.5-pro",
                "gemini-1.5-flash",
                "gemini-1.5-pro"
            )
            "OpenRouter" -> listOf(
                "google/gemini-2.5-flash",
                "deepseek/deepseek-r1-distill-llama-70b",
                "meta-llama/llama-3.3-70b-instruct",
                "mistralai/mistral-large-2411",
                "qwen/qwen-2.5-72b-instruct"
            )
            "Nvidia NIM" -> listOf(
                "meta/llama-3.1-405b-instruct",
                "nvidia/llama-3.1-nemotron-70b-instruct",
                "deepseek/deepseek-r1"
            )
            "Mistral" -> listOf(
                "mistral-large-latest",
                "mistral-medium-latest",
                "mistral-small-latest",
                "open-mixtral-8x22b"
            )
            "DeepSeek" -> listOf(
                "deepseek-chat",
                "deepseek-reasoner"
            )
            "Qwen" -> listOf(
                "qwen-turbo",
                "qwen-plus",
                "qwen-max"
            )
            "Ollama" -> listOf(
                "llama3",
                "deepseek-r1",
                "mistral",
                "gemma2",
                "qwen2.5-coder"
            )
            else -> emptyList()
        }
    }

    // Fetch models dynamically from endpoints
    suspend fun fetchModels(provider: String, apiKey: String, customUrl: String = ""): List<String> {
        val url = when (provider) {
            "Google" -> "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            "OpenRouter" -> "https://openrouter.ai/api/v1/models"
            "Nvidia NIM" -> "https://integrate.api.nvidia.com/v1/models"
            "Mistral" -> "https://api.mistral.ai/v1/models"
            "DeepSeek" -> "https://api.deepseek.com/models"
            "Qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1/models"
            "Ollama" -> {
                val base = if (customUrl.isNotBlank()) customUrl else "http://localhost:11434"
                if (base.endsWith("/")) "${base}api/tags" else "$base/api/tags"
            }
            else -> return getDefaultModels(provider)
        }

        val requestBuilder = Request.Builder().url(url)
        
        // Headers
        when (provider) {
            "OpenRouter" -> {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                requestBuilder.addHeader("HTTP-Referer", "https://github.com/google/githelp")
                requestBuilder.addHeader("X-Title", "GitHelp")
            }
            "Nvidia NIM", "Mistral", "DeepSeek", "Qwen" -> {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
        }

        try {
            val response = executeRequest(requestBuilder.build())
            val bodyString = response.body?.string() ?: return getDefaultModels(provider)
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed model fetch for $provider: ${response.code} $bodyString")
                return getDefaultModels(provider)
            }

            return if (provider == "Ollama") {
                // Parse Ollama tags list which has format {"models": [{"name": "llama3"}]}
                parseOllamaModels(bodyString)
            } else if (provider == "Google") {
                // Google formats list of models with [{"name": "models/gemini-1.5-flash"}]
                parseGoogleModels(bodyString)
            } else {
                // OpenAI model standard format
                val adapter = moshi.adapter(ModelListResponse::class.java)
                val modelList = adapter.fromJson(bodyString)
                modelList?.data?.map { it.id } ?: getDefaultModels(provider)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching models for $provider", e)
            return getDefaultModels(provider)
        }
    }

    private fun parseOllamaModels(json: String): List<String> {
        return try {
            val mapAdapter = moshi.adapter(Map::class.java)
            val root = mapAdapter.fromJson(json) as? Map<*, *>
            val models = root?.get("models") as? List<*>
            models?.mapNotNull {
                val modelMap = it as? Map<*, *>
                modelMap?.get("name")?.toString()
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseGoogleModels(json: String): List<String> {
        return try {
            val mapAdapter = moshi.adapter(Map::class.java)
            val root = mapAdapter.fromJson(json) as? Map<*, *>
            val models = root?.get("models") as? List<*>
            models?.mapNotNull {
                val modelMap = it as? Map<*, *>
                val rawName = modelMap?.get("name")?.toString() ?: ""
                if (rawName.startsWith("models/")) {
                    rawName.substringAfter("models/")
                } else {
                    rawName
                }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Connection test
    suspend fun testConnection(provider: String, apiKey: String, model: String, customUrl: String = ""): Boolean {
        return try {
            val responseText = chatCompletion(
                provider = provider,
                apiKey = apiKey,
                model = model,
                systemPrompt = "You are a connection test. Reply with 'OK'.",
                history = listOf(ChatCompletionMessage("user", "Hello")),
                customUrl = customUrl,
                temperature = 0.1,
                maxTokens = 10
            )
            responseText.isNotBlank()
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed for $provider", e)
            false
        }
    }

    // Core Chat Completion
    suspend fun chatCompletion(
        provider: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<ChatCompletionMessage>,
        customUrl: String = "",
        temperature: Double = 0.5,
        maxTokens: Int = 1500
    ): String {
        return if (provider == "Google") {
            chatGemini(apiKey, model, systemPrompt, history)
        } else {
            chatOpenAiCompatible(provider, apiKey, model, systemPrompt, history, customUrl, temperature, maxTokens)
        }
    }

    // Execute chat with Gemini directly
    private suspend fun chatGemini(
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<ChatCompletionMessage>
    ): String {
        // Clean model name
        val cleanModel = if (model.startsWith("models/")) model else "models/$model"
        val url = "https://generativelanguage.googleapis.com/v1beta/$cleanModel:generateContent?key=$apiKey"

        // Build contents list
        val contents = history.map { msg ->
            val role = when (msg.role) {
                "assistant" -> "model"
                else -> "user"
            }
            GeminiContent(role = role, parts = listOf(GeminiPart(msg.content)))
        }

        val systemInstruction = if (systemPrompt.isNotBlank()) {
            GeminiSystemInstruction(parts = listOf(GeminiPart(systemPrompt)))
        } else null

        val requestBodyObj = GeminiRequest(contents = contents, systemInstruction = systemInstruction)
        val adapter = moshi.adapter(GeminiRequest::class.java)
        val requestJson = adapter.toJson(requestBodyObj)

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = executeRequest(request)
        val bodyString = response.body?.string() ?: throw IOException("Empty response body from Google API")
        if (!response.isSuccessful) {
            throw IOException("Google API call failed: ${response.code} $bodyString")
        }

        val responseAdapter = moshi.adapter(GeminiResponse::class.java)
        val geminiResponse = responseAdapter.fromJson(bodyString)
        val textResult = geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        return textResult ?: throw IOException("Google API returned no text: $bodyString")
    }

    // Execute chat with OpenAI compatible models (OpenRouter, DeepSeek, Mistral, Ollama, etc.)
    private suspend fun chatOpenAiCompatible(
        provider: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<ChatCompletionMessage>,
        customUrl: String,
        temperature: Double,
        maxTokens: Int
    ): String {
        val baseUrl = when (provider) {
            "OpenRouter" -> "https://openrouter.ai/api/v1/chat/completions"
            "Nvidia NIM" -> "https://integrate.api.nvidia.com/v1/chat/completions"
            "Mistral" -> "https://api.mistral.ai/v1/chat/completions"
            "DeepSeek" -> "https://api.deepseek.com/chat/completions"
            "Qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
            "Ollama" -> {
                val base = if (customUrl.isNotBlank()) customUrl else "http://localhost:11434"
                if (base.endsWith("/")) "${base}v1/chat/completions" else "$base/v1/chat/completions"
            }
            else -> throw IllegalArgumentException("Unknown provider: $provider")
        }

        // Build message payload (include system prompt in messages block for standard OpenAI)
        val messagesPayload = mutableListOf<ChatCompletionMessage>()
        if (systemPrompt.isNotBlank()) {
            messagesPayload.add(ChatCompletionMessage("system", systemPrompt))
        }
        messagesPayload.addAll(history)

        val requestObj = ChatCompletionRequest(
            model = model,
            messages = messagesPayload,
            temperature = temperature,
            max_tokens = maxTokens
        )
        val adapter = moshi.adapter(ChatCompletionRequest::class.java)
        val requestJson = adapter.toJson(requestObj)

        val requestBuilder = Request.Builder()
            .url(baseUrl)
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))

        // Headers
        when (provider) {
            "OpenRouter" -> {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                requestBuilder.addHeader("HTTP-Referer", "https://github.com/google/githelp")
                requestBuilder.addHeader("X-Title", "GitHelp")
            }
            "Ollama" -> {
                if (apiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }
            }
            else -> {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
        }

        val request = requestBuilder.build()
        val response = executeRequest(request)
        val bodyString = response.body?.string() ?: throw IOException("Empty response body from $provider API")
        if (!response.isSuccessful) {
            throw IOException("$provider API call failed: ${response.code} $bodyString")
        }

        val responseAdapter = moshi.adapter(ChatCompletionResponse::class.java)
        val openAiResponse = responseAdapter.fromJson(bodyString)
        val choice = openAiResponse?.choices?.firstOrNull()
        return choice?.message?.content ?: throw IOException("$provider API returned no content: $bodyString")
    }

    // Helper to run call synchronously under suspend coroutine
    private suspend fun executeRequest(request: Request): Response {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            client.newCall(request).execute()
        }
    }
}
