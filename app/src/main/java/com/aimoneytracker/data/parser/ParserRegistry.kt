package com.aimoneytracker.data.parser

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pluggable parser registry (§3). Holds all [BankParser]s and routes a message to the best one.
 *
 * Selection strategy:
 *  1. Ask each parser whether it [BankParser.claims] the message (sender/body markers).
 *  2. Run every claiming parser and keep the highest-confidence financial result.
 *  3. If none claim it, fall back to running the generic parsers anyway (a financial sender we don't
 *     recognize by name should still be parsed).
 *
 * Register a new bank by adding it to [allBankParsers] — no other change is required.
 */
@Singleton
class ParserRegistry @Inject constructor() {

    private val parsers: List<BankParser> = allBankParsers

    fun parse(sender: String?, body: String, receivedAt: Long): ParsedTransaction {
        if (body.isBlank()) return ParsedTransaction.notFinancial(body, sender)

        val claiming = parsers.filter { it.claims(sender, body) }
        val candidates = (if (claiming.isNotEmpty()) claiming else parsers)
            .mapNotNull { it.parse(sender, body, receivedAt) }
            .filter { it.isFinancial }

        return candidates.maxByOrNull { it.confidence }
            ?: ParsedTransaction.notFinancial(body, sender)
    }

    /** Exposed for the settings/diagnostics screen: which formats are supported. */
    fun supportedBanks(): List<String> = parsers.map { it.bankName }.distinct()
}
