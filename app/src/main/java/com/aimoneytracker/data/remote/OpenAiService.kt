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
 * OpenAI Chat Completions client (§1, §22). Same boundary contract as the Anthropic service: the LLM
 * ONLY categorizes unknown merchants, phrases insights/digests, and answers chat over data we supply
 * — it never computes money. Every call short-circuits to null when the key is blank or Local-only
 * mode is on, so callers always fall back to deterministic results.
 *
 * The key is injected at build time into [BuildConfig.OPENAI_API_KEY] from local.properties
 * (OPENAI_API_KEY or VITE_OPENAI_API_KEY) — it is never hardcoded.
 */
@Singleton
class OpenAiService @Inject constructor(
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
) : AiService {

    private val json = Json { ignoreUnknownKeys = true }
    private val endpoint = "https://api.openai.com/v1/chat/completions"
    private val mediaType = "application/json".toMediaType()

    override suspend fun isAvailable(): Boolean {
        if (BuildConfig.OPENAI_API_KEY.isBlank()) return false
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
        val text = call(system, listOf(ChatTurn("user", user)), maxTokens = 120, forceJson = true) ?: return null
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
            "figures. Keep all numbers exactly as given. Friendly tone."
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
        return call(system, listOf(ChatTurn("user", question)), maxTokens = 160, forceJson = true)
    }

    /** Low-level call to the Chat Completions API. Returns the assistant text, or null on any failure. */
    private suspend fun call(
        system: String,
        turns: List<ChatTurn>,
        maxTokens: Int,
        forceJson: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val messages = buildList {
                add(MessageDto("system", system))
                turns.forEach { add(MessageDto(it.role, it.content)) }
            }
            val body = ChatRequest(
                model = BuildConfig.OPENAI_MODEL,
                messages = messages,
                maxTokens = maxTokens,
                responseFormat = if (forceJson) ResponseFormat("json_object") else null,
            )
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(json.encodeToString(ChatRequest.serializer(), body).toRequestBody(mediaType))
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val raw = resp.body?.string() ?: return@withContext null
                val parsed = json.decodeFromString<ChatResponse>(raw)
                parsed.choices.firstOrNull()?.message?.content?.trim()
            }
        }.getOrNull()
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start in 0 until end) text.substring(start, end + 1) else text
    }

    // ---- DTOs ----
    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<MessageDto>,
        @SerialName("max_tokens") val maxTokens: Int,
        @SerialName("response_format") val responseFormat: ResponseFormat? = null,
        val temperature: Double = 0.2,
    )

    @Serializable
    private data class ResponseFormat(val type: String)

    @Serializable
    private data class MessageDto(val role: String, val content: String)

    @Serializable
    private data class ChatResponse(val choices: List<Choice> = emptyList())

    @Serializable
    private data class Choice(val message: MessageDto)

    @Serializable
    private data class CategorySuggestionDto(
        val category: String,
        val confidence: Double = 0.5,
        @SerialName("is_business") val isBusiness: Boolean = true,
    )
}
