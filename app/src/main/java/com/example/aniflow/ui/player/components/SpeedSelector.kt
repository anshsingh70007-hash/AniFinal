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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.example.aniflow.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import com.example.aniflow.ui.redesign.theme.glassSurface
import com.example.aniflow.ui.redesign.theme.focusGlow
import com.example.aniflow.ui.redesign.theme.GlassTokens
import kotlinx.coroutines.delay

@Composable
fun SpeedSelector(
    speeds: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
    selectedSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isRedesign = remember { context.packageName.endsWith(".redesign") }
    val deviceType = com.example.aniflow.LocalDeviceType.current

    val focusRequesters = remember(speeds.size) { List(speeds.size) { FocusRequester() } }
    val selectedIndex = remember(speeds, selectedSpeed) {
        val idx = speeds.indexOf(selectedSpeed)
        if (idx >= 0) idx else 0
    }

    LaunchedEffect(Unit) {
        if (deviceType == com.example.aniflow.DeviceType.TV && focusRequesters.isNotEmpty()) {
            delay(100)
            try {
                focusRequesters[selectedIndex].requestFocus()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    val cardColor = if (isRedesign) {
        Color(0xFF0F0E17).copy(alpha = 0.98f)
    } else {
        SurfaceCard
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(280.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = cardColor,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Playback speed",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Applies until you leave the player",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 240.dp)
                ) {
                    itemsIndexed(speeds) { idx, speed ->
                        val isSelected = speed == selectedSpeed
                        var isFocused by remember { mutableStateOf(false) }

                        val itemModifier = if (isRedesign) {
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[idx])
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
                                    onSelect(speed)
                                    onDismiss()
                                }
                                .padding(12.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[idx])
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) PrimaryAccent else Color.Transparent)
                                .clickable {
                                    onSelect(speed)
                                    onDismiss()
                                }
                                .padding(12.dp)
                        }

                        Row(
                            modifier = itemModifier,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${speed}x",
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

