package com.example.aniflow.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.aniflow.data.model.StreamingSource
import com.example.aniflow.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import com.example.aniflow.ui.redesign.theme.glassSurface
import com.example.aniflow.ui.redesign.theme.focusGlow
import com.example.aniflow.ui.redesign.theme.GlassTokens

@Composable
fun QualitySelector(
    sources: List<StreamingSource>,
    selectedSource: StreamingSource?,
    onSelect: (StreamingSource) -> Unit,
    selectedVideoQuality: String,
    onSelectVideoQuality: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isRedesign = remember { context.packageName.endsWith(".redesign") }
    val deviceType = com.example.aniflow.LocalDeviceType.current

    val uniqueSources = remember(sources) {
        sources.distinctBy { it.url }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = if (isRedesign) {
                Modifier
                    .width(320.dp)
                    .wrapContentHeight()
                    .glassSurface(shape = RoundedCornerShape(16.dp))
            } else {
                Modifier
                    .width(320.dp)
                    .wrapContentHeight()
            },
            shape = RoundedCornerShape(16.dp),
            color = if (isRedesign) Color.Transparent else SurfaceCard,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Player Options",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                // Section 1: Server
                Text(
                    text = "SELECT SERVER",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 180.dp)
                ) {
                    items(uniqueSources) { source ->
                        val isSelected = source.quality == selectedSource?.quality
                        var isFocused by remember { mutableStateOf(false) }

                        val itemModifier = if (isRedesign) {
                            Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused }
                                .let { 
                                    if (deviceType == com.example.aniflow.DeviceType.TV) {
                                        it.focusGlow(isFocused, shape = RoundedCornerShape(8.dp))
                                    } else {
                                        it
                                    }
                                }
                                .glassSurface(
                                    shape = RoundedCornerShape(8.dp), 
                                    isFocused = isSelected || isFocused
                                )
                                .clickable {
                                    onSelect(source)
                                }
                                .padding(12.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) PrimaryAccent else Color.Transparent)
                                .clickable {
                                    onSelect(source)
                                }
                                .padding(12.dp)
                        }

                        Row(
                            modifier = itemModifier,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = source.quality,
                                color = TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Section 2: Quality Lock
                Text(
                    text = "RESOLUTION LIMIT",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(8.dp))

                val qualityOptions = listOf("Auto", "1080p", "720p", "480p", "360p")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    qualityOptions.forEach { option ->
                        val isSelected = selectedVideoQuality == option
                        var isFocused by remember { mutableStateOf(false) }

                        val buttonModifier = if (isRedesign) {
                            Modifier
                                .weight(1f)
                                .height(38.dp)
                                .onFocusChanged { isFocused = it.isFocused }
                                .let { 
                                    if (deviceType == com.example.aniflow.DeviceType.TV) {
                                        it.focusGlow(isFocused, shape = RoundedCornerShape(8.dp))
                                    } else {
                                        it
                                    }
                                }
                                .glassSurface(
                                    shape = RoundedCornerShape(8.dp), 
                                    isFocused = isSelected || isFocused
                                )
                                .clickable {
                                    onSelectVideoQuality(option)
                                }
                        } else {
                            Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) PrimaryAccent else Color.Transparent)
                                .border(1.dp, if (isSelected) Color.Transparent else SurfaceBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    onSelectVideoQuality(option)
                                }
                        }

                        Box(
                            modifier = buttonModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
