package com.omsati.dropwatch

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

val SuggestedCategories = listOf("Electronics", "Fashion", "Home", "Beauty", "Books", "Gifts", "Other")

val FolderColorPalette = listOf(
    0xFF4F46E5L, // Indigo
    0xFF7C3AEDL, // Purple
    0xFFDB2777L, // Pink
    0xFFE11D48L, // Rose
    0xFFEA580CL, // Orange
    0xFFD97706L, // Amber
    0xFF059669L, // Emerald
    0xFF0D9488L, // Teal
    0xFF0284C7L, // Light Blue
    0xFF2563EBL, // Royal Blue
    0xFF475569L, // Slate
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class FolderSortOption(val id: String, val label: String) {
    MY_ORDER("MY_ORDER", "My order (drag to rearrange)"),
    DATE_DESC("DATE_DESC", "Date added: newest first"),
    DATE_ASC("DATE_ASC", "Date added: oldest first"),
    NAME_ASC("NAME_ASC", "Name: A to Z"),
    NAME_DESC("NAME_DESC", "Name: Z to A"),
    PRICE_ASC("PRICE_ASC", "Price: low to high"),
    PRICE_DESC("PRICE_DESC", "Price: high to low");

    companion object {
        fun fromId(id: String?): FolderSortOption = entries.firstOrNull { it.id == id } ?: MY_ORDER
    }
}

@Immutable
data class FolderSummary(
    val id: Long?, // null for virtual Unsorted folder
    val name: String,
    val emoji: String,
    val color: Long,
    val sortOption: String = "MY_ORDER",
    val count: Int,
    val totalValue: String,
    val latestImageUrl: String?,
    val isUnsorted: Boolean = false,
)

class MainVm(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)
    val dao = repo.dao
    private val prefs = app.getSharedPreferences("dropwatch_prefs", Context.MODE_PRIVATE)

    // Default Currency Setting
    private val _defaultCurrency = MutableStateFlow(prefs.getString("default_currency", "INR") ?: "INR")
    val defaultCurrency: StateFlow<String> = _defaultCurrency.asStateFlow()

    fun setDefaultCurrency(cur: String) {
        val uppercaseCur = cur.uppercase()
        prefs.edit().putString("default_currency", uppercaseCur).apply()
        _defaultCurrency.value = uppercaseCur
        messages.tryEmit("Default currency set to $uppercaseCur")
    }

    // Theme Mode
    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private fun loadThemeMode(): ThemeMode {
        val saved = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return runCatching { ThemeMode.valueOf(saved) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
    }

    // WorkManager Check Interval (1, 3, 6, 12, 24)
    private val _checkIntervalHours = MutableStateFlow(prefs.getInt("check_interval_hours", 6))
    val checkIntervalHours: StateFlow<Int> = _checkIntervalHours.asStateFlow()

    fun setCheckInterval(hours: Int) {
        prefs.edit().putInt("check_interval_hours", hours).apply()
        _checkIntervalHours.value = hours
        MahoApp.schedulePeriodicCheck(getApplication(), update = true)
        messages.tryEmit("Check interval set to ${hours}h")
    }

    fun checkAllPricesNow() {
        MahoApp.runCheckNow(getApplication())
        refreshAll()
    }

    fun sendTestNotification() {
        Notifier.sendTestNotification(getApplication())
        messages.tryEmit("Test notification sent")
    }

    // Folders & Products
    val folders = dao.folders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val all = dao.products().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _unsortedSortOption = MutableStateFlow(
        prefs.getString("sort_option_unsorted", FolderSortOption.MY_ORDER.id) ?: FolderSortOption.MY_ORDER.id
    )

    val folderSummaries = combine(folders, all, _unsortedSortOption, _defaultCurrency) { folderList, productList, unsortedSort, defCur ->
        val list = mutableListOf<FolderSummary>()
        for (f in folderList) {
            val prods = productList.filter { it.folderId == f.id }
            val validPrices = prods.filter { it.price != null && it.price > 0 }
            val totals = validPrices.groupBy { it.currency?.uppercase() ?: defCur }
                .mapValues { entry -> entry.value.sumOf { it.price ?: 0.0 } }
            val totalStr = if (totals.isEmpty()) money(0.0, defCur)
            else totals.entries.joinToString(" · ") { (cur, sum) -> money(sum, cur) }

            list.add(
                FolderSummary(
                    id = f.id,
                    name = f.name,
                    emoji = f.emoji,
                    color = f.color,
                    sortOption = f.sortOption,
                    count = prods.size,
                    totalValue = totalStr,
                    latestImageUrl = prods.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl,
                    isUnsorted = false,
                )
            )
        }
        val unsortedProds = productList.filter { it.folderId == null }
        val validUnsorted = unsortedProds.filter { it.price != null && it.price > 0 }
        val unsortedTotals = validUnsorted.groupBy { it.currency?.uppercase() ?: defCur }
            .mapValues { entry -> entry.value.sumOf { it.price ?: 0.0 } }
        val unsortedTotalStr = if (unsortedTotals.isEmpty()) money(0.0, defCur)
        else unsortedTotals.entries.joinToString(" · ") { (cur, sum) -> money(sum, cur) }

        list.add(
            FolderSummary(
                id = null,
                name = "Unsorted",
                emoji = "📦",
                color = 0xFF64748BL,
                sortOption = unsortedSort,
                count = unsortedProds.size,
                totalValue = unsortedTotalStr,
                latestImageUrl = unsortedProds.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl,
                isUnsorted = true,
            )
        )
        list.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun productsForFolder(folderId: Long?): Flow<List<Product>> {
        return all.map { list -> list.filter { it.folderId == folderId } }
    }

    fun setFolderSortOption(folderId: Long?, option: FolderSortOption) = viewModelScope.launch {
        if (folderId != null) {
            repo.updateFolderSortOption(folderId, option.id)
        } else {
            prefs.edit().putString("sort_option_unsorted", option.id).apply()
            _unsortedSortOption.value = option.id
        }
    }

    fun updatePositions(orderedIds: List<Long>) = viewModelScope.launch {
        repo.updatePositions(orderedIds)
    }

    fun updateNote(productId: Long, note: String?) = viewModelScope.launch {
        repo.updateNote(productId, note)
        messages.tryEmit(if (note.isNullOrBlank()) "Note removed" else "Note saved")
    }

    val refreshing = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val categories = all.map { l -> l.map { it.category }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun product(id: Long) = dao.product(id)
    fun history(id: Long) = dao.history(id)

    fun add(rawUrl: String, folderId: Long?, category: String) = viewModelScope.launch {
        val url = repo.normalizeUrl(rawUrl)
        if (url == null) { messages.tryEmit("Enter a valid product link"); return@launch }
        messages.tryEmit("Saving…")
        val p = repo.add(url, folderId, category)
        messages.tryEmit(
            when (p.status) {
                "ok" -> "Saved: ${money(p.price, p.currency)}"
                "no_price" -> "Saved, but no price found. You can set it manually."
                else -> "Saved, but the page couldn't be read. Will retry later."
            }
        )
    }

    fun refresh(p: Product) = viewModelScope.launch { repo.check(p) }

    fun refreshAll() = viewModelScope.launch {
        refreshing.value = true
        for (p in dao.allProducts()) repo.check(p)
        refreshing.value = false
        messages.tryEmit("Prices updated")
    }

    fun update(p: Product) = viewModelScope.launch { dao.update(p) }
    fun updateTitle(p: Product, title: String) = viewModelScope.launch { repo.updateTitle(p, title) }
    fun setManualPrice(p: Product, price: Double) = viewModelScope.launch { repo.setManualPrice(p, price) }

    fun updatePriceAndCurrency(p: Product, newPrice: Double?, newCurrency: String?) = viewModelScope.launch {
        repo.updatePriceAndCurrency(p, newPrice, newCurrency, lockCurrency = true)
        messages.tryEmit("Price updated")
    }

    fun switchProductCurrency(p: Product, newCurrency: String) = viewModelScope.launch {
        dao.update(p.copy(currency = newCurrency.uppercase(), currencyLocked = true))
        messages.tryEmit("Currency changed to ${newCurrency.uppercase()}")
    }

    fun convertProductCurrency(p: Product, targetCurrency: String, rate: Double) = viewModelScope.launch {
        if (p.price != null && rate > 0) {
            val newPrice = kotlin.math.round(p.price * rate * 100) / 100.0
            repo.updatePriceAndCurrency(p, newPrice, targetCurrency.uppercase(), lockCurrency = true)
            messages.tryEmit("Converted price to ${money(newPrice, targetCurrency)}")
        }
    }

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id); messages.tryEmit("Removed") }

    fun deleteMultiple(ids: Collection<Long>) = viewModelScope.launch {
        repo.deleteMultiple(ids.toList())
        messages.tryEmit("${ids.size} products removed")
    }

    fun addFolder(name: String, emoji: String, color: Long) = viewModelScope.launch {
        if (name.isNotBlank()) {
            dao.insertFolder(Folder(name = name.trim(), emoji = emoji.ifBlank { "📁" }, color = color))
        }
    }

    fun renameFolder(id: Long, name: String, emoji: String, color: Long) = viewModelScope.launch {
        if (name.isNotBlank()) {
            val existing = dao.folderById(id)
            val sortOpt = existing?.sortOption ?: "MY_ORDER"
            dao.updateFolder(Folder(id = id, name = name.trim(), emoji = emoji.ifBlank { "📁" }, color = color, sortOption = sortOpt))
        }
    }

    fun deleteFolder(id: Long) = viewModelScope.launch {
        repo.deleteFolder(id)
        messages.tryEmit("Folder deleted; items moved to Unsorted")
    }
}
