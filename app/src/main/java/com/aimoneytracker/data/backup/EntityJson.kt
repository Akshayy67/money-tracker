package com.aimoneytracker.data.backup

import com.aimoneytracker.data.local.entity.AccountEntity
import com.aimoneytracker.data.local.entity.CategoryEntity
import com.aimoneytracker.data.local.entity.PersonEntity
import com.aimoneytracker.data.local.entity.TransactionEntity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import com.aimoneytracker.domain.model.CategorizationSource
import com.aimoneytracker.domain.model.PaymentMethod
import com.aimoneytracker.domain.model.ProcessingFlag
import com.aimoneytracker.domain.model.TransactionSource
import com.aimoneytracker.domain.model.TransactionType
import com.aimoneytracker.domain.model.AccountType
import com.aimoneytracker.domain.model.RelationshipType

/**
 * Manual JSON (de)serialization for the entities used in backup/export.
 *
 * We intentionally do NOT annotate Room `@Entity` classes with `@Serializable` — the kotlinx
 * serialization compiler plugin rewrites such classes and Room's KSP processor then fails to resolve
 * them (`[MissingType]`). Building the JSON tree by hand here keeps entities clean for Room while
 * still giving us a stable, versioned backup/export format.
 */
object EntityJson {

    // ---- helpers ----
    private fun str(s: String?): JsonElement = if (s == null) JsonNull else JsonPrimitive(s)
    private fun num(n: Long?): JsonElement = if (n == null) JsonNull else JsonPrimitive(n)
    private fun strList(list: List<String>): JsonArray = JsonArray(list.map { JsonPrimitive(it) })

