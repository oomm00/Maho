@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.omsati.dropwatch

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DropWatchRoot(
    sharedUrl: String?, onSharedConsumed: () -> Unit,
    openId: Long?, onOpenConsumed: () -> Unit,
    vm: MainVm = viewModel(),
) {
    val themeMode by vm.themeMode.collectAsState()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> darkColorScheme(
            primary = Color(0xFF818CF8),
            onPrimary = Color(0xFF1E1B4B),
            primaryContainer = Color(0xFF312E81),
            onPrimaryContainer = Color(0xFFE0E7FF),
            secondary = Color(0xFFA5B4FC),
            surface = Color(0xFF1E1E2E),
            onSurface = Color(0xFFE2E8F0),
            surfaceVariant = Color(0xFF272738),
            onSurfaceVariant = Color(0xFF94A3B8),
            background = Color(0xFF13131C),
            onBackground = Color(0xFFF1F5F9),
        )
        else -> lightColorScheme(
            primary = Color(0xFF4F46E5),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEEF2FF),
            onPrimaryContainer = Color(0xFF312E81),
            secondary = Color(0xFF6366F1),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF64748B),
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0F172A),
        )
    }

    val greenColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)

    MaterialTheme(colorScheme = colorScheme) {
        val snack = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        // Navigation state
        var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
        var selectedProductId by rememberSaveable { mutableStateOf<Long?>(null) }
        var activeFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
        var isInFolder by rememberSaveable { mutableStateOf(false) }

        // Dialog states
        var showNewFolderDialog by rememberSaveable { mutableStateOf(false) }
        var folderToEdit by remember { mutableStateOf<FolderSummary?>(null) }
        var folderToDelete by remember { mutableStateOf<FolderSummary?>(null) }
        var showAddProductDialog by rememberSaveable { mutableStateOf(false) }
        var addDialogInitialUrl by rememberSaveable { mutableStateOf("") }
        var addDialogTargetFolderId by rememberSaveable { mutableStateOf<Long?>(null) }

        // Manual price dialog state
        var productForManualPrice by remember { mutableStateOf<Product?>(null) }

        // Shared link bottom sheet picker
        var pendingShareUrl by rememberSaveable { mutableStateOf<String?>(null) }

        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }

        // Handle shared URL from other apps
        LaunchedEffect(sharedUrl) {
            if (sharedUrl != null) {
                if (isInFolder) {
                    addDialogInitialUrl = sharedUrl
                    addDialogTargetFolderId = activeFolderId
                    showAddProductDialog = true
                } else {
                    pendingShareUrl = sharedUrl
                }
                onSharedConsumed()
            }
        }

        // Handle notification open
        LaunchedEffect(openId) {
            if (openId != null) {
                selectedProductId = openId
                onOpenConsumed()
            }
        }

        // Back navigation handling
        BackHandler(enabled = (selectedProductId != null || isInFolder || isSettingsOpen)) {
            when {
                selectedProductId != null -> selectedProductId = null
                isInFolder -> {
                    isInFolder = false
                    activeFolderId = null
                }
                isSettingsOpen -> isSettingsOpen = false
            }
        }

        when {
            isSettingsOpen -> {
                SettingsScreen(
                    vm = vm,
                    snack = snack,
                    onBack = { isSettingsOpen = false },
                )
            }
            selectedProductId != null -> {
                DetailScreen(
                    vm = vm,
                    id = selectedProductId!!,
                    snack = snack,
                    greenColor = greenColor,
                    onBack = { selectedProductId = null },
                )
            }
            isInFolder -> {
                val folders by vm.folders.collectAsState()
                val currentFolder = folders.firstOrNull { it.id == activeFolderId }
                val folderName = if (activeFolderId == null) "Unsorted" else (currentFolder?.name ?: "Folder")
                val folderEmoji = if (activeFolderId == null) "📦" else (currentFolder?.emoji ?: "📁")

                FolderCatalogScreen(
                    vm = vm,
                    folderId = activeFolderId,
                    folderName = folderName,
                    folderEmoji = folderEmoji,
                    snack = snack,
                    greenColor = greenColor,
                    onBack = {
                        isInFolder = false
                        activeFolderId = null
                    },
                    onOpenHistory = { selectedProductId = it },
                    onSetPriceManually = { productForManualPrice = it },
                    onAddLink = {
                        addDialogInitialUrl = ""
                        addDialogTargetFolderId = activeFolderId
                        showAddProductDialog = true
                    },
                )
            }
            else -> {
                HomeScreen(
                    vm = vm,
                    snack = snack,
                    onOpenFolder = { folderId ->
                        activeFolderId = folderId
                        isInFolder = true
                    },
                    onNewFolder = { showNewFolderDialog = true },
                    onOpenSettings = { isSettingsOpen = true },
                    onEditFolder = { folderToEdit = it },
                    onDeleteFolder = { folderToDelete = it },
                )
            }
        }

        // New folder dialog
        if (showNewFolderDialog) {
            FolderDialog(
                title = "New folder",
                initialName = "",
                initialEmoji = "📁",
                initialColor = 0xFF4F46E5L,
                onDismiss = { showNewFolderDialog = false },
                onSave = { name, emoji, color ->
                    vm.addFolder(name, emoji, color)
                    showNewFolderDialog = false
                },
            )
        }

        // Edit folder dialog
        folderToEdit?.let { summary ->
            if (!summary.isUnsorted && summary.id != null) {
                FolderDialog(
                    title = "Rename folder",
                    initialName = summary.name,
                    initialEmoji = summary.emoji,
                    initialColor = summary.color,
                    onDismiss = { folderToEdit = null },
                    onSave = { name, emoji, color ->
                        vm.renameFolder(summary.id, name, emoji, color)
                        folderToEdit = null
                    },
                )
            }
        }

        // Delete folder confirmation
        folderToDelete?.let { summary ->
            if (!summary.isUnsorted && summary.id != null) {
                AlertDialog(
                    onDismissRequest = { folderToDelete = null },
                    title = { Text("Delete folder?") },
                    text = { Text("Delete “${summary.emoji} ${summary.name}”? All products inside will be moved to Unsorted.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                vm.deleteFolder(summary.id)
                                folderToDelete = null
                            }
                        ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { folderToDelete = null }) { Text("Cancel") }
                    },
                )
            }
        }

        // Add Product Dialog (from inside folder)
        if (showAddProductDialog) {
            AddProductDialog(
                initialUrl = addDialogInitialUrl,
                onDismiss = { showAddProductDialog = false },
                onSave = { url ->
                    vm.add(url, addDialogTargetFolderId, "Other")
                    showAddProductDialog = false
                },
            )
        }

        // Shared link folder picker bottom sheet
        pendingShareUrl?.let { url ->
            val folderSummaries by vm.folderSummaries.collectAsState()
            ModalBottomSheet(
                onDismissRequest = { pendingShareUrl = null },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text("Save to folder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                    ) {
                        items(folderSummaries, key = { it.id ?: -1L }) { f ->
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        vm.add(url, f.id, "Other")
                                        pendingShareUrl = null
                                    },
                                leadingContent = { Text(f.emoji, fontSize = 24.sp) },
                                headlineContent = { Text(f.name, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("${f.count} items · ${f.totalValue}") },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            showNewFolderDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(painterResource(R.drawable.ic_new_folder), contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create new folder")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        // Manual price dialog
        productForManualPrice?.let { p ->
            NumberDialog(
                title = "Set price manually",
                initial = p.price,
                allowClear = false,
                onDismiss = { productForManualPrice = null },
                onSave = { newPrice ->
                    newPrice?.let { vm.setManualPrice(p, it) }
                    productForManualPrice = null
                },
            )
        }
    }
}

// -------------------------------------------------------------
// HOME SCREEN (Folder-First, Material 3 Tinted Cards)
// -------------------------------------------------------------
@Composable
private fun HomeScreen(
    vm: MainVm,
    snack: SnackbarHostState,
    onOpenFolder: (Long?) -> Unit,
    onNewFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditFolder: (FolderSummary) -> Unit,
    onDeleteFolder: (FolderSummary) -> Unit,
) {
    val folderSummaries by vm.folderSummaries.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    var folderMenuTarget by remember { mutableStateOf<FolderSummary?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("DropWatch", fontWeight = FontWeight.Bold) },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { vm.refreshAll() }) {
                            Icon(Icons.Default.Refresh, "Refresh all")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                    ThemeMenuButton(vm)
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewFolder,
                icon = { Icon(painterResource(R.drawable.ic_new_folder), contentDescription = null, Modifier.size(22.dp)) },
                text = { Text("New folder") },
            )
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    "My Folders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }

            items(folderSummaries, key = { it.id ?: -1L }) { f ->
                FolderCard(
                    summary = f,
                    onClick = { onOpenFolder(f.id) },
                    onLongClick = {
                        if (!f.isUnsorted) folderMenuTarget = f
                    },
                )
            }
        }

        // Folder options menu sheet
        folderMenuTarget?.let { f ->
            ModalBottomSheet(
                onDismissRequest = { folderMenuTarget = null },
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text("${f.emoji} ${f.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable {
                            folderMenuTarget = null
                            onEditFolder(f)
                        },
                        leadingContent = { Icon(Icons.Default.Edit, null) },
                        headlineContent = { Text("Edit folder & color") },
                    )
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable {
                            folderMenuTarget = null
                            onDeleteFolder(f)
                        },
                        leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        headlineContent = { Text("Delete folder", color = MaterialTheme.colorScheme.error) },
                        supportingContent = { Text("Items will be moved to Unsorted") },
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    summary: FolderSummary,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val folderColor = Color(summary.color.toInt())
    val isDark = isSystemInDarkTheme()

    val cardBg = if (isDark) {
        folderColor.copy(alpha = 0.22f)
    } else {
        folderColor.copy(alpha = 0.12f)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, folderColor.copy(alpha = if (isDark) 0.35f else 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(summary.emoji, fontSize = 34.sp)
                if (summary.latestImageUrl != null) {
                    ProductAsyncImage(
                        imageUrl = summary.latestImageUrl,
                        pageUrl = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface),
                    )
                } else {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_placeholder),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Column {
                Text(
                    summary.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${summary.count} ${if (summary.count == 1) "item" else "items"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    summary.totalValue,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = folderColor,
                )
            }
        }
    }
}

// -------------------------------------------------------------
// FOLDER SCREEN (Sorting, Reorder, Search, Notes, Selection, Bill)
// -------------------------------------------------------------
@Composable
private fun FolderCatalogScreen(
    vm: MainVm,
    folderId: Long?,
    folderName: String,
    folderEmoji: String,
    snack: SnackbarHostState,
    greenColor: Color,
    onBack: () -> Unit,
    onOpenHistory: (Long) -> Unit,
    onSetPriceManually: (Product) -> Unit,
    onAddLink: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val products by vm.productsForFolder(folderId).collectAsState(initial = emptyList())
    val refreshing by vm.refreshing.collectAsState()
    val folderSummaries by vm.folderSummaries.collectAsState()
    val currentSummary = remember(folderSummaries, folderId) {
        folderSummaries.firstOrNull { it.id == folderId }
    }
    var activeSortOption by remember(folderId, currentSummary?.sortOption) {
        mutableStateOf(FolderSortOption.fromId(currentSummary?.sortOption))
    }
    var isGrid by rememberSaveable { mutableStateOf(true) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // 2) Gallery-style selection state
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var productForNote by remember { mutableStateOf<Product?>(null) }
    var productToConfirm by remember { mutableStateOf<Product?>(null) }
    var productToDeleteSingle by remember { mutableStateOf<Product?>(null) }
    var showDeleteMultiDialog by remember { mutableStateOf(false) }

    val allSelected = products.isNotEmpty() && products.all { it.id in selectedIds }
    val selectedProducts = remember(products, selectedIds) {
        products.filter { it.id in selectedIds }
    }

    // Currency totals for selected products
    val selectedWithPrice = selectedProducts.filter { it.price != null && it.price > 0 }
    val withoutPriceCount = selectedProducts.size - selectedWithPrice.size
    val totalsByCurrency = selectedWithPrice
        .groupBy { it.currency?.uppercase() ?: "INR" }
        .mapValues { entry -> entry.value.sumOf { it.price ?: 0.0 } }

    val totalText = if (totalsByCurrency.isEmpty()) {
        "₹0"
    } else {
        totalsByCurrency.entries.joinToString(" · ") { (cur, sum) -> money(sum, cur) }
    }

    // Sort / filter
    val filteredProducts = remember(products, searchQuery, activeSortOption) {
        val base = if (searchQuery.isBlank()) products else {
            val q = searchQuery.trim()
            products.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.domain.contains(q, ignoreCase = true) ||
                    (!it.note.isNullOrBlank() && it.note.contains(q, ignoreCase = true))
            }
        }
        when (activeSortOption) {
            FolderSortOption.MY_ORDER   -> base.sortedWith(compareBy<Product> { it.position }.thenBy { it.addedAt })
            FolderSortOption.DATE_DESC  -> base.sortedByDescending { it.addedAt }
            FolderSortOption.DATE_ASC   -> base.sortedBy { it.addedAt }
            FolderSortOption.NAME_ASC   -> base.sortedBy { it.title.lowercase() }
            FolderSortOption.NAME_DESC  -> base.sortedByDescending { it.title.lowercase() }
            FolderSortOption.PRICE_ASC  -> base.sortedWith { a, b ->
                val pa = a.price; val pb = b.price
                when { pa != null && pa > 0 && pb != null && pb > 0 -> pa.compareTo(pb); pa != null && pa > 0 -> -1; pb != null && pb > 0 -> 1; else -> a.position.compareTo(b.position) }
            }
            FolderSortOption.PRICE_DESC -> base.sortedWith { a, b ->
                val pa = a.price; val pb = b.price
                when { pa != null && pa > 0 && pb != null && pb > 0 -> pb.compareTo(pa); pa != null && pa > 0 -> -1; pb != null && pb > 0 -> 1; else -> a.position.compareTo(b.position) }
            }
        }
    }
    var localProducts by remember(filteredProducts) { mutableStateOf(filteredProducts) }
    LaunchedEffect(filteredProducts) { localProducts = filteredProducts }
    val isReorderEnabled = activeSortOption == FolderSortOption.MY_ORDER && searchQuery.isBlank() && !isSelectionMode

    // Intercept back button when in selection mode
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            if (isSelectionMode) {
                // Contextual selection top bar
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, "Cancel selection")
                        }
                    },
                    title = {
                        Text("${selectedIds.size} selected", fontWeight = FontWeight.Bold)
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                selectedIds = if (allSelected) emptySet() else products.map { it.id }.toSet()
                            }
                        ) {
                            Text(if (allSelected) "Clear" else "Select all")
                        }
                        // Total indicator
                        Text(
                            totalText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                        // Bill action
                        IconButton(
                            onClick = {
                                if (selectedProducts.isEmpty()) {
                                    scope.launch { snack.showSnackbar("Select at least 1 product to create a bill") }
                                } else {
                                    val bill = BillHelper.generateBill(folderName, selectedProducts)
                                    val (downloadUri, shareUri) = BillHelper.saveBillAndGetShareUri(context, folderName, bill)
                                    scope.launch {
                                        val res = snack.showSnackbar(
                                            message = "Bill saved to Downloads",
                                            actionLabel = "Share",
                                            duration = SnackbarDuration.Long,
                                        )
                                        if (res == SnackbarResult.ActionPerformed) {
                                            BillHelper.shareBill(context, shareUri ?: downloadUri, bill, folderName)
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(painterResource(R.drawable.ic_receipt), "Print bill", Modifier.size(22.dp))
                        }
                        // Delete selected action
                        IconButton(
                            onClick = {
                                if (selectedIds.isNotEmpty()) showDeleteMultiDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Delete, "Delete selected")
                        }
                    },
                )
            } else {
                // Normal catalog top bar
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back to folders")
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(folderEmoji, fontSize = 22.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(folderName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    actions = {
                        // Sort menu
                        var showSortMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(painterResource(R.drawable.ic_sort), "Sort", Modifier.size(22.dp))
                            }
                            DropdownMenu(showSortMenu, { showSortMenu = false }) {
                                FolderSortOption.entries.forEach { option ->
                                    val sel = option == activeSortOption
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = sel, onClick = null, modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(option.label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                            }
                                        },
                                        trailingIcon = if (sel) {{ Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }} else null,
                                        onClick = {
                                            showSortMenu = false
                                            activeSortOption = option
                                            vm.setFolderSortOption(folderId, option)
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { isGrid = !isGrid }) {
                            Icon(
                                painter = painterResource(if (isGrid) R.drawable.ic_view_list else R.drawable.ic_grid_view),
                                contentDescription = if (isGrid) "Switch to list" else "Switch to grid",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        ThemeMenuButton(vm)
                    },
                )
            }
        },
        bottomBar = {
            // Bottom bar ONLY appears in selection mode
            AnimatedVisibility(visible = isSelectionMode) {
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "Selected: ${selectedProducts.size} items · Total $totalText",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            if (withoutPriceCount > 0) {
                                Text(
                                    "$withoutPriceCount without price",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // Hide FAB in selection mode
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onAddLink,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add link") },
                )
            }
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refreshAll() },
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
            // Search box (matches title, domain, note)
            if (products.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name, domain, note…") },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, "Clear", Modifier.size(18.dp)) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            if (products.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(folderEmoji, fontSize = 54.sp)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "No products in $folderName yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tap “Add link” below or share a product link directly from your browser.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else if (localProducts.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("No matches for \"$searchQuery\"", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (isGrid) {
                val gridState = rememberLazyGridState()
                val reorderGridState = rememberReorderableLazyGridState(gridState) { from, to ->
                    localProducts = localProducts.toMutableList().apply { add(to.index, removeAt(from.index)) }
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, if (isSelectionMode) 80.dp else 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(localProducts, key = { it.id }) { p ->
                    ReorderableItem(reorderGridState, key = p.id, enabled = isReorderEnabled) { isDragging ->
                        val isSelected = p.id in selectedIds
                        ProductCatalogGridCard(
                            p = p,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            greenColor = greenColor,
                            isReorderEnabled = isReorderEnabled,
                            isDragging = isDragging,
                            dragModifier = Modifier.draggableHandle(enabled = isReorderEnabled, onDragStopped = { vm.updatePositions(localProducts.map { it.id }) }),
                            onDisabledDragHint = { scope.launch { snack.showSnackbar("Switch to 'My order' to rearrange") } },
                            onClick = {
                                if (isSelectionMode) {
                                    selectedIds = if (isSelected) selectedIds - p.id else selectedIds + p.id
                                } else productToConfirm = p
                            },
                            onLongClick = { if (!isSelectionMode) { selectedIds = setOf(p.id); isSelectionMode = true } },
                            onOpenNote = { productForNote = p },
                            onPriceHistory = { onOpenHistory(p.id) },
                            onCopyLink = { clipboardManager.setText(AnnotatedString(p.url)); scope.launch { snack.showSnackbar("Link copied") } },
                            onDelete = { productToDeleteSingle = p },
                            onSetPrice = { onSetPriceManually(p) },
                        )
                    } } // ReorderableItem + items
                } // LazyVerticalGrid
            } else {
                val listState = rememberLazyListState()
                val reorderListState = rememberReorderableLazyListState(listState) { from, to ->
                    localProducts = localProducts.toMutableList().apply { add(to.index, removeAt(from.index)) }
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, if (isSelectionMode) 80.dp else 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(localProducts, key = { it.id }) { p ->
                    ReorderableItem(reorderListState, key = p.id, enabled = isReorderEnabled) { isDragging ->
                        val isSelected = p.id in selectedIds
                        ProductCatalogListCard(
                            p = p,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            greenColor = greenColor,
                            isReorderEnabled = isReorderEnabled,
                            isDragging = isDragging,
                            dragModifier = Modifier.draggableHandle(enabled = isReorderEnabled, onDragStopped = { vm.updatePositions(localProducts.map { it.id }) }),
                            onDisabledDragHint = { scope.launch { snack.showSnackbar("Switch to 'My order' to rearrange") } },
                            onClick = {
                                if (isSelectionMode) {
                                    selectedIds = if (isSelected) selectedIds - p.id else selectedIds + p.id
                                } else productToConfirm = p
                            },
                            onLongClick = { if (!isSelectionMode) { selectedIds = setOf(p.id); isSelectionMode = true } },
                            onOpenNote = { productForNote = p },
                            onPriceHistory = { onOpenHistory(p.id) },
                            onCopyLink = { clipboardManager.setText(AnnotatedString(p.url)); scope.launch { snack.showSnackbar("Link copied") } },
                            onDelete = { productToDeleteSingle = p },
                            onSetPrice = { onSetPriceManually(p) },
                        )
                    } } // ReorderableItem + items
                } // LazyColumn
            }
            } // Column wrapper
        } // PullToRefreshBox

        // Note dialog
        productForNote?.let { prod ->
            NoteDialog(
                productTitle = prod.title,
                initialNote = prod.note ?: "",
                onDismiss = { productForNote = null },
                onSave = { vm.updateNote(prod.id, it); productForNote = null },
                onDeleteNote = { vm.updateNote(prod.id, null); productForNote = null },
            )
        }

        // Visit product dialog
        productToConfirm?.let { prod ->
            AlertDialog(
                onDismissRequest = { productToConfirm = null },
                title = { Text("Visit product link?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(prod.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(prod.domain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val url = prod.url
                            productToConfirm = null
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    ) {
                        Text("Visit")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { productToConfirm = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        // Delete single product confirmation
        productToDeleteSingle?.let { prod ->
            AlertDialog(
                onDismissRequest = { productToDeleteSingle = null },
                title = { Text("Remove product?") },
                text = { Text("“${prod.title}” will be removed along with its price history.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.delete(prod.id)
                            productToDeleteSingle = null
                        }
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { productToDeleteSingle = null }) { Text("Cancel") }
                },
            )
        }

        // Delete multiple products confirmation
        if (showDeleteMultiDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteMultiDialog = false },
                title = { Text("Delete ${selectedIds.size} products?") },
                text = { Text("The selected products and their price history will be permanently deleted.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.deleteMultiple(selectedIds)
                            showDeleteMultiDialog = false
                            isSelectionMode = false
                            selectedIds = emptySet()
                        }
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteMultiDialog = false }) { Text("Cancel") }
                },
            )
        }
    }
}

// -------------------------------------------------------------
// CATALOG PRODUCT CARD (GRID VIEW)
// -------------------------------------------------------------
@Composable
private fun ProductCatalogGridCard(
    p: Product,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    greenColor: Color,
    isReorderEnabled: Boolean = false,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
    onDisabledDragHint: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenNote: () -> Unit = {},
    onPriceHistory: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit,
    onSetPrice: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var menuExpanded by remember { mutableStateOf(false) }

    val dropped = p.price != null && p.previousPrice != null && p.price < p.previousPrice
    val pctOff = if (dropped && p.previousPrice != null && p.price != null && p.previousPrice > 0) {
        (((p.previousPrice - p.price) / p.previousPrice) * 100).roundToInt()
    } else 0

    val checkedText = if (p.lastCheckedAt > 0) {
        DateUtils.getRelativeTimeSpanString(p.lastCheckedAt).toString()
    } else ""

    val border = when {
        isSelectionMode && isSelected -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
        !p.note.isNullOrBlank() -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
        else -> null
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 2.dp),
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(140.dp)) {
                ProductAsyncImage(
                    imageUrl = p.imageUrl,
                    pageUrl = p.url,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )

                // Bottom gradient scrim
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                            )
                        )
                )

                // Selection Checkbox with smooth animation
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                ) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                // Top-start: drag handle + note icon
                if (!isSelectionMode) {
                    Row(
                        Modifier.align(Alignment.TopStart).padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                .then(if (isReorderEnabled) dragModifier else Modifier.clickable { onDisabledDragHint() }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_drag_handle), null,
                                tint = if (isReorderEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp))
                        }
                        if (!p.note.isNullOrBlank()) {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f))
                                    .clickable { onOpenNote() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(painterResource(R.drawable.ic_note), "Note",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // ⋮ Overflow menu button (hidden in selection mode)
                if (!isSelectionMode) {
                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                        ) {
                            Icon(Icons.Default.MoreVert, "Options", Modifier.size(18.dp))
                        }

                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(painterResource(R.drawable.ic_note), null, Modifier.size(18.dp)) },
                                text = { Text(if (p.note.isNullOrBlank()) "Add note" else "Edit note") },
                                onClick = { menuExpanded = false; onOpenNote() },
                            )
                            DropdownMenuItem(
                                text = { Text("Price history") },
                                onClick = { menuExpanded = false; onPriceHistory() },
                            )
                            DropdownMenuItem(
                                text = { Text("Copy link") },
                                onClick = { menuExpanded = false; onCopyLink() },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuExpanded = false; onDelete() },
                            )
                        }
                    }
                }
            }

            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    p.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                // Store chip
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        p.domain,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                if (p.status == "no_price" || p.price == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (p.status == "fetching") "Fetching price…" else "Price unavailable",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (p.status == "fetching") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(
                            onClick = onSetPrice,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                        ) {
                            Text("Set price", fontSize = 11.sp)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            money(p.price, p.currency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (dropped) greenColor else MaterialTheme.colorScheme.onSurface,
                        )
                        if (dropped) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = greenColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    "▼ $pctOff% off",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = greenColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                if (checkedText.isNotBlank()) {
                    Text(
                        "Checked $checkedText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CATALOG PRODUCT CARD (LIST VIEW)
// -------------------------------------------------------------
@Composable
private fun ProductCatalogListCard(
    p: Product,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    greenColor: Color,
    isReorderEnabled: Boolean = false,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
    onDisabledDragHint: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenNote: () -> Unit = {},
    onPriceHistory: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit,
    onSetPrice: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var menuExpanded by remember { mutableStateOf(false) }

    val dropped = p.price != null && p.previousPrice != null && p.price < p.previousPrice
    val pctOff = if (dropped && p.previousPrice != null && p.price != null && p.previousPrice > 0) {
        (((p.previousPrice - p.price) / p.previousPrice) * 100).roundToInt()
    } else 0

    val checkedText = if (p.lastCheckedAt > 0) DateUtils.getRelativeTimeSpanString(p.lastCheckedAt).toString() else ""

    val border = when {
        isSelectionMode && isSelected -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
        !p.note.isNullOrBlank() -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
        else -> null
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 1.dp),
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
            ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Drag handle (only when not in selection mode)
            if (!isSelectionMode) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .then(if (isReorderEnabled) dragModifier else Modifier.clickable { onDisabledDragHint() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_drag_handle), null,
                        tint = if (isReorderEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
            }

            AnimatedVisibility(visible = isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.padding(end = 6.dp))
            }

            Box {
                ProductAsyncImage(
                    imageUrl = p.imageUrl, pageUrl = p.url, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(86.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                if (!p.note.isNullOrBlank()) {
                    Box(
                        Modifier.align(Alignment.TopStart).padding(3.dp)
                            .size(22.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .clickable { onOpenNote() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_note), "Note",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(13.dp))
                    }
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(p.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = RoundedCornerShape(6.dp)) {
                    Text("${p.domain} · ${p.category}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }

                if (p.status == "no_price" || p.price == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (p.status == "fetching") "Fetching…" else "No price", style = MaterialTheme.typography.bodySmall,
                            color = if (p.status == "fetching") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = onSetPrice,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)) { Text("Set price", fontSize = 11.sp) }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(money(p.price, p.currency), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (dropped) greenColor else MaterialTheme.colorScheme.onSurface)
                        if (dropped) {
                            Spacer(Modifier.width(8.dp))
                            Surface(color = greenColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                Text("▼ $pctOff% off", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                    color = greenColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }

                if (checkedText.isNotBlank()) {
                    Text("Checked $checkedText", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 10.sp)
                }
            }

            // ⋮ Menu (not in selection mode)
            if (!isSelectionMode) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Options") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(painterResource(R.drawable.ic_note), null, Modifier.size(18.dp)) },
                            text = { Text(if (p.note.isNullOrBlank()) "Add note" else "Edit note") },
                            onClick = { menuExpanded = false; onOpenNote() },
                        )
                        DropdownMenuItem(text = { Text("Price history") }, onClick = { menuExpanded = false; onPriceHistory() })
                        DropdownMenuItem(text = { Text("Copy link") }, onClick = { menuExpanded = false; onCopyLink() })
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SETTINGS SCREEN
// -------------------------------------------------------------
@Composable
private fun SettingsScreen(
    vm: MainVm,
    snack: SnackbarHostState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val currentInterval by vm.checkIntervalHours.collectAsState()
    val intervals = listOf(1, 3, 6, 12, 24)

    val notificationsEnabled = remember(context) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    ThemeMenuButton(vm)
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier
                .padding(pad)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Notification permission warning banner
            if (!notificationsEnabled) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Notifications are disabled",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Text(
                                "DropWatch needs notification permissions to alert you when prices drop.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text("Open notification settings")
                            }
                        }
                    }
                }
            }

            // Notifications section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Test that price drop alerts appear on your device with sound/vibration.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { vm.sendTestNotification() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Notifications, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Send test notification")
                        }
                    }
                }
            }

            // Check Interval section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Price check frequency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "How often DropWatch runs in the background to check store pages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            intervals.forEach { h ->
                                FilterChip(
                                    selected = currentInterval == h,
                                    onClick = { vm.setCheckInterval(h) },
                                    label = { Text("${h}h") },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            // Manual Check All Now
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Instant price check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Trigger an immediate background check for all saved items via WorkManager.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { vm.checkAllPricesNow() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Check all prices now")
                        }
                    }
                }
            }

            // Battery Optimization Note & Settings
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Battery optimization note", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Android may delay background checks when battery saver is on or when background battery optimization is active. If you want on-time alerts, you can exclude DropWatch from battery restrictions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Battery optimization settings")
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DETAIL SCREEN (With 1-Year Price History Graph & Canvas Line Chart)
// -------------------------------------------------------------
@Composable
private fun DetailScreen(
    vm: MainVm,
    id: Long,
    snack: SnackbarHostState,
    greenColor: Color,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val p by vm.product(id).collectAsState(initial = null)
    val history by vm.history(id).collectAsState(initial = emptyList())
    val folders by vm.folders.collectAsState()

    var dlg by remember { mutableStateOf("") } // "target" | "price" | "delete" | "title"
    var folderMenu by remember { mutableStateOf(false) }

    val current = p
    if (current == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val product: Product = current

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(product.domain, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh(product) }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = { dlg = "delete" }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                    ThemeMenuButton(vm)
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ProductAsyncImage(
                    imageUrl = product.imageUrl,
                    pageUrl = product.url,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        product.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).clickable { dlg = "title" },
                    )
                    IconButton(onClick = { dlg = "title" }) {
                        Icon(Icons.Default.Edit, "Edit title", Modifier.size(20.dp))
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        money(product.price, product.currency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (product.lastCheckedAt > 0) {
                        Text(
                            "Checked " + DateUtils.getRelativeTimeSpanString(product.lastCheckedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (product.status == "error") {
                        Text("Couldn't load the store page last time.", color = MaterialTheme.colorScheme.error)
                    }
                    if (product.status == "no_price") {
                        Text("No price found on page. You can set it manually below.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(product.url))) }) {
                        Text("Open store")
                    }
                    OutlinedButton(onClick = { dlg = "price" }) {
                        Text("Set price")
                    }
                    OutlinedButton(onClick = { dlg = "title" }) {
                        Text("Edit title")
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().clickable { dlg = "target" }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Alert me at or below", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            product.targetPrice?.let { money(it, product.currency) } ?: "Not set (alerts for any drop)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            item {
                Box {
                    OutlinedButton(onClick = { folderMenu = true }) {
                        val currentF = folders.firstOrNull { it.id == product.folderId }
                        val label = if (currentF != null) "${currentF.emoji} ${currentF.name}" else "📦 Unsorted"
                        Text("Folder: $label")
                    }
                    DropdownMenu(folderMenu, { folderMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("📦 Unsorted") },
                            onClick = {
                                vm.update(product.copy(folderId = null))
                                folderMenu = false
                            }
                        )
                        folders.forEach { f ->
                            DropdownMenuItem(
                                text = { Text("${f.emoji} ${f.name}") },
                                onClick = {
                                    vm.update(product.copy(folderId = f.id))
                                    folderMenu = false
                                },
                            )
                        }
                    }
                }
            }

            item {
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SuggestedCategories) { c ->
                        FilterChip(
                            selected = product.category == c,
                            onClick = { vm.update(product.copy(category = c)) },
                            label = { Text(c) },
                        )
                    }
                }
            }

            // Note Card on Detail Screen
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (product.note.isNullOrBlank()) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                        }
                    ),
                    border = if (!product.note.isNullOrBlank()) BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary) else null,
                    modifier = Modifier.fillMaxWidth().clickable { dlg = "note" },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_note),
                                    contentDescription = null,
                                    tint = if (!product.note.isNullOrBlank()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Note", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { dlg = "note" }) {
                                Text(if (product.note.isNullOrBlank()) "Add note" else "Edit")
                            }
                        }
                        if (!product.note.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                product.note,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Text(
                                "No note added yet. Tap to add sizes, promo codes, reminders, etc.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 4) ONE YEAR OF PRICE HISTORY WITH A GRAPH & STATS
            item {
                PriceHistorySection(
                    history = history,
                    currentPrice = product.price,
                    currency = product.currency,
                    greenColor = greenColor,
                )
            }

            // Below the chart keep the list of price changes
            if (history.isNotEmpty()) {
                item {
                    Text("Price changes", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                }
                items(history.reversed()) { h ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            DateUtils.formatDateTime(ctx, h.at, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            money(h.price, product.currency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    when (dlg) {
        "target" -> NumberDialog(
            title = "Target price",
            initial = product.targetPrice,
            allowClear = true,
            onDismiss = { dlg = "" },
            onSave = {
                vm.update(product.copy(targetPrice = it))
                dlg = ""
            },
        )
        "price" -> NumberDialog(
            title = "Current price",
            initial = product.price,
            allowClear = false,
            onDismiss = { dlg = "" },
            onSave = {
                it?.let { v -> vm.setManualPrice(product, v) }
                dlg = ""
            },
        )
        "title" -> TextEditDialog(
            title = "Edit product title",
            initial = product.title,
            onDismiss = { dlg = "" },
            onSave = {
                if (it.isNotBlank()) vm.updateTitle(product, it)
                dlg = ""
            },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { dlg = "" },
            title = { Text("Remove this product?") },
            text = { Text("“${product.title}” will be deleted along with its price history.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(product.id)
                    dlg = ""
                    onBack()
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dlg = "" }) { Text("Cancel") } },
        )
        "note" -> NoteDialog(
            productTitle = product.title,
            initialNote = product.note ?: "",
            onDismiss = { dlg = "" },
            onSave = {
                vm.updateNote(product.id, it)
                dlg = ""
            },
            onDeleteNote = {
                vm.updateNote(product.id, null)
                dlg = ""
            },
        )
    }
}

// -------------------------------------------------------------
// PRICE HISTORY SECTION (Range Chips, Canvas Chart, Tooltip, Stats)
// -------------------------------------------------------------
@Composable
private fun PriceHistorySection(
    history: List<PricePoint>,
    currentPrice: Double?,
    currency: String?,
    greenColor: Color,
) {
    var range by rememberSaveable { mutableStateOf("1Y") }
    val ranges = listOf("1M", "3M", "6M", "1Y", "All")

    val now = remember { System.currentTimeMillis() }
    val filtered = remember(history, range) {
        val cutoff = when (range) {
            "1M" -> now - 30L * 24 * 3600 * 1000L
            "3M" -> now - 90L * 24 * 3600 * 1000L
            "6M" -> now - 180L * 24 * 3600 * 1000L
            "1Y" -> now - 365L * 24 * 3600 * 1000L
            else -> 0L
        }
        history.filter { it.at >= cutoff }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Price history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ranges) { r ->
                    FilterChip(
                        selected = range == r,
                        onClick = { range = r },
                        label = { Text(r, fontSize = 12.sp) },
                    )
                }
            }
        }

        if (filtered.size < 2) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Box(Modifier.padding(20.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Not enough data yet. The graph fills in as prices are checked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            // Stats row: Lowest, Highest, Average, Current vs Lowest
            val prices = filtered.map { it.price }
            val minP = prices.minOrNull() ?: 0.0
            val maxP = prices.maxOrNull() ?: 0.0
            val avgP = prices.average()

            val currentVsLowestText = if (currentPrice != null) {
                if (currentPrice <= minP) {
                    "At lowest!"
                } else {
                    val diff = currentPrice - minP
                    val pct = ((diff / minP) * 100).roundToInt()
                    "+${money(diff, currency)} (+$pct%)"
                }
            } else "—"

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatItem("Lowest", money(minP, currency), greenColor)
                    StatItem("Highest", money(maxP, currency))
                    StatItem("Average", money(avgP, currency))
                    StatItem("Vs Lowest", currentVsLowestText, if (currentPrice != null && currentPrice <= minP) greenColor else MaterialTheme.colorScheme.primary)
                }
            }

            // Interactive Canvas Line Chart
            InteractivePriceChart(
                points = filtered,
                currency = currency,
                greenColor = greenColor,
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun InteractivePriceChart(
    points: List<PricePoint>,
    currency: String?,
    greenColor: Color,
) {
    val isDark = isSystemInDarkTheme()
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val textPaintColor = if (isDark) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY

    var touchFraction by remember { mutableStateOf<Float?>(null) }

    val minPrice = remember(points) { points.minOf { it.price } }
    val maxPrice = remember(points) { points.maxOf { it.price } }
    val lowestIndex = remember(points) { points.indexOfFirst { it.price == minPrice } }

    val range = (maxPrice - minPrice).takeIf { it > 0 } ?: 1.0

    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .pointerInput(points) {
                detectTapGestures(
                    onTap = { offset ->
                        val leftPad = 54.dp.toPx()
                        val rightPad = 16.dp.toPx()
                        val chartW = size.width - leftPad - rightPad
                        if (chartW > 0) {
                            val relX = (offset.x - leftPad).coerceIn(0f, chartW)
                            touchFraction = relX / chartW
                        }
                    }
                )
            }
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val leftPad = 54.dp.toPx()
                        val rightPad = 16.dp.toPx()
                        val chartW = size.width - leftPad - rightPad
                        if (chartW > 0) {
                            val relX = (offset.x - leftPad).coerceIn(0f, chartW)
                            touchFraction = relX / chartW
                        }
                    },
                    onDrag = { change, _ ->
                        val leftPad = 54.dp.toPx()
                        val rightPad = 16.dp.toPx()
                        val chartW = size.width - leftPad - rightPad
                        if (chartW > 0) {
                            val relX = (change.position.x - leftPad).coerceIn(0f, chartW)
                            touchFraction = relX / chartW
                        }
                    },
                    onDragEnd = { touchFraction = null },
                    onDragCancel = { touchFraction = null },
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val leftPad = 54.dp.toPx()
            val rightPad = 16.dp.toPx()
            val topPad = 26.dp.toPx()
            val bottomPad = 28.dp.toPx()

            val chartW = size.width - leftPad - rightPad
            val chartH = size.height - topPad - bottomPad

            if (chartW <= 0 || chartH <= 0) return@Canvas

            val paint = android.graphics.Paint().apply {
                color = textPaintColor
                textSize = 9.sp.toPx()
                isAntiAlias = true
            }

            // 1. Horizontal gridlines & Y-axis labels (max, mid, min)
            val midPrice = (maxPrice + minPrice) / 2.0
            val yLevels = listOf(
                maxPrice to topPad,
                midPrice to (topPad + chartH / 2f),
                minPrice to (topPad + chartH),
            )

            for ((pVal, yPos) in yLevels) {
                drawLine(
                    color = gridColor,
                    start = Offset(leftPad, yPos),
                    end = Offset(size.width - rightPad, yPos),
                    strokeWidth = 1.dp.toPx(),
                )
                val label = money(pVal, currency)
                drawContext.canvas.nativeCanvas.drawText(label, 8.dp.toPx(), yPos + 4.dp.toPx(), paint)
            }

            // 2. X-axis date labels (start, mid, end)
            val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            val startDate = dateFormat.format(Date(points.first().at))
            val midDate = dateFormat.format(Date(points[points.size / 2].at))
            val endDate = dateFormat.format(Date(points.last().at))

            val yDate = size.height - 8.dp.toPx()
            drawContext.canvas.nativeCanvas.drawText(startDate, leftPad, yDate, paint)
            drawContext.canvas.nativeCanvas.drawText(midDate, leftPad + chartW / 2f - 18.dp.toPx(), yDate, paint)
            val endPaint = android.graphics.Paint(paint).apply { textAlign = android.graphics.Paint.Align.RIGHT }
            drawContext.canvas.nativeCanvas.drawText(endDate, size.width - rightPad, yDate, endPaint)

            // 3. Compute point coordinates
            val coords = points.mapIndexed { i, p ->
                val x = leftPad + chartW * i / (points.size - 1)
                val y = topPad + (chartH * (1.0 - ((p.price - minPrice) / range))).toFloat()
                Offset(x, y)
            }

            // 4. Gradient filled area under line
            val fillPath = Path().apply {
                moveTo(coords.first().x, topPad + chartH)
                coords.forEach { lineTo(it.x, it.y) }
                lineTo(coords.last().x, topPad + chartH)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.32f), primaryColor.copy(alpha = 0.02f)),
                    startY = topPad,
                    endY = topPad + chartH,
                )
            )

            // 5. Line curve
            val linePath = Path().apply {
                coords.forEachIndexed { i, pt ->
                    if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
            }
            drawPath(
                path = linePath,
                color = primaryColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // 6. Dot on latest price
            val lastPt = coords.last()
            drawCircle(primaryColor.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = lastPt)
            drawCircle(primaryColor, radius = 4.dp.toPx(), center = lastPt)

            // 7. Lowest price marked in green
            if (lowestIndex in coords.indices) {
                val lowPt = coords[lowestIndex]
                drawCircle(greenColor, radius = 5.dp.toPx(), center = lowPt)
                drawCircle(Color.White, radius = 2.dp.toPx(), center = lowPt)
            }

            // 8. Tooltip on touch / drag
            touchFraction?.let { frac ->
                val idx = (frac * (coords.size - 1)).roundToInt().coerceIn(0, coords.size - 1)
                val touchPt = coords[idx]
                val ptData = points[idx]

                // Vertical dashed indicator line
                drawLine(
                    color = primaryColor.copy(alpha = 0.6f),
                    start = Offset(touchPt.x, topPad),
                    end = Offset(touchPt.x, topPad + chartH),
                    strokeWidth = 1.5.dp.toPx(),
                )

                // Dot on selected point
                drawCircle(primaryColor, radius = 6.dp.toPx(), center = touchPt)
                drawCircle(Color.White, radius = 3.dp.toPx(), center = touchPt)

                // Tooltip card background and text
                val tipDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ptData.at))
                val tipPrice = money(ptData.price, currency)
                val tipText = "$tipPrice · $tipDate"

                val tipPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 10.sp.toPx()
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val textW = tipPaint.measureText(tipText)
                val boxW = textW + 16.dp.toPx()
                val boxH = 22.dp.toPx()
                val boxX = (touchPt.x - boxW / 2f).coerceIn(leftPad, size.width - rightPad - boxW)
                val boxY = (touchPt.y - boxH - 10.dp.toPx()).coerceAtLeast(topPad - 6.dp.toPx())

                drawRoundRect(
                    color = Color(0xFF1E293B),
                    topLeft = Offset(boxX, boxY),
                    size = androidx.compose.ui.geometry.Size(boxW, boxH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    tipText,
                    boxX + 8.dp.toPx(),
                    boxY + boxH - 6.dp.toPx(),
                    tipPaint
                )
            }
        }
    }
}

