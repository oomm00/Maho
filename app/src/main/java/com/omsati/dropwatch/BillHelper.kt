package com.omsati.dropwatch

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BillHelper {
    private const val LINE_WIDTH = 40

    fun generateBill(folderName: String, products: List<Product>): String {
        val sb = StringBuilder()
        val eqLine = "=".repeat(LINE_WIDTH)
        val dashLine = "-".repeat(LINE_WIDTH)

        sb.appendLine(eqLine)
        sb.appendLine(centerText("DROPWATCH BILL", LINE_WIDTH))
        sb.appendLine(eqLine)

        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        sb.appendLine("Folder : $folderName")
        sb.appendLine("Date   : ${dateFormat.format(Date())}")
        sb.appendLine("Items  : ${products.size}")
        sb.appendLine(dashLine)
        sb.appendLine(String.format(Locale.getDefault(), "%-3s %-25s %10s", "No", "Item", "Price"))
        sb.appendLine(dashLine)

        products.forEachIndexed { index, p ->
            val num = "${index + 1}"
            val priceStr = money(p.price, p.currency)
            val title = p.title.replace('\n', ' ').trim()

            // Wrap title to max 25 chars per line
            val titleLines = wrapText(title, 25)
            val firstLine = titleLines.firstOrNull() ?: ""

            sb.appendLine(String.format(Locale.getDefault(), "%-3s %-25s %10s", num, firstLine, priceStr))
            for (i in 1 until titleLines.size) {
                sb.appendLine(String.format(Locale.getDefault(), "    %-36s", titleLines[i]))
            }
            sb.appendLine("    ${p.url}")
            if (!p.note.isNullOrBlank()) {
                val noteLines = wrapText("Note: ${p.note.replace('\n', ' ').trim()}", 36)
                for (nl in noteLines) {
                    sb.appendLine(String.format(Locale.getDefault(), "    %-36s", nl))
                }
            }
        }

        sb.appendLine(dashLine)

        val itemsWithPrice = products.filter { it.price != null && it.price > 0 }
        sb.appendLine("Items with price : ${itemsWithPrice.size}")

        val totalsByCurrency = itemsWithPrice
            .groupBy { it.currency?.uppercase() ?: "INR" }
            .mapValues { entry -> entry.value.sumOf { it.price ?: 0.0 } }

        if (totalsByCurrency.isEmpty()) {
            sb.appendLine(String.format(Locale.getDefault(), "%-20s %19s", "SUBTOTAL", "₹0"))
        } else if (totalsByCurrency.size == 1) {
            val (cur, sum) = totalsByCurrency.entries.first()
            sb.appendLine(String.format(Locale.getDefault(), "%-20s %19s", "SUBTOTAL", money(sum, cur)))
        } else {
            for ((cur, sum) in totalsByCurrency) {
                sb.appendLine(String.format(Locale.getDefault(), "%-20s %19s", "SUBTOTAL ($cur)", money(sum, cur)))
            }
        }

        sb.appendLine(eqLine)
        sb.appendLine("Prices as last checked by DropWatch.")
        sb.appendLine("Thank you!")

        return sb.toString()
    }

    private fun centerText(text: String, width: Int): String {
        if (text.length >= width) return text
        val pad = (width - text.length) / 2
        return " ".repeat(pad) + text
    }

    private fun wrapText(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (w in words) {
            if (current.isEmpty()) {
                current.append(w.take(maxLen))
            } else if (current.length + 1 + w.length <= maxLen) {
                current.append(" ").append(w)
            } else {
                lines.add(current.toString())
                current = StringBuilder(w.take(maxLen))
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.ifEmpty { listOf(text.take(maxLen)) }
    }

    fun saveBillAndGetShareUri(ctx: Context, folderName: String, billContent: String): Pair<Uri?, Uri?> {
        val safeFolder = folderName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val fileName = "DropWatch_Bill_${safeFolder}_${timestamp}.txt"

        var downloadUri: Uri? = null
        var shareUri: Uri? = null

        // 1. Save to Downloads via MediaStore on Android 10+
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = ctx.contentResolver
                downloadUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (downloadUri != null) {
                    resolver.openOutputStream(downloadUri)?.use { it.write(billContent.toByteArray(Charsets.UTF_8)) }
                }
            } else {
                @Suppress("DEPRECATION")
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloads.mkdirs()
                val targetFile = File(downloads, fileName)
                targetFile.writeText(billContent, Charsets.UTF_8)
                downloadUri = Uri.fromFile(targetFile)
            }
        } catch (e: Exception) {
            Log.e("BillHelper", "Error saving to Downloads: ${e.message}", e)
        }

        // 2. Save a copy in cache directory for FileProvider sharing
        try {
            val cacheFolder = File(ctx.cacheDir, "bills").apply { mkdirs() }
            val cacheFile = File(cacheFolder, fileName)
            cacheFile.writeText(billContent, Charsets.UTF_8)
            shareUri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", cacheFile)
        } catch (e: Exception) {
            Log.e("BillHelper", "Error preparing share URI: ${e.message}", e)
        }

        return Pair(downloadUri, shareUri)
    }

    fun shareBill(ctx: Context, shareUri: Uri?, billContent: String, folderName: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "DropWatch Bill - $folderName")
            putExtra(Intent.EXTRA_TEXT, billContent)
            if (shareUri != null) {
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        ctx.startActivity(Intent.createChooser(intent, "Share DropWatch Bill"))
    }
}
