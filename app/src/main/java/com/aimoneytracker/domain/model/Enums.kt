package com.aimoneytracker.domain.model

/**
 * Core enums shared across the data, domain, and UI layers.
 *
 * These are intentionally NOT annotated `@Serializable`. They are used as Room column types in the
 * entities, and the kotlinx-serialization compiler plugin rewrites `@Serializable` types in a way
 * that makes Room's KSP processor fail to resolve them — producing the generic
 * "[MissingType]: AppDatabase references a type that is not present". Backup/export serializes
 * these enums manually as their [name] strings via `EntityJson`, so no annotation is needed.
 *
 * Note on money: all monetary amounts in this app are stored and computed as [Long] *minor units*
 * (paise for INR). This avoids floating-point rounding errors in financial math. Use
 * [com.aimoneytracker.util.Money] to format minor units for display and to parse user input.
 */

enum class TransactionType { DEBIT, CREDIT, TRANSFER }

enum class TransactionSource { SMS, NOTIFICATION, EMAIL, MANUAL, OCR, VOICE }

enum class AccountType { BANK, CREDIT_CARD, DEBIT_CARD, CASH, UPI, WALLET, INVESTMENT, LOAN }

enum class PaymentMethod { UPI, CARD, NETBANKING, CASH, IMPS, NEFT, RTGS, ATM, AUTO_DEBIT, CHEQUE, UNKNOWN }

enum class RelationshipType {
    FRIEND, FAMILY, PARTNER, COLLEAGUE, EMPLOYEE, EMPLOYER, CLIENT, VENDOR, MERCHANT, ROOMMATE, OTHER
}

enum class SplitType { EQUAL, PERCENTAGE, CUSTOM, WEIGHTED }

enum class SplitStatus { OUTSTANDING, PARTIALLY_PAID, SETTLED }

enum class BudgetPeriod { WEEKLY, MONTHLY, ANNUAL, CUSTOM }

enum class BudgetScope { OVERALL, CATEGORY, PERSON }

enum class GoalType { EMERGENCY_FUND, TRAVEL, GADGET, VEHICLE, HOUSE, EDUCATION, RETIREMENT, CUSTOM }

enum class SubscriptionCycle { WEEKLY, MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY, CUSTOM }

enum class SubscriptionKind { SUBSCRIPTION, EMI, RENT, UTILITY, LOAN, INSURANCE }

enum class GroupType { TRIP, ROOMMATE, EVENT, TEAM, OTHER }

enum class IncomeKind { SALARY, FREELANCE, BUSINESS, RENTAL, INTEREST, DIVIDEND, REFUND, GIFT, OTHER }

enum class DigestType { WEEKLY, MONTHLY }

/** How a transaction's category was assigned, used to decide whether to ask the user. */
enum class CategorizationSource { RULE, MERCHANT_MAP, AI, USER, DEFAULT }

/** A transaction-level processing flag describing detected special states. */
enum class ProcessingFlag {
    NONE, DUPLICATE, FAILED, REFUND, REVERSAL, TRANSFER, ATM_WITHDRAWAL, CASH_DEPOSIT
}