// -------------------------------------------------------------
// REUSABLE ASYNC IMAGE WITH HEADERS & PLACEHOLDERS
// -------------------------------------------------------------
@Composable
fun ProductAsyncImage(
    imageUrl: String?,
    pageUrl: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val model = remember(imageUrl, pageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .setHeader("User-Agent", Scraper.UA)
            .apply {
                if (!pageUrl.isNullOrBlank()) {
                    setHeader("Referer", pageUrl)
                }
            }
            .build()
    }

    AsyncImage(
        model = model,
        contentDescription = null,
        placeholder = painterResource(R.drawable.ic_placeholder),
        error = painterResource(R.drawable.ic_image_error),
        contentScale = contentScale,
        modifier = modifier,
    )
}

// -------------------------------------------------------------
// THEME MENU BUTTON
// -------------------------------------------------------------
@Composable
private fun ThemeMenuButton(vm: MainVm) {
    var expanded by remember { mutableStateOf(false) }
    val currentMode by vm.themeMode.collectAsState()

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_theme),
                contentDescription = "Theme mode",
                modifier = Modifier.size(22.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Follow system" + if (currentMode == ThemeMode.SYSTEM) " ✓" else "") },
                onClick = {
                    vm.setThemeMode(ThemeMode.SYSTEM)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Light theme" + if (currentMode == ThemeMode.LIGHT) " ✓" else "") },
                onClick = {
                    vm.setThemeMode(ThemeMode.LIGHT)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Dark theme" + if (currentMode == ThemeMode.DARK) " ✓" else "") },
                onClick = {
                    vm.setThemeMode(ThemeMode.DARK)
                    expanded = false
                },
            )
        }
    }
}

