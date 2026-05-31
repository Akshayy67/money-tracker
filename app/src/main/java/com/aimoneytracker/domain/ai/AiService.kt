package com.aimoneytracker.domain.ai

/**
 * The AI integration boundary (§1, §22). Claude is used ONLY to:
 *  - categorize genuinely unknown merchants,
 *  - phrase insights and digest copy from deterministic numbers,
 *  - answer chat questions over data we fetch for it.
 *
 * It NEVER computes money. Every method degrades gracefully (returns null) when Local-only mode is on
 * or no API key is configured — callers must always have a deterministic fallback.
 */
interface AiService {

    /** True when network AI is permitted (key present AND not in Local-only mode). */
    suspend fun isAvailable(): Boolean

    /** Suggest a category key for an unknown merchant. Returns one of the catalog keys or null. */
    suspend fun categorizeMerchant(merchant: String, rawMessage: String?, candidateKeys: List<String>): AiCategorySuggestion?

    /** Turn a list of deterministic facts into a short, friendly insight string. */
    suspend fun phraseInsight(facts: List<String>): String?

    /** Generate a glanceable natural-language digest summary from pre-computed numbers. */
    suspend fun summarizeDigest(facts: List<String>): String?

    /**
     * Chat: given the user's question and a structured, already-computed data context (JSON/string),
     * produce a natural-language answer. The numbers are supplied; the model must not invent figures.
     */
    suspend fun answerChat(question: String, dataContext: String, history: List<ChatTurn>): String?

    /** Parse a free-text question into a structured query intent for deterministic DB lookup (§22). */
    suspend fun parseQueryIntent(question: String): String?
}

data class AiCategorySuggestion(val categoryKey: String, val confidence: Double, val isBusiness: Boolean)

data class ChatTurn(val role: String, val content: String) // role: "user" | "assistant"
