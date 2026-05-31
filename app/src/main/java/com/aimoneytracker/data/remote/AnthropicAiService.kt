package com.aimoneytracker.data.remote

import com.aimoneytracker.BuildConfig
import com.aimoneytracker.data.preferences.SettingsRepository
import com.aimoneytracker.domain.ai.AiCategorySuggestion
import com.aimoneytracker.domain.ai.AiService
import com.aimoneytracker.domain.ai.ChatTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Claude API client (§1, §22). Talks to the Anthropic Messages API over OkHttp. The key is injected
 * at build time into [BuildConfig.ANTHROPIC_API_KEY]; if blank, or Local-only mode is on, every call
 * short-circuits to null so the caller uses its deterministic fallback.
 */
@Singleton
class AnthropicAiService @Inject constructor(
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
) : AiService {

    private val json = Json { ignoreUnknownKeys = true }
    private val endpoint = "https://api.anthropic.com/v1/messages"
    private val mediaType = "application/json".toMediaType()

    override suspend fun isAvailable(): Boolean {
        if (BuildConfig.ANTHROPIC_API_KEY.isBlank()) return false
        return !settings.settings.first().localOnly
    }

    override suspend fun categorizeMerchant(
        merchant: String,
        rawMessage: String?,
        candidateKeys: List<String>,
    ): AiCategorySuggestion? {
        if (!isAvailable()) return null
        val system = "You categorize Indian financial transactions. Reply with ONLY a compact JSON " +
            "object: {\"category\":\"<one of the provided keys>\",\"confidence\":0-1," +
            "\"is_business\":true|false}. No prose."
        val user = buildString {
            append("Merchant/payee: ").append(merchant).append('\n')
            if (!rawMessage.isNullOrBlank()) append("Message: ").append(rawMessage.take(300)).append('\n')
            append("Allowed category keys: ").append(candidateKeys.joinToString(", "))
        }
        val text = call(system, listOf(ChatTurn("user", user)), maxTokens = 120) ?: return null
        return runCatching {
            val obj = json.decodeFromString<CategorySuggestionDto>(extractJson(text))
            if (obj.category in candidateKeys)
                AiCategorySuggestion(obj.category, obj.confidence.coerceIn(0.0, 1.0), obj.isBusiness)
            else null
        }.getOrNull()
    }

    override suspend fun phraseInsight(facts: List<String>): String? {
        if (!isAvailable()) return null
        val system = "You are a concise personal-finance assistant. Given exact facts, write ONE " +
            "short, friendly insight sentence. Never change or invent numbers."
        return call(system, listOf(ChatTurn("user", facts.joinToString("\n"))), maxTokens = 120)
    }

    override suspend fun summarizeDigest(facts: List<String>): String? {
        if (!isAvailable()) return null
        val system = "Write a short, glanceable spending recap (2-3 sentences) from these exact " +
            "figures. Keep all numbers exactly as given. Friendly, no emojis overload."
        return call(system, listOf(ChatTurn("user", facts.joinToString("\n"))), maxTokens = 220)
    }

    override suspend fun answerChat(question: String, dataContext: String, history: List<ChatTurn>): String? {
        if (!isAvailable()) return null
        val system = "You are the user's money assistant. Answer ONLY from the provided DATA. The " +
            "numbers are already computed and correct — never recompute or invent figures. If the " +
            "data doesn't contain the answer, say so briefly. Be concise."
        val turns = history.takeLast(6) + ChatTurn("user", "DATA:\n$dataContext\n\nQUESTION: $question")
        return call(system, turns, maxTokens = 400)
    }

    override suspend fun parseQueryIntent(question: String): String? {
        if (!isAvailable()) return null
        val system = "Extract a query intent from the user's finance question. Reply ONLY JSON: " +
            "{\"metric\":\"spend|income|balance|person_net|owes|subscriptions|count\"," +
            "\"category\":null|key,\"person\":null|name,\"period\":\"this_month|last_month|this_week|this_year|all\"," +
            "\"min_amount\":null|number,\"weekend_only\":true|false}."
        return call(system, listOf(ChatTurn("user", question)), maxTokens = 160)
    }

    /** Low-level call to the Messages API. Returns the assistant text, or null on any failure. */
    private suspend fun call(system: String, turns: List<ChatTurn>, maxTokens: Int): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = MessagesRequest(
                    model = BuildConfig.ANTHROPIC_MODEL,
                    maxTokens = maxTokens,
                    system = system,
                    messages = turns.map { MessageDto(it.role, listOf(ContentDto(text = it.content))) },
                )
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("x-api-key", BuildConfig.ANTHROPIC_API_KEY)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(json.encodeToString(MessagesRequest.serializer(), body).toRequestBody(mediaType))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val raw = resp.body?.string() ?: return@withContext null
                    val parsed = json.decodeFromString<MessagesResponse>(raw)
                    parsed.content.firstOrNull { it.type == "text" }?.text?.trim()
                }
            }.getOrNull()
        }

    /** Pull the first {...} block out of a possibly-fenced model reply. */
    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start in 0 until end) text.substring(start, end + 1) else text
    }

    // ---- DTOs ----
    @Serializable
    private data class MessagesRequest(
        val model: String,
        @SerialName("max_tokens") val maxTokens: Int,
        val system: String,
        val messages: List<MessageDto>,
    )

    @Serializable
    private data class MessageDto(val role: String, val content: List<ContentDto>)

    @Serializable
    private data class ContentDto(val type: String = "text", val text: String? = null)

    @Serializable
    private data class MessagesResponse(val content: List<ContentDto> = emptyList())

    @Serializable
    private data class CategorySuggestionDto(
        val category: String,
        val confidence: Double = 0.5,
        @SerialName("is_business") val isBusiness: Boolean = true,
    )
}