// -------------------------------------------------------------
// DIALOGS
// -------------------------------------------------------------
@Composable
private fun AddProductDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var url by remember {
        val clipText = clipboardManager.getText()?.text?.trim() ?: ""
        val candidate = if (initialUrl.isNotBlank()) {
            initialUrl
        } else if (clipText.startsWith("http://", ignoreCase = true) || clipText.startsWith("https://", ignoreCase = true)) {
            clipText
        } else {
            ""
        }
        mutableStateOf(candidate)
    }

    val trimmed = url.trim()
    val isValidUrl = remember(trimmed) {
        (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
            trimmed.length > 10 &&
            trimmed.contains('.') &&
            !trimmed.contains(' ')
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add product link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Product link") },
                    placeholder = { Text("https://store.com/item...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailingIcon = {
                        if (url.isNotEmpty()) {
                            IconButton(onClick = { url = "" }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = isValidUrl,
                onClick = { if (isValidUrl) onSave(trimmed) }
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NoteDialog(
    productTitle: String,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
    onDeleteNote: (() -> Unit)? = null,
) {
    var note by remember { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_note), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text(if (initialNote.isBlank()) "Add note" else "Edit note")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    productTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    placeholder = { Text("Size, color, promo code, reminders…") },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(note.trim().ifBlank { null }) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (initialNote.isNotBlank() && onDeleteNote != null) {
                    TextButton(
                        onClick = onDeleteNote,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete note")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun FolderDialog(
    title: String,
    initialName: String,
    initialEmoji: String,
    initialColor: Long,
    onDismiss: () -> Unit,
    onSave: (String, String, Long) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji) }
    var selectedColor by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Denim)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(4) },
                    label = { Text("Emoji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Folder Color", style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(FolderColorPalette) { c ->
                        val color = Color(c.toInt())
                        val isSelected = selectedColor == c
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = c }
                                .then(
                                    if (isSelected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name, emoji, selectedColor) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberDialog(
    title: String,
    initial: Double?,
    allowClear: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double?) -> Unit,
) {
    var text by remember {
        mutableStateOf(initial?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val v = Scraper.parsePrice(text)
                if (v != null || (allowClear && text.isBlank())) onSave(v)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TextEditDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Product title") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
