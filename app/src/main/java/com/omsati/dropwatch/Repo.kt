package com.omsati.dropwatch

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Currency
import java.util.Locale

class Repo(val ctx: Context) {
    val dao = AppDb.get(ctx).dao()

    /** Normalises user input ("example.com/x" -> https://example.com/x). Returns null if invalid. */
    fun normalizeUrl(input: String): String? {
        val t = input.trim()
        if (t.isEmpty() || t.contains(' ')) return null
        val withScheme = if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) t else "https://$t"
        val u = withScheme.toHttpUrlOrNull() ?: return null
        if (!u.scheme.equals("http", ignoreCase = true) && !u.scheme.equals("https", ignoreCase = true)) return null
        if (!u.host.contains('.')) return null
        return u.toString()
    }

    suspend fun add(url: String, folderId: Long?, category: String): Product {
        val normalized = normalizeUrl(url) ?: url
        val host = normalized.toHttpUrlOrNull()?.host?.removePrefix("www.") ?: normalized

        // Check if product with same URL already exists in this folder
        val existingInFolder = dao.productsInFolder(folderId).firstOrNull { it.url.equals(normalized, ignoreCase = true) }
        if (existingInFolder != null) {
            return check(existingInFolder)
        }

        val nextPos = (dao.maxPosition(folderId) ?: -1) + 1
        val p = Product(
            url = normalized,
            domain = host,
            title = host,
            folderId = folderId,
            category = category.ifBlank { "Other" },
            position = nextPos,
        )
        val id = dao.insert(p)
        return check(p.copy(id = id))
    }

    /** Fetches the page again, stores any new price in history, returns the updated product. */
    suspend fun check(p: Product): Product {
        val now = System.currentTimeMillis()
        val r = runCatching { Scraper.fetch(ctx, p.url) }.getOrNull()
        if (r == null) {
            val u = p.copy(lastCheckedAt = now, status = "error")
            dao.update(u); return u
        }
        val np = if (r.price != null && r.price > 0) r.price else null
        val finalUrl = r.finalUrl ?: p.url
        val host = finalUrl.toHttpUrlOrNull()?.host?.removePrefix("www.") ?: p.domain

        // Never overwrite currency if user-locked
        val newCurrency = if (p.currencyLocked) p.currency else (r.currency ?: p.currency)

        val u = p.copy(
            url = finalUrl,
            domain = host,
            title = if (p.title.isBlank() || p.title == p.domain) (r.title ?: p.title) else p.title,
            imageUrl = r.imageUrl ?: p.imageUrl,
            price = np ?: p.price,
            previousPrice = if (np != null && p.price != null && np != p.price) p.price else p.previousPrice,
            currency = newCurrency,
            lastCheckedAt = now,
            status = if (np == null && p.price == null) "no_price" else "ok",
        )
        dao.update(u)

        if (np != null) {
            val lastPoint = dao.latestPoint(p.id)
            val priceChanged = (p.price != null && np != p.price)
            val noRecentPoint = (lastPoint == null || (now - lastPoint.at) >= 24 * 60 * 60 * 1000L)
            if (priceChanged || noRecentPoint) {
                dao.insertPoint(PricePoint(productId = p.id, price = np, at = now))
            }
        }

        // Keep 365 days of history; delete older entries
        val oneYearAgo = now - 365L * 24 * 60 * 60 * 1000L
        dao.pruneOldHistory(oneYearAgo)

        return u
    }

    suspend fun setManualPrice(p: Product, price: Double) {
        val now = System.currentTimeMillis()
        val validPrice = if (price > 0) price else null
        val updated = p.copy(
            price = validPrice ?: p.price,
            previousPrice = if (validPrice != null && p.price != null && validPrice != p.price) p.price else p.previousPrice,
            status = if (validPrice != null) "ok" else p.status,
            lastCheckedAt = now,
        )
        dao.update(updated)
        if (validPrice != null) {
            dao.insertPoint(PricePoint(productId = p.id, price = validPrice, at = now))
            dao.pruneOldHistory(now - 365L * 24 * 60 * 60 * 1000L)
        }
    }

    suspend fun updatePriceAndCurrency(p: Product, newPrice: Double?, newCurrency: String?, lockCurrency: Boolean = true) {
        val now = System.currentTimeMillis()
        val validPrice = if (newPrice != null && newPrice > 0) newPrice else null
        val cur = newCurrency?.trim()?.uppercase() ?: p.currency
        val updated = p.copy(
            price = validPrice ?: p.price,
            currency = cur,
            currencyLocked = lockCurrency || p.currencyLocked,
            previousPrice = if (validPrice != null && p.price != null && validPrice != p.price) p.price else p.previousPrice,
            status = if (validPrice != null || p.price != null) "ok" else p.status,
            lastCheckedAt = now,
        )
        dao.update(updated)
        if (validPrice != null) {
            dao.insertPoint(PricePoint(productId = p.id, price = validPrice, at = now))
            dao.pruneOldHistory(now - 365L * 24 * 60 * 60 * 1000L)
        }
    }

    suspend fun updateTitle(p: Product, newTitle: String) {
        dao.update(p.copy(title = newTitle.trim()))
    }

    suspend fun updateNote(productId: Long, note: String?) {
        dao.updateProductNote(productId, note?.trim()?.ifBlank { null })
    }

    suspend fun updatePositions(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            dao.updateProductPosition(id, index)
        }
    }

    suspend fun compactPositions(folderId: Long?) {
        val items = dao.productsInFolder(folderId)
        items.forEachIndexed { index, p ->
            if (p.position != index) {
                dao.updateProductPosition(p.id, index)
            }
        }
    }

    suspend fun updateFolder(f: Folder) {
        dao.updateFolder(f)
    }

    suspend fun updateFolderSortOption(folderId: Long, sortOption: String) {
        dao.updateFolderSortOption(folderId, sortOption)
    }

    suspend fun delete(id: Long) {
        val p = dao.productById(id)
        dao.deleteHistory(id)
        dao.deleteProduct(id)
        compactPositions(p?.folderId)
    }

    suspend fun deleteMultiple(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            val affectedFolders = dao.getFolderIdsForProducts(ids)
            dao.deleteHistoryForProducts(ids)
            dao.deleteProducts(ids)
            for (fId in affectedFolders) {
                compactPositions(fId)
            }
        }
    }

    suspend fun deleteFolder(id: Long) {
        dao.clearFolder(id)
        dao.deleteFolder(id)
        compactPositions(null)
    }
}

fun money(v: Double?, currency: String?): String {
    if (v == null || v < 0) return "Price unavailable"
    val sym = when (currency?.uppercase()) {
        "INR" -> "₹"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> runCatching { Currency.getInstance(currency ?: "").getSymbol(Locale.getDefault()) }.getOrNull()
            ?: currency?.let { "$it " } ?: "₹"
    }
    val num = if (v % 1.0 == 0.0) String.format(Locale.getDefault(), "%,.0f", v)
    else String.format(Locale.getDefault(), "%,.2f", v)
    return "$sym$num"
}
