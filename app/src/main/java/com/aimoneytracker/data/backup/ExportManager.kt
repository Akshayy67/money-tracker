package com.aimoneytracker.data.backup

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.util.DateUtil
import com.aimoneytracker.util.Money
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Report export (§20) to CSV (Excel-compatible), JSON and PDF. Reports are written to filesDir/exports
 * and can be shared via the app's FileProvider.
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun exportsDir(): File = File(context.filesDir, "exports").apply { mkdirs() }

    fun exportCsv(transactions: List<TransactionEntity>, name: String = "transactions"): File {
        val sb = StringBuilder()
        sb.append("Date,Type,Amount,Merchant,Category,Subcategory,PaymentMethod,Account,Notes\n")
        transactions.forEach { t ->
            sb.append(DateUtil.format(t.dateTime, "yyyy-MM-dd HH:mm")).append(',')
                .append(t.type).append(',')
                .append(Money.minorToMajor(t.amount)).append(',')
                .append(csv(t.merchantNormalized)).append(',')
                .append(t.category).append(',')
                .append(t.subcategory ?: "").append(',')
                .append(t.paymentMethod).append(',')
                .append(t.accountId ?: "").append(',')
                .append(csv(t.notes ?: "")).append('\n')
        }
        return File(exportsDir(), "${name}_${stamp()}.csv").apply { writeText(sb.toString()) }
    }

    fun exportJson(transactions: List<TransactionEntity>, name: String = "transactions"): File {
        // Entities are not @Serializable (Room/KSP conflict); build the JSON via EntityJson.
        val array = JsonArray(transactions.map { EntityJson.toJson(it) })
        val text = json.encodeToString(JsonArray.serializer(), array)
        return File(exportsDir(), "${name}_${stamp()}.json").apply { writeText(text) }
    }

    fun exportPdf(transactions: List<TransactionEntity>, title: String = "Transactions Report"): File {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 10f }
        val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true }
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
        var canvas = page.canvas
        var y = 40
        canvas.drawText(title, 40f, y.toFloat(), titlePaint); y += 30
        transactions.forEach { t ->
            if (y > 800) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
                canvas = page.canvas
                y = 40
            }
            val line = "${DateUtil.format(t.dateTime, "dd MMM")}  ${t.type}  ${Money.format(t.amount)}  ${t.merchantNormalized}  [${t.category}]"
            canvas.drawText(line, 40f, y.toFloat(), paint); y += 16
        }
        doc.finishPage(page)
        val file = File(exportsDir(), "report_${stamp()}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun csv(s: String): String =
        if (s.contains(',') || s.contains('"')) "\"${s.replace("\"", "\"\"")}\"" else s

    private fun stamp() = System.currentTimeMillis()
}
