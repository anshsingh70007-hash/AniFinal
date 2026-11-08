package com.example.aniflow.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.aniflow.ui.tv.home.TvTokens

/** The app's switch: a violet pill whose thumb springs across. Pure visual; the row handles input. */
@Composable
fun AniSwitch(checked: Boolean, modifier: Modifier = Modifier, width: Dp = 52.dp, height: Dp = 30.dp) {
    val pad = 3.dp
    val thumb = height - pad * 2
    val x by animateDpAsState(
        if (checked) width - thumb - pad * 2 else 0.dp,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "thumb"
    )
    val track by animateColorAsState(if (checked) TvTokens.Accent else Color(0xFF3A3150), label = "track")
    Box(
        modifier
            .size(width, height)
            .clip(RoundedCornerShape(50))
            .background(
                if (checked) Brush.horizontalGradient(listOf(TvTokens.AccentDeep, track, TvTokens.AccentGlow))
                else Brush.horizontalGradient(listOf(track, track))
            )
            .padding(pad),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(thumb)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