    private fun JsonObject.s(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.l(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
    private fun JsonObject.lReq(key: String): Long = this[key]?.jsonPrimitive?.longOrNull ?: 0L
    private fun JsonObject.d(key: String): Double = this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0
    private fun JsonObject.b(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull ?: false
    private fun JsonObject.sl(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

    // ---- Transaction ----
    fun toJson(t: TransactionEntity): JsonObject = JsonObject(mapOf(
        "id" to num(t.id),
        "amount" to num(t.amount),
        "type" to str(t.type.name),
        "currency" to str(t.currency),
        "merchantNormalized" to str(t.merchantNormalized),
        "merchantRaw" to str(t.merchantRaw),
        "category" to str(t.category),
        "subcategory" to str(t.subcategory),
        "categorizationSource" to str(t.categorizationSource.name),
        "categoryConfidence" to JsonPrimitive(t.categoryConfidence),
        "dateTime" to num(t.dateTime),
        "accountId" to num(t.accountId),
        "paymentMethod" to str(t.paymentMethod.name),
        "location" to str(t.location),
        "notes" to str(t.notes),
        "tags" to strList(t.tags),
        "attachments" to strList(t.attachments),
        "receiptImage" to str(t.receiptImage),
        "relatedPersonId" to num(t.relatedPersonId),
        "relatedSplitId" to num(t.relatedSplitId),
        "relatedTransactionId" to num(t.relatedTransactionId),
        "availableBalance" to num(t.availableBalance),
        "confidence" to JsonPrimitive(t.confidence),
        "processingFlag" to str(t.processingFlag.name),
        "rawMessage" to str(t.rawMessage),
        "source" to str(t.source.name),
        "senderId" to str(t.senderId),
        "dedupKey" to str(t.dedupKey),
        "isReimbursable" to JsonPrimitive(t.isReimbursable),
        "isReviewed" to JsonPrimitive(t.isReviewed),
        "isIgnored" to JsonPrimitive(t.isIgnored),
        "isArchived" to JsonPrimitive(t.isArchived),
        "createdAt" to num(t.createdAt),
        "updatedAt" to num(t.updatedAt),
    ))

    fun transactionFrom(o: JsonObject): TransactionEntity = TransactionEntity(
        id = o.lReq("id"),
        amount = o.lReq("amount"),
        type = enumOr(o.s("type"), TransactionType.DEBIT),
        currency = o.s("currency") ?: "INR",
        merchantNormalized = o.s("merchantNormalized") ?: "Unknown",
        merchantRaw = o.s("merchantRaw") ?: "",
        category = o.s("category") ?: "uncategorized",
        subcategory = o.s("subcategory"),
        categorizationSource = enumOr(o.s("categorizationSource"), CategorizationSource.DEFAULT),
        categoryConfidence = o.d("categoryConfidence"),
        dateTime = o.lReq("dateTime"),
        accountId = o.l("accountId"),
        paymentMethod = enumOr(o.s("paymentMethod"), PaymentMethod.UNKNOWN),
        location = o.s("location"),
        notes = o.s("notes"),
        tags = o.sl("tags"),
        attachments = o.sl("attachments"),
        receiptImage = o.s("receiptImage"),
        relatedPersonId = o.l("relatedPersonId"),
        relatedSplitId = o.l("relatedSplitId"),
        relatedTransactionId = o.l("relatedTransactionId"),
        availableBalance = o.l("availableBalance"),
        confidence = o.d("confidence"),
        processingFlag = enumOr(o.s("processingFlag"), ProcessingFlag.NONE),
        rawMessage = o.s("rawMessage"),
        source = enumOr(o.s("source"), TransactionSource.MANUAL),
        senderId = o.s("senderId"),
        dedupKey = o.s("dedupKey"),
        isReimbursable = o.b("isReimbursable"),
        isReviewed = o.b("isReviewed"),
        isIgnored = o.b("isIgnored"),
        isArchived = o.b("isArchived"),
        createdAt = o.lReq("createdAt"),
        updatedAt = o.lReq("updatedAt"),
    )

    // ---- Account ----
    fun toJson(a: AccountEntity): JsonObject = JsonObject(mapOf(
        "id" to num(a.id),
        "name" to str(a.name),
        "type" to str(a.type.name),
        "bankName" to str(a.bankName),
        "maskedNumber" to str(a.maskedNumber),
        "openingBalance" to num(a.openingBalance),
        "currentBalanceCached" to num(a.currentBalanceCached),
        "reportedBalance" to num(a.reportedBalance),
        "reportedBalanceAt" to num(a.reportedBalanceAt),
        "isTracked" to JsonPrimitive(a.isTracked),
        "isOwnAccount" to JsonPrimitive(a.isOwnAccount),
        "colorHex" to str(a.colorHex),
        "creditLimit" to num(a.creditLimit),
        "statementDay" to num(a.statementDay?.toLong()),
        "dueDay" to num(a.dueDay?.toLong()),
        "currentStatementDue" to num(a.currentStatementDue),
        "minimumDue" to num(a.minimumDue),
        "createdAt" to num(a.createdAt),
    ))

    fun accountFrom(o: JsonObject): AccountEntity = AccountEntity(
        id = o.lReq("id"),
        name = o.s("name") ?: "Account",
        type = enumOr(o.s("type"), AccountType.BANK),
        bankName = o.s("bankName"),
        maskedNumber = o.s("maskedNumber"),
        openingBalance = o.lReq("openingBalance"),
        currentBalanceCached = o.lReq("currentBalanceCached"),
        reportedBalance = o.l("reportedBalance"),
        reportedBalanceAt = o.l("reportedBalanceAt"),
        isTracked = o.b("isTracked"),
        isOwnAccount = o.b("isOwnAccount"),
        colorHex = o.s("colorHex"),
        creditLimit = o.l("creditLimit"),
        statementDay = o.l("statementDay")?.toInt(),
        dueDay = o.l("dueDay")?.toInt(),
        currentStatementDue = o.l("currentStatementDue"),
        minimumDue = o.l("minimumDue"),
        createdAt = o.lReq("createdAt"),
    )

    // ---- Person ----
    fun toJson(p: PersonEntity): JsonObject = JsonObject(mapOf(
        "id" to num(p.id),
        "name" to str(p.name),
        "nickname" to str(p.nickname),
        "photoUri" to str(p.photoUri),
        "relationship" to str(p.relationship.name),
        "groupId" to num(p.groupId),
        "contactLookupKey" to str(p.contactLookupKey),
        "phone" to str(p.phone),
        "notes" to str(p.notes),
        "isMerchant" to JsonPrimitive(p.isMerchant),
        "createdAt" to num(p.createdAt),
    ))

    fun personFrom(o: JsonObject): PersonEntity = PersonEntity(
        id = o.lReq("id"),
        name = o.s("name") ?: "Person",
        nickname = o.s("nickname"),
        photoUri = o.s("photoUri"),
        relationship = enumOr(o.s("relationship"), RelationshipType.OTHER),
        groupId = o.l("groupId"),
        contactLookupKey = o.s("contactLookupKey"),
        phone = o.s("phone"),
        notes = o.s("notes"),
        isMerchant = o.b("isMerchant"),
        createdAt = o.lReq("createdAt"),
    )

    // ---- Category ----
    fun toJson(c: CategoryEntity): JsonObject = JsonObject(mapOf(
        "key" to str(c.key),
        "displayName" to str(c.displayName),
        "parentKey" to str(c.parentKey),
        "iconName" to str(c.iconName),
        "colorHex" to str(c.colorHex),
        "isIncome" to JsonPrimitive(c.isIncome),
        "isTransfer" to JsonPrimitive(c.isTransfer),
        "isSystem" to JsonPrimitive(c.isSystem),
        "sortOrder" to JsonPrimitive(c.sortOrder),
    ))

    fun categoryFrom(o: JsonObject): CategoryEntity = CategoryEntity(
        key = o.s("key") ?: "unknown",
        displayName = o.s("displayName") ?: "Unknown",
        parentKey = o.s("parentKey"),
        iconName = o.s("iconName"),
        colorHex = o.s("colorHex"),
        isIncome = o.b("isIncome"),
        isTransfer = o.b("isTransfer"),
        isSystem = o.b("isSystem"),
        sortOrder = (o.l("sortOrder") ?: 0L).toInt(),
    )

    fun arrayOf(elements: List<JsonElement>): JsonArray = JsonArray(elements)

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
