package com.omsati.dropwatch

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// -------------------------------------------------------------
// COLLAPSING HEADER STATE
// -------------------------------------------------------------
class CollapsingHeaderState {
    var heightPx by mutableFloatStateOf(0f)
    var offsetPx by mutableFloatStateOf(0f)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (heightPx <= 0f) return Offset.Zero
            val new = (offsetPx + available.y).coerceIn(-heightPx, 0f)
            val consumed = new - offsetPx
            offsetPx = new
            return Offset(0f, consumed)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MahoRoot(
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

    MahoTheme(isDark = isDark) {
        val snack = remember { SnackbarHostState() }

        // Navigation state
        var isAboutOpen by rememberSaveable { mutableStateOf(false) }
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
        BackHandler(enabled = (selectedProductId != null || isAboutOpen || isInFolder || isSettingsOpen)) {
            when {
                selectedProductId != null -> selectedProductId = null
                isAboutOpen -> isAboutOpen = false
                isInFolder -> {
                    isInFolder = false
                    activeFolderId = null
                }
                isSettingsOpen -> isSettingsOpen = false
            }
        }

        when {
            isAboutOpen -> {
                AboutScreen(onBack = { isAboutOpen = false })
            }
            isSettingsOpen -> {
                SettingsScreen(
                    vm = vm,
                    snack = snack,
                    onBack = { isSettingsOpen = false },
                    onOpenAbout = { isAboutOpen = true },
                )
            }
            selectedProductId != null -> {
                DetailScreen(
                    vm = vm,
                    id = selectedProductId!!,
                    snack = snack,
                    greenColor = MutedGreen,
                    onBack = { selectedProductId = null },
                )
            }
            isInFolder -> {
                val folders by vm.folders.collectAsState()
                val currentFolder = folders.firstOrNull { it.id == activeFolderId }
                val folderName = if (activeFolderId == null) "Unsorted" else (currentFolder?.name ?: "Folder")

                FolderCatalogScreen(
                    vm = vm,
                    folderId = activeFolderId,
                    folderName = folderName,
                    snack = snack,
                    greenColor = MutedGreen,
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
                    onOpenAbout = { isAboutOpen = true },
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
                initialColor = 0xFF4F46E5L,
                onDismiss = { showNewFolderDialog = false },
                onSave = { name, color ->
                    vm.addFolder(name, "", color)
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
                    initialColor = summary.color,
                    onDismiss = { folderToEdit = null },
                    onSave = { name, color ->
                        vm.renameFolder(summary.id, name, "", color)
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
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    title = { Text("Delete folder?", style = MaterialTheme.typography.titleLarge) },
                    text = { Text("Delete “${summary.name}”? All products inside will be moved to Unsorted.") },
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

        // Shared link bottom sheet picker
        pendingShareUrl?.let { url ->
            val folderSummaries by vm.folderSummaries.collectAsState()
            ModalBottomSheet(
                onDismissRequest = { pendingShareUrl = null },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text("Save to folder", style = MaterialTheme.typography.titleLarge)
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
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        vm.add(url, f.id, "Other")
                                        pendingShareUrl = null
                                    },
                                headlineContent = { Text(f.name, style = MaterialTheme.typography.titleMedium) },
                                supportingContent = { Text("${f.count} items · ${f.totalValue}") },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    PillButton(
                        text = "Create new folder",
                        onClick = { showNewFolderDialog = true },
                        isPrimary = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        // Manual price dialog
        productForManualPrice?.let { p ->
            val defaultCur by vm.defaultCurrency.collectAsState()
            EditPriceAndCurrencyDialog(
                product = p,
                defaultCurrency = defaultCur,
                onDismiss = { productForManualPrice = null },
                onSave = { newPrice, newCurrency ->
                    vm.updatePriceAndCurrency(p, newPrice, newCurrency)
                    productForManualPrice = null
                },
            )
        }
    }
}

// -------------------------------------------------------------
// HOME SCREEN ("Your folders" with Smooth Collapsing Header)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    vm: MainVm,
    snack: SnackbarHostState,
    onOpenFolder: (Long?) -> Unit,
    onNewFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onEditFolder: (FolderSummary) -> Unit,
    onDeleteFolder: (FolderSummary) -> Unit,
) {
    val density = LocalDensity.current
    val folderSummaries by vm.folderSummaries.collectAsState()
    val allProducts by vm.all.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    var folderMenuTarget by remember { mutableStateOf<FolderSummary?>(null) }

    val totalItemsCount = remember(folderSummaries) { folderSummaries.sumOf { it.count } }

    val headerState = remember { CollapsingHeaderState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            PillButton(
                text = "New folder",
                icon = Icons.Default.Add,
                onClick = onNewFolder,
                isPrimary = true,
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refreshAll() },
            modifier = Modifier
                .padding(bottom = innerPadding.calculateBottomPadding())
                .fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(headerState.connection)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(
                        top = with(density) { headerState.heightPx.toDp() } + 8.dp,
                        bottom = 120.dp,
                        start = 20.dp,
                        end = 20.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(folderSummaries, key = { it.id ?: -1L }, contentType = { "folder" }) { summary ->
                        val images = remember(allProducts, summary.id) {
                            allProducts
                                .filter { it.folderId == summary.id && !it.imageUrl.isNullOrBlank() }
                                .mapNotNull { it.imageUrl }
                        }

                        FolderCard(
                            summary = summary,
                            productImages = images,
                            onClick = { onOpenFolder(summary.id) },
                            onLongClick = {
                                if (!summary.isUnsorted) folderMenuTarget = summary
                            },
                        )
                    }
                }

                // Smooth Collapsing Header Column (no recomposition jitter, graphicsLayer translation)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { headerState.heightPx = it.height.toFloat() }
                        .graphicsLayer { translationY = headerState.offsetPx }
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "MAHO",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = 0.1.em,
                                )
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        },
                        actions = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    IconButton(onClick = { vm.refreshAll() }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Refresh, "Refresh all", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = onOpenAbout, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Info, "About", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Settings, "Settings", modifier = Modifier.size(16.dp))
                                    }
                                    ThemeMenuButton(vm, modifier = Modifier.size(32.dp))
                                }
                            }
                        },
                    )

                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(
                            "Your folders",
                            style = MaterialTheme.typography.displayLarge,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${folderSummaries.size} folders · $totalItemsCount items",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        folderMenuTarget?.let { f ->
            ModalBottomSheet(
                onDismissRequest = { folderMenuTarget = null },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text(f.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                            folderMenuTarget = null
                            onEditFolder(f)
                        },
                        leadingContent = { Icon(Icons.Default.Edit, null) },
                        headlineContent = { Text("Edit folder & color") },
                    )
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
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

// -------------------------------------------------------------
// FOLDER CATALOG SCREEN (Smooth Collapsing Header & Search)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderCatalogScreen(
    vm: MainVm,
    folderId: Long?,
    folderName: String,
    snack: SnackbarHostState,
    greenColor: Color,
    onBack: () -> Unit,
    onOpenHistory: (Long) -> Unit,
    onSetPriceManually: (Product) -> Unit,
    onAddLink: () -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val products by vm.productsForFolder(folderId).collectAsState(initial = emptyList())
    val refreshing by vm.refreshing.collectAsState()
    val defaultCurrency by vm.defaultCurrency.collectAsState()
    val folderSummaries by vm.folderSummaries.collectAsState()
    val currentSummary = remember(folderSummaries, folderId) {
        folderSummaries.firstOrNull { it.id == folderId }
    }
    var activeSortOption by remember(folderId, currentSummary?.sortOption) {
        mutableStateOf(FolderSortOption.fromId(currentSummary?.sortOption))
    }
    var isGrid by rememberSaveable { mutableStateOf(true) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<Long>()) }

    var productForOptionsSheet by remember { mutableStateOf<Product?>(null) }
    var productForCurrencyWarning by remember { mutableStateOf<Product?>(null) }
    var productForEditPriceCurrency by remember { mutableStateOf<Product?>(null) }
    var productForNote by remember { mutableStateOf<Product?>(null) }
    var productToConfirmVisit by remember { mutableStateOf<Product?>(null) }
    var productToDeleteSingle by remember { mutableStateOf<Product?>(null) }
    var showDeleteMultiDialog by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }

