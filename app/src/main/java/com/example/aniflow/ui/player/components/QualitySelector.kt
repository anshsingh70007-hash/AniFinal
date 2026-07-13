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
import com.example.aniflow.data.model.QualityPolicy
import com.example.aniflow.theme.*
import com.example.aniflow.ui.redesign.theme.glassSurface
import com.example.aniflow.ui.redesign.theme.focusGlow

@Composable
fun QualitySelector(
    availableHeights: List<Int>,
    selectedQualityPolicy: QualityPolicy,
    onSelectQuality: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isRedesign = remember { context.packageName.endsWith(".redesign") }
    val deviceType = com.example.aniflow.LocalDeviceType.current

    val options = remember(availableHeights) {
        val list = mutableListOf<QualityOption>()
        list.add(QualityOption("Auto", "auto", QualityPolicy.Auto))
        list.add(QualityOption("Best available", "max", QualityPolicy.MaxAvailable))
        availableHeights.forEach { height ->
            list.add(QualityOption("${height}p", "${height}p", QualityPolicy.FixedHeight(height)))
        }
        list
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
                .heightIn(max = if (deviceType == com.example.aniflow.DeviceType.TV) 320.dp else 450.dp)
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
                    text = "Video Quality",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(options) { index, option ->
                        val isSelected = when (option.policy) {
                            is QualityPolicy.Auto -> selectedQualityPolicy is QualityPolicy.Auto
                            is QualityPolicy.MaxAvailable -> selectedQualityPolicy is QualityPolicy.MaxAvailable
                            is QualityPolicy.FixedHeight -> {
                                selectedQualityPolicy is QualityPolicy.FixedHeight &&
                                    selectedQualityPolicy.height == option.policy.height
                            }
                        }
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
                                onSelectQuality(option.prefValue)
                                onDismiss()
                            }
                            .padding(12.dp)

                        Row(
                            modifier = itemModifier,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option.label,
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

private data class QualityOption(
    val label: String,
    val prefValue: String,
    val policy: QualityPolicy
)
