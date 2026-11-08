package com.example.aniflow.ui.schedule

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.ui.tv.home.TvTokens

/**
 * What a day with no episodes shows. Without [failed] an empty week used to read "Loading the week…"
 * forever after a fetch that had already given up, so a failure now says so and offers Retry.
 */
@Composable
fun ScheduleEmptyState(weekLoaded: Boolean, failed: Boolean, onRetry: () -> Unit, tv: Boolean) {
    val body = if (tv) 16.sp else 15.sp
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        when {
            weekLoaded -> Text("Nothing scheduled. A quiet day.", color = TvTokens.TextTertiary, fontSize = body)
            !failed -> Text("Loading the week…", color = TvTokens.TextTertiary, fontSize = body)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Couldn't load the schedule", color = TvTokens.TextPrimary, fontSize = if (tv) 20.sp else 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Check your connection. It'll try again by itself once you're back online.",
                    color = TvTokens.TextTertiary, fontSize = body, textAlign = TextAlign.Center
                )
                RetryPill(onRetry, tv)
            }
        }
    }
}

@Composable
private fun RetryPill(onRetry: () -> Unit, tv: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, TvTokens.FocusSpring, label = "retry")
    val shape = RoundedCornerShape(50)
    Text(
        "Retry",
        color = Color.White, fontSize = if (tv) 16.sp else 15.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(top = 8.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(TvTokens.AccentGradient)
            .border(2.dp, if (focused) TvTokens.AccentPale else Color.Transparent, shape)
            .clickable(interaction, indication = null, onClick = onRetry)
            .padding(horizontal = 28.dp, vertical = 10.dp)
    )
}
