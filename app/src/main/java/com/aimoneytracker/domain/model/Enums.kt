package com.aimoneytracker.domain.model

import kotlinx.serialization.Serializable

/**
 * Core enums shared across the data, domain, and UI layers.
 *
 * All are [Serializable] because several are fields of `@Serializable` Room entities
 * (TransactionEntity, AccountEntity, PersonEntity) used by the backup/export layer — kotlinx
 * serialization requires the enum types themselves to be serializable.
 *
 * Note on money: all monetary amounts in this app are stored and computed as [Long] *minor units*
 * (paise for INR). This avoids floating-point rounding errors in financial math. Use
 * [com.aimoneytracker.util.Money] to format minor units for display and to parse user input.
 */

@Serializable
enum class TransactionType { DEBIT, CREDIT, TRANSFER }

@Serializable
enum class TransactionSource { SMS, NOTIFICATION, EMAIL, MANUAL, OCR, VOICE }

@Serializable
enum class AccountType { BANK, CREDIT_CARD, DEBIT_CARD, CASH, UPI, WALLET, INVESTMENT, LOAN }

@Serializable
enum class PaymentMethod { UPI, CARD, NETBANKING, CASH, IMPS, NEFT, RTGS, ATM, AUTO_DEBIT, CHEQUE, UNKNOWN }

@Serializable
enum class RelationshipType {
    FRIEND, FAMILY, PARTNER, COLLEAGUE, EMPLOYEE, EMPLOYER, CLIENT, VENDOR, MERCHANT, ROOMMATE, OTHER
}

@Serializable
enum class SplitType { EQUAL, PERCENTAGE, CUSTOM, WEIGHTED }

@Serializable
enum class SplitStatus { OUTSTANDING, PARTIALLY_PAID, SETTLED }

@Serializable
enum class BudgetPeriod { WEEKLY, MONTHLY, ANNUAL, CUSTOM }

@Serializable
enum class BudgetScope { OVERALL, CATEGORY, PERSON }

@Serializable
enum class GoalType { EMERGENCY_FUND, TRAVEL, GADGET, VEHICLE, HOUSE, EDUCATION, RETIREMENT, CUSTOM }

@Serializable
enum class SubscriptionCycle { WEEKLY, MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY, CUSTOM }

@Serializable
enum class SubscriptionKind { SUBSCRIPTION, EMI, RENT, UTILITY, LOAN, INSURANCE }

@Serializable
enum class GroupType { TRIP, ROOMMATE, EVENT, TEAM, OTHER }

@Serializable
enum class IncomeKind { SALARY, FREELANCE, BUSINESS, RENTAL, INTEREST, DIVIDEND, REFUND, GIFT, OTHER }

@Serializable
enum class DigestType { WEEKLY, MONTHLY }

/** How a transaction's category was assigned, used to decide whether to ask the user. */
@Serializable
enum class CategorizationSource { RULE, MERCHANT_MAP, AI, USER, DEFAULT }

/** A transaction-level processing flag describing detected special states. */
@Serializable
enum class ProcessingFlag {
    NONE, DUPLICATE, FAILED, REFUND, REVERSAL, TRANSFER, ATM_WITHDRAWAL, CASH_DEPOSIT
}
