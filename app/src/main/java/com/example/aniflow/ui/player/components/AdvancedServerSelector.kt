package com.example.aniflow.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.aniflow.data.model.AudioType
import com.example.aniflow.data.model.SourceEndpoint
import com.example.aniflow.theme.*
import com.example.aniflow.ui.redesign.theme.glassSurface
import com.example.aniflow.ui.redesign.theme.focusGlow

@Composable
fun AdvancedServerSelector(
    sources: List<SourceEndpoint>,
    selectedSource: SourceEndpoint?,
    onSelectServer: (String, AudioType) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isRedesign = remember { context.packageName.endsWith(".redesign") }
    val deviceType = com.example.aniflow.LocalDeviceType.current

    val options = remember(sources) {
        sources.distinctBy { Pair(it.server, it.audioType) }
    }

    val subOptions = remember(options) {
        options.filter { it.audioType == AudioType.SUB }
    }
    val dubOptions = remember(options) {
        options.filter { it.audioType == AudioType.DUB }
    }

    val focusRequesters = remember(options.size) {
        List(options.size) { FocusRequester() }
    }
    
    LaunchedEffect(Unit) {
        if (deviceType == com.example.aniflow.DeviceType.TV && focusRequesters.isNotEmpty()) {
            focusRequesters.first().requestFocus()
        }
    }

    val cardColor = if (isRedesign) {
        Color(0xFF0F0E17).copy(alpha = 0.98f)
    } else {
        SurfaceCard
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .heightIn(max = if (deviceType == com.example.aniflow.DeviceType.TV) 420.dp else 600.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = cardColor,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Server Settings",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (subOptions.isNotEmpty()) {
                        item {
                            Text(
                                text = "SUBTITLE SERVERS",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
                            )
                        }
                        itemsIndexed(subOptions) { index, source ->
                            val isSelected = selectedSource?.server == source.server && selectedSource.audioType == AudioType.SUB
                            var isFocused by remember { mutableStateOf(false) }

                            val itemModifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[index])
                                .onFocusChanged { isFocused = it.isFocused }
                                .let { 
                                    if (deviceType == com.example.aniflow.DeviceType.TV) {
                                        it.focusGlow(isFocused, shape = RoundedCornerShape(8.dp))
                                    } else {
                                        it
                                    }
                                }
                                .let {
                                    if (isRedesign) {
                                        it.glassSurface(shape = RoundedCornerShape(8.dp), isFocused = isSelected || isFocused)
                                    } else {
                                        it.clip(RoundedCornerShape(8.dp))
                                          .background(if (isSelected) PrimaryAccent else Color.Transparent)
                                    }
                                }
                                .clickable {
                                    onSelectServer(source.server.value, AudioType.SUB)
                                    onDismiss()
                                }
                                .padding(12.dp)

                            Row(
                                modifier = itemModifier,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = source.server.value,
                                    color = TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    if (dubOptions.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "DUBBED SERVERS",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
                            )
                        }
                        itemsIndexed(dubOptions) { index, source ->
                            val realIndex = subOptions.size + index
                            val isSelected = selectedSource?.server == source.server && selectedSource.audioType == AudioType.DUB
                            var isFocused by remember { mutableStateOf(false) }

                            val itemModifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[realIndex])
                                .onFocusChanged { isFocused = it.isFocused }
                                .let { 
                                    if (deviceType == com.example.aniflow.DeviceType.TV) {
                                        it.focusGlow(isFocused, shape = RoundedCornerShape(8.dp))
                                    } else {
                                        it
                                    }
                                }
                                .let {
                                    if (isRedesign) {
                                        it.glassSurface(shape = RoundedCornerShape(8.dp), isFocused = isSelected || isFocused)
                                    } else {
                                        it.clip(RoundedCornerShape(8.dp))
                                          .background(if (isSelected) PrimaryAccent else Color.Transparent)
                                    }
                                }
                                .clickable {
                                    onSelectServer(source.server.value, AudioType.DUB)
                                    onDismiss()
                                }
                                .padding(12.dp)

                            Row(
                                modifier = itemModifier,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = source.server.value,
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
}
