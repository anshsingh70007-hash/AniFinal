package com.example.aniflow.ui.player.components

import androidx.compose.foundation.background
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
                    .width(280.dp)
                    .wrapContentHeight()
                    .glassSurface(shape = RoundedCornerShape(16.dp))
            } else {
                Modifier
                    .width(280.dp)
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
                    text = "Select Quality",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 240.dp)
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
                                text = source.quality,
                                color = TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

