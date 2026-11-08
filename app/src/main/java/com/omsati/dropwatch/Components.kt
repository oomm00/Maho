package com.omsati.dropwatch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// -------------------------------------------------------------
// PILL BUTTON
// -------------------------------------------------------------
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = true,
    enabled: Boolean = true,
) {
    val containerColor = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val border = if (!isPrimary) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = modifier.height(48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// -------------------------------------------------------------
// IMAGE-LED FOLDER CARD
// -------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCard(
    summary: FolderSummary,
    productImages: List<String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val folderColor = Color(summary.color.toInt())

    if (summary.count == 0) {
        // Dashed outline card for empty folders
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(24.dp))
                .drawWithContent {
                    drawContent()
                    val stroke = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                    )
                    drawRoundRect(
                        color = Color(0xFF8F8B83).copy(alpha = 0.4f),
                        style = stroke,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                    )
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    summary.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Add first link",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        return
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
    ) {
        Box(Modifier.fillMaxSize()) {
            // Background Image Collage (1 or 2x2 grid)
            val images = productImages.take(4)
            when {
                images.size >= 4 -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            ProductAsyncImage(images[0], null, Modifier.weight(1f).fillMaxHeight())
                            Spacer(Modifier.width(1.dp))
                            ProductAsyncImage(images[1], null, Modifier.weight(1f).fillMaxHeight())
                        }
                        Spacer(Modifier.height(1.dp))
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            ProductAsyncImage(images[2], null, Modifier.weight(1f).fillMaxHeight())
                            Spacer(Modifier.width(1.dp))
                            ProductAsyncImage(images[3], null, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
                images.isNotEmpty() -> {
                    ProductAsyncImage(
                        imageUrl = images.first(),
                        pageUrl = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                else -> {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }

            // Dark Scrim overlay
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.85f),
                            ),
                            startY = 60f,
                        )
                    )
            )

            // Top-left folder accent indicator line
            Box(
                Modifier
                    .padding(14.dp)
                    .size(width = 24.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(folderColor)
            )

            // Bottom Info Overlay
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    summary.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${summary.count} ${if (summary.count == 1) "item" else "items"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Text("·", color = Color.White.copy(alpha = 0.5f))
                    Text(
                        summary.totalValue,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// PRODUCT CARD (GRID VIEW - EDITORIAL 4:5 ASPECT RATIO)
// -------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductCardGrid(
    p: Product,
    defaultCurrency: String,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    greenColor: Color,
    isReorderEnabled: Boolean = false,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onWarningChipClick: () -> Unit,
    onSetPrice: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "pressScale",
    )

    val dropped = p.price != null && p.previousPrice != null && p.price < p.previousPrice
    val pctOff = if (dropped && p.previousPrice != null && p.price != null && p.previousPrice > 0) {
        (((p.previousPrice - p.price) / p.previousPrice) * 100).roundToInt()
    } else 0

    val isForeignCurrency = p.currency != null && !p.currency.equals(defaultCurrency, ignoreCase = true)

    val border = when {
        isSelectionMode && isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 12.dp else 0.dp),
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Column {
            // 4:5 Aspect Ratio Image Container
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                ProductAsyncImage(
                    imageUrl = p.imageUrl,
                    pageUrl = p.url,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                // Top-left badging area
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (dropped) {
                        Surface(
                            color = greenColor,
                            shape = CircleShape,
                        ) {
                            Text(
                                "▼ $pctOff%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }

                    if (!p.note.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Text(
                                "NOTE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }

                    if (isForeignCurrency) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = CircleShape,
                            modifier = Modifier.clickable { onWarningChipClick() },
                        ) {
                            Text(
                                "${p.currency}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                // Selection checkmark
                if (isSelectionMode) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Drag handle "≡" (Reorder mode)
                if (!isSelectionMode && isReorderEnabled) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {}, onLongPress = {})
                            }
                            .then(dragHandleModifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_drag_handle),
                            contentDescription = "Drag handle",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // Quiet "⋮" Options button
                if (!isSelectionMode) {
                    IconButton(
                        onClick = onOptionsClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Card Text Details
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = p.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (p.status == "no_price" || p.price == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (p.status == "fetching") "FETCHING…" else "NO PRICE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "SET",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onSetPrice() },
                        )
                    }
                } else {
                    Text(
                        text = money(p.price, p.currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (dropped) greenColor else MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Footer row: Store name UPPERCASE left + status dot right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = p.domain.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    // Status Dot
                    val dotColor = when (p.status) {
                        "ok" -> greenColor
                        "fetching" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    }
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// PRODUCT CARD (LIST VIEW)
// -------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductCardList(
    p: Product,
    defaultCurrency: String,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    greenColor: Color,
    isReorderEnabled: Boolean = false,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onWarningChipClick: () -> Unit,
    onSetPrice: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val dropped = p.price != null && p.previousPrice != null && p.price < p.previousPrice

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drag Handle
            if (!isSelectionMode && isReorderEnabled) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {}, onLongPress = {})
                        }
                        .then(dragHandleModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_drag_handle),
                        contentDescription = "Drag handle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
            }

            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.padding(end = 8.dp))
            }

            // Thumbnail
            ProductAsyncImage(
                imageUrl = p.imageUrl,
                pageUrl = p.url,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            )

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = p.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = money(p.price, p.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (dropped) greenColor else MaterialTheme.colorScheme.onSurface,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = p.domain.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val dotColor = when (p.status) {
                        "ok" -> greenColor
                        "fetching" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    }
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            if (!isSelectionMode) {
                IconButton(onClick = onOptionsClick) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SEGMENTED TEXT CONTROL
// -------------------------------------------------------------
@Composable
fun SegmentedTextControl(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSelected = option == selectedOption
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onOptionSelected(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(if (isSelected) 16.dp else 0.dp)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                )
            }
        }
    }
}
