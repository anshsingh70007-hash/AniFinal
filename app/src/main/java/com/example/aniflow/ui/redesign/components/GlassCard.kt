package com.example.aniflow.ui.redesign.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.aniflow.ui.redesign.theme.focusGlow
import com.example.aniflow.ui.redesign.theme.glassSurface

@Composable
fun GlassCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val deviceType = com.example.aniflow.LocalDeviceType.current
    var isFocused by remember { mutableStateOf(false) }

    val baseModifier = if (deviceType == com.example.aniflow.DeviceType.TV) {
        modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusGlow(isFocused, shape)
            .glassSurface(shape, borderWidth, isFocused)
    } else {
        modifier
            .glassSurface(shape, borderWidth, false)
    }

    Box(
        modifier = baseModifier
            .run {
                if (onClick != null) {
                    clickable(onClick = onClick)
                } else {
                    this
                }
            },
        contentAlignment = androidx.compose.ui.Alignment.Center,
        content = content
    )
}
