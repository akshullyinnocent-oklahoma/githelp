package com.example.network

import com.squareup.moshi.JsonClass

// OpenAI Chat Completion Request / Response
@JsonClass(generateAdapter = true)
data class ChatCompletionMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatCompletionMessage>,
    val temperature: Double? = null,
    val max_tokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class ChatCompletionChoice(
    val index: Int,
    val message: ChatCompletionMessage,
    val finish_reason: String?
)

@JsonClass(generateAdapter = true)
data class ChatCompletionResponse(
    val id: String?,
    val model: String?,
    val choices: List<ChatCompletionChoice>
)

// OpenAI Model List Response (for Model Fetcher)
@JsonClass(generateAdapter = true)
data class ModelItem(
    val id: String
)

@JsonClass(generateAdapter = true)
data class ModelListResponse(
    val data: List<ModelItem>
)

// Google Gemini API Request / Response
@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiSystemInstruction(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiSystemInstruction? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent?,
    val finishReason: String?
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)
