package com.aimoneytracker.data.local.result

/** Lightweight projections returned by aggregate DAO queries. */

data class CategorySum(val category: String, val total: Long, val count: Int)

data class MerchantSum(val merchantNormalized: String, val total: Long, val count: Int)

data class PersonSum(val relatedPersonId: Long?, val total: Long, val count: Int)

data class AccountSum(val accountId: Long?, val total: Long, val count: Int)

data class DaySum(val day: String, val total: Long, val count: Int)

data class TypeSum(val type: String, val total: Long, val count: Int)

data class DayOfWeekSum(val dow: Int, val total: Long, val count: Int)

data class HourSum(val hour: Int, val total: Long, val count: Int)

/** Net sent/received between the user and a person. */
data class PersonBalance(
    val personId: Long,
    val sent: Long,
    val received: Long,
    val count: Int,
    val lastTxn: Long,
)
