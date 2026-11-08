package com.example.aniflow.ui.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.aniflow.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import com.example.aniflow.ui.redesign.theme.glassSurface
import com.example.aniflow.ui.redesign.theme.focusGlow
import com.example.aniflow.ui.redesign.theme.GlassTokens

@Composable
fun TvSideNavRail(
    selectedIndex: Int,
    items: List<Pair<ImageVector, String>>,
    onSelect: (Int) -> Unit
) {
    val focusRequesters = remember { List(items.size) { FocusRequester() } }

    Column(
        modifier = Modifier
            .width(80.dp)
            .fillMaxHeight()
            .background(PrimaryDarker)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        items.forEachIndexed { index, pair ->
            TvNavRailItem(
                icon = pair.first,
                isSelected = selectedIndex == index,
                focusRequester = focusRequesters[index],
                onSelect = { onSelect(index) }
            )
        }
    }

    // Auto-focus the current index item on start
    LaunchedEffect(Unit) {
        if (selectedIndex in items.indices) {
            focusRequesters[selectedIndex].requestFocus()
        }
    }
}

@Composable
fun TvNavRailItem(
    icon: ImageVector,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onSelect: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(54.dp)
            .focusRequester(focusRequester)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isSelected -> PrimaryAccent
                    isFocused -> SurfaceBorder
                    else -> Color.Transparent
                }
            )
            .clickable { onSelect() }
            .onFocusChanged { isFocused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected || isFocused) TextPrimary else TextSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun TvTopNavBar(
    selectedIndex: Int,
    items: List<Pair<ImageVector, String>>,
    onSelect: (Int) -> Unit,
    // Attached to the currently selected item so the caller can pull focus back onto the nav bar —
    // used by MainScreen's BACK handling on TV.
    selectedItemFocusRequester: FocusRequester? = null
) {
    val focusRequesters = remember(items.size) { List(items.size) { FocusRequester() } }
    val context = LocalContext.current
    val isRedesign = remember { context.packageName.endsWith(".redesign") }

    Row(
        modifier = if (isRedesign) {
            Modifier
                .wrapContentWidth()
                .height(64.dp)
                .glassSurface(shape = RoundedCornerShape(32.dp), borderWidth = 1.dp, isFocused = false)
                .padding(horizontal = 16.dp)
        } else {
            Modifier
                .wrapContentWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(PrimaryDarker, RoundedCornerShape(32.dp))
                .padding(horizontal = 16.dp)
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items.forEachIndexed { index, pair ->
            TvTopNavBarItem(
                icon = pair.first,
                label = pair.second,
                isSelected = selectedIndex == index,
                focusRequester = if (index == selectedIndex && selectedItemFocusRequester != null) {
                    selectedItemFocusRequester
                } else {
                    focusRequesters[index]
                },
                isRedesign = isRedesign,
                onSelect = { onSelect(index) }
            )
        }
    }

    // Auto-focus the current index item on start
    LaunchedEffect(Unit) {
        if (selectedIndex in items.indices) {
            runCatching {
                (selectedItemFocusRequester ?: focusRequesters[selectedIndex]).requestFocus()
            }
        }
    }
}

@Composable
fun TvTopNavBarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    isRedesign: Boolean,
    onSelect: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val itemModifier = if (isRedesign) {
        Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusGlow(isFocused, shape = RoundedCornerShape(20.dp))
            .glassSurface(shape = RoundedCornerShape(20.dp), borderWidth = 1.dp, isFocused = isFocused)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onSelect() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(
                if (isSelected) {
                    Modifier.drawWithContent {
                        drawContent()
                        // Glowing accent bottom underline
                        drawLine(
                            color = GlassTokens.GlowCyan,
                            start = Offset(12.dp.toPx(), size.height),
                            end = Offset(size.width - 12.dp.toPx(), size.height),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                } else Modifier
            )
    } else {
        Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    isSelected -> PrimaryAccent
                    isFocused -> SurfaceBorder
                    else -> Color.Transparent
                }
            )
            .clickable { onSelect() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    }

    Row(
        modifier = itemModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected || isFocused) TextPrimary else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = if (isSelected || isFocused) TextPrimary else TextSecondary,
            fontSize = if (isRedesign) 16.sp else 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