    val allSelected = products.isNotEmpty() && products.all { it.id in selectedIds }
    val selectedProducts = remember(products, selectedIds) {
        products.filter { it.id in selectedIds }
    }

    val selectedWithPrice = selectedProducts.filter { it.price != null && it.price > 0 }
    val totalsByCurrency = selectedWithPrice
        .groupBy { it.currency?.uppercase() ?: defaultCurrency }
        .mapValues { entry -> entry.value.sumOf { it.price ?: 0.0 } }

    val totalText = if (totalsByCurrency.isEmpty()) {
        money(0.0, defaultCurrency)
    } else {
        totalsByCurrency.entries.joinToString(" · ") { (cur, sum) -> money(sum, cur) }
    }

    val filteredProducts by remember(products, searchQuery, activeSortOption) {
        derivedStateOf {
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
    }

    var localProducts by remember(filteredProducts) { mutableStateOf(filteredProducts) }
    LaunchedEffect(filteredProducts) { localProducts = filteredProducts }
    val isReorderEnabled = activeSortOption == FolderSortOption.MY_ORDER && searchQuery.isBlank() && !isSelectionMode

    val headerState = remember { CollapsingHeaderState() }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (isSelectionMode) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            totalText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(
                            onClick = {
                                if (selectedProducts.isNotEmpty()) {
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
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_receipt), "Print bill", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(
                            onClick = { if (selectedIds.isNotEmpty()) showDeleteMultiDialog = true },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(Icons.Default.Delete, "Delete selected", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                PillButton(
                    text = "Add link",
                    icon = Icons.Default.Add,
                    onClick = onAddLink,
                    isPrimary = true,
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refreshAll() },
            modifier = Modifier
                .padding(bottom = innerPadding.calculateBottomPadding())
                .fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(headerState.connection)
            ) {
                if (products.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(top = with(density) { headerState.heightPx.toDp() } + 16.dp, start = 32.dp, end = 32.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 40.dp)) {
                            Text(
                                "No items in $folderName yet",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tap “Add link” below or share a URL directly from your browser.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else if (localProducts.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(top = with(density) { headerState.heightPx.toDp() } + 32.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Text("No matches for \"$searchQuery\"", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 40.dp))
                    }
                } else if (isGrid) {
                    val gridState = rememberLazyGridState()
                    val reorderGridState = rememberReorderableLazyGridState(gridState) { from, to ->
                        localProducts = localProducts.toMutableList().apply { add(to.index, removeAt(from.index)) }
                    }
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(
                            top = with(density) { headerState.heightPx.toDp() } + 8.dp,
                            bottom = 120.dp,
                            start = 20.dp,
                            end = 20.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(localProducts, key = { it.id }, contentType = { "product" }) { p ->
                            ReorderableItem(reorderGridState, key = p.id, enabled = isReorderEnabled) { isDragging ->
                                val isSelected = p.id in selectedIds
                                val haptic = LocalHapticFeedback.current
                                val scale by animateFloatAsState(if (isDragging) 1.05f else 1.0f, label = "scale")

                                ProductCardGrid(
                                    p = p,
                                    defaultCurrency = defaultCurrency,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    greenColor = greenColor,
                                    isReorderEnabled = isReorderEnabled,
                                    isDragging = isDragging,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    },
                                    dragHandleModifier = with(this) {
                                        Modifier.draggableHandle(
                                            enabled = isReorderEnabled,
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragStopped = {
                                                vm.updatePositions(localProducts.map { it.id })
                                            }
                                        )
                                    },
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedIds = if (isSelected) selectedIds - p.id else selectedIds + p.id
                                        } else productToConfirmVisit = p
                                    },
                                    onLongClick = { if (!isSelectionMode) { selectedIds = setOf(p.id); isSelectionMode = true } },
                                    onOptionsClick = { productForOptionsSheet = p },
                                    onWarningChipClick = { productForCurrencyWarning = p },
                                    onSetPrice = { onSetPriceManually(p) },
                                )
                            }
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    val reorderListState = rememberReorderableLazyListState(listState) { from, to ->
                        localProducts = localProducts.toMutableList().apply { add(to.index, removeAt(from.index)) }
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = with(density) { headerState.heightPx.toDp() } + 8.dp,
                            bottom = 120.dp,
                            start = 20.dp,
                            end = 20.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(localProducts, key = { it.id }, contentType = { "product" }) { p ->
                            ReorderableItem(reorderListState, key = p.id, enabled = isReorderEnabled) { isDragging ->
                                val isSelected = p.id in selectedIds
                                val haptic = LocalHapticFeedback.current
                                val scale by animateFloatAsState(if (isDragging) 1.03f else 1.0f, label = "scale")

                                ProductCardList(
                                    p = p,
                                    defaultCurrency = defaultCurrency,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    greenColor = greenColor,
                                    isReorderEnabled = isReorderEnabled,
                                    isDragging = isDragging,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    },
                                    dragHandleModifier = with(this) {
                                        Modifier.draggableHandle(
                                            enabled = isReorderEnabled,
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragStopped = {
                                                vm.updatePositions(localProducts.map { it.id })
                                            }
                                        )
                                    },
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedIds = if (isSelected) selectedIds - p.id else selectedIds + p.id
                                        } else productToConfirmVisit = p
                                    },
                                    onLongClick = { if (!isSelectionMode) { selectedIds = setOf(p.id); isSelectionMode = true } },
                                    onOptionsClick = { productForOptionsSheet = p },
                                    onWarningChipClick = { productForCurrencyWarning = p },
                                    onSetPrice = { onSetPriceManually(p) },
                                )
                            }
                        }
                    }
                }

                // Smooth Collapsing Header Column (pinned in selection mode, graphicsLayer translated when scrolling)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { headerState.heightPx = it.height.toFloat() }
                        .graphicsLayer { translationY = if (isSelectionMode) 0f else headerState.offsetPx }
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (isSelectionMode) {
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
                                Text("${selectedIds.size} selected", style = MaterialTheme.typography.titleLarge)
                            },
                            actions = {
                                TextButton(
                                    onClick = {
                                        selectedIds = if (allSelected) emptySet() else products.map { it.id }.toSet()
                                    }
                                ) {
                                    Text(if (allSelected) "CLEAR" else "SELECT ALL", style = MaterialTheme.typography.labelMedium)
                                }
                            },
                        )
                    } else {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.Default.ArrowBack, "Back to folders")
                                }
                            },
                            title = {
                                Text(folderName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            actions = {
                                ThemeMenuButton(vm)
                            },
                        )

                        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                "Search by title, store, note…",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outline)
                            )

                            Spacer(Modifier.height(10.dp))

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "SORT: ${activeSortOption.name.replace('_', ' ')}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { showSortBottomSheet = true },
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(
                                        text = if (isGrid) "LIST VIEW" else "GRID VIEW",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.clickable { isGrid = !isGrid },
                                    )
                                    Text(
                                        text = "SELECT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.clickable { isSelectionMode = true },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Product Options Bottom Sheet
        productForOptionsSheet?.let { prod ->
            ProductOptionsBottomSheet(
                product = prod,
                onDismiss = { productForOptionsSheet = null },
                onOpenNote = { productForNote = prod },
                onPriceHistory = { onOpenHistory(prod.id) },
                onCopyLink = {
                    clipboardManager.setText(AnnotatedString(prod.url))
                    scope.launch { snack.showSnackbar("Link copied") }
                },
                onChangeCurrencyPrice = { productForEditPriceCurrency = prod },
                onDelete = { productToDeleteSingle = prod },
            )
        }

        // Sort Options Bottom Sheet
        if (showSortBottomSheet) {
            SortBottomSheet(
                activeOption = activeSortOption,
                onDismiss = { showSortBottomSheet = false },
                onSelect = { option ->
                    activeSortOption = option
                    vm.setFolderSortOption(folderId, option)
                },
            )
        }

        // Currency Warning Dialog
        productForCurrencyWarning?.let { prod ->
            CurrencyWarningDialog(
                product = prod,
                defaultCurrency = defaultCurrency,
                onDismiss = { productForCurrencyWarning = null },
                onSwitchCurrency = {
                    vm.switchProductCurrency(prod, defaultCurrency)
                    productForCurrencyWarning = null
                },
                onConvertRate = { rate ->
                    vm.convertProductCurrency(prod, defaultCurrency, rate)
                    productForCurrencyWarning = null
                },
            )
        }

        // Edit Price and Currency Dialog
        productForEditPriceCurrency?.let { prod ->
            EditPriceAndCurrencyDialog(
                product = prod,
                defaultCurrency = defaultCurrency,
                onDismiss = { productForEditPriceCurrency = null },
                onSave = { newPrice, newCurrency ->
                    vm.updatePriceAndCurrency(prod, newPrice, newCurrency)
                    productForEditPriceCurrency = null
                },
            )
        }

        // Note Dialog
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
        productToConfirmVisit?.let { prod ->
            VisitProductDialog(
                product = prod,
                onDismiss = { productToConfirmVisit = null },
                onVisit = {
                    val url = prod.url
                    productToConfirmVisit = null
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
            )
        }

        // Delete single product confirmation
        productToDeleteSingle?.let { prod ->
            AlertDialog(
                onDismissRequest = { productToDeleteSingle = null },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                title = { Text("Remove product?", style = MaterialTheme.typography.titleLarge) },
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
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                title = { Text("Delete ${selectedIds.size} products?", style = MaterialTheme.typography.titleLarge) },
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
// ABOUT SCREEN ("Meet the creature")
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("1.0")
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ABOUT", style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Meet the creature", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Maho saves the stuff you want to buy and yells when it gets cheaper. No ads, no accounts, no tracking. Your list stays on your phone, where it can't embarrass you in public.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "One person built this. He is humble, slightly feral, and he likes talking to people.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "So talk to him. Go to his DMs and shoot your funniest joke. If it's good, he might reply. If it's bad, he'll still read it, and he'll remember.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Primary Accent Button
                    PillButton(
                        text = "Slide into his DMs  @omsneakycow",
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/omsneakycow")))
                            } catch (e: ActivityNotFoundException) {
                                // Handled
                            }
                        },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Secondary Outlined Buttons
                    PillButton(
                        text = "GitHub: oomm00",
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/oomm00")))
                            } catch (e: ActivityNotFoundException) {
                                // Handled
                            }
                        },
                        isPrimary = false,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    PillButton(
                        text = "X: @Omsneakycow",
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://x.com/Omsneakycow")))
                            } catch (e: ActivityNotFoundException) {
                                // Handled
                            }
                        },
                        isPrimary = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Maho v$versionName. Built by a creature, not a company.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SETTINGS SCREEN
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    vm: MainVm,
    snack: SnackbarHostState,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val currentInterval by vm.checkIntervalHours.collectAsState()
    val defaultCur by vm.defaultCurrency.collectAsState()
    val intervals = listOf(1, 3, 6, 12, 24)
    val currencies = listOf("INR" to "₹ INR", "USD" to "$ USD", "EUR" to "€ EUR", "GBP" to "£ GBP")

    val notificationsEnabled = remember(context) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("SETTINGS", style = MaterialTheme.typography.labelLarge) },
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
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (!notificationsEnabled) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Notifications disabled", style = MaterialTheme.typography.titleMedium)
                            Text("Enable notifications to receive price drop alerts.", style = MaterialTheme.typography.bodyMedium)
                            PillButton(
                                text = "Open notification settings",
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                },
                                isPrimary = true,
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ABOUT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().clickable { onOpenAbout() },
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("About Maho & Creator", style = MaterialTheme.typography.bodyLarge)
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DEFAULT CURRENCY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        currencies.forEach { (code, label) ->
                            FilterChip(
                                selected = defaultCur.equals(code, ignoreCase = true),
                                onClick = { vm.setDefaultCurrency(code) },
                                label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PRICE CHECK FREQUENCY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        intervals.forEach { h ->
                            FilterChip(
                                selected = currentInterval == h,
                                onClick = { vm.setCheckInterval(h) },
                                label = { Text("${h}h", style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ACTIONS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PillButton(text = "Check all prices now", onClick = { vm.checkAllPricesNow() }, isPrimary = true, modifier = Modifier.fillMaxWidth())
                    PillButton(text = "Send test notification", onClick = { vm.sendTestNotification() }, isPrimary = false, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DETAIL SCREEN
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(
    vm: MainVm,
    id: Long,
    snack: SnackbarHostState,
    greenColor: Color,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val productState = vm.product(id).collectAsState(initial = null)
    val product = productState.value
    val history by vm.history(id).collectAsState(initial = emptyList())
    val defaultCur by vm.defaultCurrency.collectAsState()

    var dlg by remember { mutableStateOf("") }

    LaunchedEffect(history, id) {
        if (BuildConfig.DEBUG) Log.d("History", "Loaded ${history.size} price points for product $id")
    }

    var isInitialLoading by remember { mutableStateOf(true) }
    LaunchedEffect(id) {
        delay(400)
        isInitialLoading = false
    }

    if (product == null) {
        if (isInitialLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LaunchedEffect(Unit) { onBack() }
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(product.domain.uppercase(), style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { dlg = "currency_price" }) {
                        Icon(Icons.Default.Edit, "Edit price")
                    }
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
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                color = Color.Transparent,
            ) {
                PillButton(
                    text = "Visit store",
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(product.url)))
                        }
                    },
                    isPrimary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 80.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                ProductAsyncImage(
                    imageUrl = product.imageUrl,
                    pageUrl = product.url,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        product.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.clickable { dlg = "title" },
                    )
                    Text(
                        money(product.price, product.currency),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (product.lastCheckedAt > 0) {
                        Text(
                            "LAST CHECKED ${DateUtils.getRelativeTimeSpanString(product.lastCheckedAt).toString().uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                PriceHistorySection(
                    history = history,
                    product = product,
                    currentPrice = product.price,
                    currency = product.currency ?: defaultCur,
                    greenColor = greenColor,
                )
            }
        }

        if (dlg == "delete") {
            AlertDialog(
                onDismissRequest = { dlg = "" },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                title = { Text("Delete product?", style = MaterialTheme.typography.titleLarge) },
                text = { Text("“${product.title}” and its price history will be deleted.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dlg = ""
                            vm.delete(product.id)
                            onBack()
                        }
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { dlg = "" }) { Text("Cancel") }
                },
            )
        }

        if (dlg == "title") {
            TextEditDialog(
                title = "Edit title",
                initial = product.title,
                onDismiss = { dlg = "" },
                onSave = { newTitle ->
                    vm.updateTitle(product, newTitle)
                    dlg = ""
                },
            )
        }

        if (dlg == "currency_price") {
            EditPriceAndCurrencyDialog(
                product = product,
                defaultCurrency = defaultCur,
                onDismiss = { dlg = "" },
                onSave = { newPrice, newCurrency ->
                    vm.updatePriceAndCurrency(product, newPrice, newCurrency)
                    dlg = ""
                },
            )
        }
    }
}

// -------------------------------------------------------------
// PRICE HISTORY SECTION
// -------------------------------------------------------------
@Composable
private fun PriceHistorySection(
    history: List<PricePoint>,
    product: Product,
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("PRICE HISTORY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (filtered.size >= 2) {
            SegmentedTextControl(
                options = ranges,
                selectedOption = range,
                onOptionSelected = { range = it },
            )
        }

        if (filtered.size < 2) {
            val singlePointDate = remember(history, product.addedAt) {
                val at = history.firstOrNull()?.at ?: product.addedAt
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(at))
            }
            val primaryColor = MaterialTheme.colorScheme.primary

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("TRACKING STARTED $singlePointDate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        money(currentPrice, currency),
                        style = MaterialTheme.typography.displayMedium,
                        color = primaryColor,
                    )

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val cy = size.height / 2f
                            drawLine(
                                color = primaryColor.copy(alpha = 0.4f),
                                start = Offset(24.dp.toPx(), cy),
                                end = Offset(size.width - 24.dp.toPx(), cy),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                            )
                            drawCircle(primaryColor, radius = 6.dp.toPx(), center = Offset(size.width / 2f, cy))
                            drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(size.width / 2f, cy))
                        }
                        Text(
                            "Price changes will appear here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 48.dp),
                        )
                    }
                }
            }
        } else {
            val prices = filtered.map { it.price }
            val minP = prices.minOrNull() ?: 0.0
            val maxP = prices.maxOrNull() ?: 0.0
            val avgP = prices.average()

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem("LOWEST", money(minP, currency), greenColor)
                StatItem("HIGHEST", money(maxP, currency))
                StatItem("AVERAGE", money(avgP, currency))
            }

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
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
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
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
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
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
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

            val coords = points.mapIndexed { i, p ->
                val x = leftPad + chartW * i / (points.size - 1)
                val y = topPad + (chartH * (1.0 - ((p.price - minPrice) / range))).toFloat()
                Offset(x, y)
            }

            val fillPath = Path().apply {
                moveTo(coords.first().x, topPad + chartH)
                coords.forEach { lineTo(it.x, it.y) }
                lineTo(coords.last().x, topPad + chartH)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.25f), primaryColor.copy(alpha = 0.01f)),
                    startY = topPad,
                    endY = topPad + chartH,
                )
            )

            val linePath = Path().apply {
                coords.forEachIndexed { i, pt ->
                    if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
            }
            drawPath(
                path = linePath,
                color = primaryColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            val lastPt = coords.last()
            drawCircle(primaryColor, radius = 4.dp.toPx(), center = lastPt)

            if (lowestIndex in coords.indices) {
                val lowPt = coords[lowestIndex]
                drawCircle(greenColor, radius = 5.dp.toPx(), center = lowPt)
            }

            touchFraction?.let { frac ->
                val idx = (frac * (coords.size - 1)).roundToInt().coerceIn(0, coords.size - 1)
                val touchPt = coords[idx]
                val ptData = points[idx]

                drawLine(
                    color = primaryColor.copy(alpha = 0.6f),
                    start = Offset(touchPt.x, topPad),
                    end = Offset(touchPt.x, topPad + chartH),
                    strokeWidth = 1.dp.toPx(),
                )

                drawCircle(primaryColor, radius = 6.dp.toPx(), center = touchPt)
                drawCircle(Color.White, radius = 3.dp.toPx(), center = touchPt)

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
                    color = Color(0xFF171716),
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
// REUSABLE ASYNC IMAGE
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
            .crossfade(false)
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
private fun ThemeMenuButton(vm: MainVm, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val currentMode by vm.themeMode.collectAsState()

    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_theme),
                contentDescription = "Theme mode",
                modifier = Modifier.size(16.dp),
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
// REDESIGNED ACTION SHEETS & DIALOGS
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductOptionsBottomSheet(
    product: Product,
    onDismiss: () -> Unit,
    onOpenNote: () -> Unit,
    onPriceHistory: () -> Unit,
    onCopyLink: () -> Unit,
    onChangeCurrencyPrice: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                ProductAsyncImage(
                    imageUrl = product.imageUrl,
                    pageUrl = product.url,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        product.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        product.domain.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(Modifier.height(8.dp))

            ListItem(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onDismiss(); onOpenNote() },
                leadingContent = { Icon(painterResource(R.drawable.ic_note), null, tint = MaterialTheme.colorScheme.onSurface) },
                headlineContent = { Text(if (product.note.isNullOrBlank()) "Add note" else "Edit note") },
            )
            ListItem(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onDismiss(); onPriceHistory() },
                leadingContent = { Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.onSurface) },
                headlineContent = { Text("Price history") },
            )
            ListItem(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onDismiss(); onCopyLink() },
                leadingContent = { Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.onSurface) },
                headlineContent = { Text("Copy link") },
            )
            ListItem(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onDismiss(); onChangeCurrencyPrice() },
                leadingContent = { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurface) },
                headlineContent = { Text("Change currency / edit price") },
            )
            ListItem(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onDismiss(); onDelete() },
                leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBottomSheet(
    activeOption: FolderSortOption,
    onDismiss: () -> Unit,
    onSelect: (FolderSortOption) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("SORT PRODUCTS BY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            FolderSortOption.entries.forEach { option ->
                val sel = option == activeOption
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(option); onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = sel,
                        onClick = null,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VisitProductDialog(
    product: Product,
    onDismiss: () -> Unit,
    onVisit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Visit store?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductAsyncImage(
                        imageUrl = product.imageUrl,
                        pageUrl = product.url,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(product.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(product.domain.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            PillButton(text = "Visit store", onClick = onVisit, isPrimary = true)
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
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
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(if (initialNote.isBlank()) "Add note" else "Edit note", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(productTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    minLines = 4,
                    maxLines = 8,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            if (note.isEmpty()) {
                                Text("Size, color, reminders…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                            innerTextField()
                        }
                    },
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
            }
        },
        confirmButton = {
            PillButton(text = "Save", onClick = { onSave(note.trim().ifBlank { null }) }, isPrimary = true)
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (initialNote.isNotBlank() && onDeleteNote != null) {
                    TextButton(onClick = onDeleteNote, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Delete note")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

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
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Add product link", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Product URL") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            PillButton(text = "Save link", onClick = { if (isValidUrl) onSave(trimmed) }, enabled = isValidUrl, isPrimary = true)
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CurrencyWarningDialog(
    product: Product,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSwitchCurrency: () -> Unit,
    onConvertRate: (Double) -> Unit,
) {
    var rateText by remember { mutableStateOf("") }
    var showConvertInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("${product.currency ?: "Foreign currency"} detected", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This item is listed in ${product.currency ?: "USD"}. You can switch it to $defaultCurrency or convert using an exchange rate.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (showConvertInput) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text("Exchange rate") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (showConvertInput) {
                PillButton(
                    text = "Convert",
                    onClick = {
                        val rate = rateText.toDoubleOrNull()
                        if (rate != null && rate > 0) onConvertRate(rate)
                    },
                    isPrimary = true,
                )
            } else {
                PillButton(
                    text = "Switch to $defaultCurrency",
                    onClick = onSwitchCurrency,
                    isPrimary = true,
                )
            }
        },
        dismissButton = {
            Row {
                if (!showConvertInput) {
                    TextButton(onClick = { showConvertInput = true }) { Text("Convert rate") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun EditPriceAndCurrencyDialog(
    product: Product,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSave: (Double?, String) -> Unit,
) {
    var priceText by remember {
        mutableStateOf(product.price?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }
    var currencyText by remember { mutableStateOf(product.currency ?: defaultCurrency) }
    val currencies = listOf("INR", "USD", "EUR", "GBP")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Change currency / edit price", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(product.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("CURRENCY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    currencies.forEach { cur ->
                        FilterChip(
                            selected = currencyText.equals(cur, ignoreCase = true),
                            onClick = { currencyText = cur },
                            label = { Text(cur, style = MaterialTheme.typography.bodySmall) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            PillButton(
                text = "Save",
                onClick = {
                    val p = Scraper.parsePrice(priceText)
                    onSave(p, currencyText.uppercase())
                },
                isPrimary = true,
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FolderDialog(
    title: String,
    initialName: String,
    initialColor: Long,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("ACCENT COLOR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            PillButton(text = "Save", onClick = { if (name.isNotBlank()) onSave(name, selectedColor) }, isPrimary = true)
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
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Title") },
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            PillButton(text = "Save", onClick = { onSave(text) }, isPrimary = true)
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
