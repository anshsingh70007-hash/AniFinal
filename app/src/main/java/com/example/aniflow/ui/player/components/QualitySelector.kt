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
        sources.distinctBy { it.quality }
    }

    val subSources = remember(uniqueSources) {
        uniqueSources.filter { it.quality.contains("(SUB)", ignoreCase = true) }
    }
    val dubSources = remember(uniqueSources) {
        uniqueSources.filter { it.quality.contains("(DUB)", ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = if (isRedesign) {
                Modifier
                    .widthIn(max = 360.dp)
                    .heightIn(max = if (deviceType == com.example.aniflow.DeviceType.TV) 420.dp else 600.dp)
                    .glassSurface(shape = RoundedCornerShape(16.dp))
            } else {
                Modifier
                    .widthIn(max = 360.dp)
                    .heightIn(max = if (deviceType == com.example.aniflow.DeviceType.TV) 420.dp else 600.dp)
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

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 240.dp)
                ) {
                    if (subSources.isNotEmpty()) {
                        item {
                            Text(
                                text = "SUBTITLE",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
                            )
                        }
                        items(subSources) { source ->
                            val cleanLabel = source.quality.replace(" (SUB)", "", ignoreCase = true).replace(" (DUB)", "", ignoreCase = true)
                            val isSelected = source.url == selectedSource?.url
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
                                        onDismiss()
                                    }
                                    .padding(12.dp)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PrimaryAccent else Color.Transparent)
                                    .clickable {
                                        onSelect(source)
                                        onDismiss()
                                    }
                                    .padding(12.dp)
                            }

                            Row(
                                modifier = itemModifier,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cleanLabel,
                                    color = TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    if (dubSources.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "DUBBED",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
                            )
                        }
                        items(dubSources) { source ->
                            val cleanLabel = source.quality.replace(" (SUB)", "", ignoreCase = true).replace(" (DUB)", "", ignoreCase = true)
                            val isSelected = source.url == selectedSource?.url
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
                                        onDismiss()
                                    }
                                    .padding(12.dp)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PrimaryAccent else Color.Transparent)
                                    .clickable {
                                        onSelect(source)
                                        onDismiss()
                                    }
                                    .padding(12.dp)
                            }

                            Row(
                                modifier = itemModifier,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cleanLabel,
                                    color = TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Section 2: Resolution — switches the actual stream URL, not ExoPlayer track limits
                Text(
                    text = "RESOLUTION",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(8.dp))

                val qualityOptions = listOf("auto", "1080p", "720p", "480p", "360p")
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
                                    onDismiss()
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
                                    onDismiss()
                                }
                        }

                        Box(
                            modifier = buttonModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
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
