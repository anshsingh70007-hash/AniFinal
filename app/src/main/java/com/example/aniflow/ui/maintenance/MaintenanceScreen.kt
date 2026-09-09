package com.example.aniflow.ui.maintenance

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.DeviceType
import com.example.aniflow.ui.redesign.theme.focusGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Warm Amber & Cozy Night Aesthetic Palette
private val DeepObsidian = Color(0xFF09080F)
private val CardSurfaceDark = Color(0xFF13111F)
private val WarmAmber = Color(0xFFFF9E44)
private val WarmOrange = Color(0xFFFF6D00)
private val WarmGold = Color(0xFFFFD166)
private val WarmCoral = Color(0xFFFF5252)
private val TextWarmPrimary = Color(0xFFFFF8F0)
private val TextWarmSecondary = Color(0xFFD5D0E0)
private val TextWarmMuted = Color(0xFF8F88A0)

@Composable
fun MaintenanceScreen(
    deviceType: DeviceType,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }

    BackHandler {
        activity?.finish()
    }

    if (deviceType == DeviceType.TV) {
        MaintenanceTvScreen(
            modifier = modifier,
            onExit = { activity?.finish() }
        )
    } else {
        MaintenancePhoneScreen(
            modifier = modifier,
            onExit = { activity?.finish() }
        )
    }
}

/**
 * Mobile-optimized Portrait Maintenance Screen with rich warm glassmorphism
 */
@Composable
fun MaintenancePhoneScreen(
    modifier: Modifier = Modifier,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulseGlow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gearRotation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepObsidian),
        contentAlignment = Alignment.Center
    ) {
        // Ambient Warm Lighting Spots
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-60).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(WarmAmber.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(360.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-100).dp, y = 100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(WarmCoral.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
        )

        // Center Content Card
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                shape = RoundedCornerShape(24.dp),
                color = CardSurfaceDark.copy(alpha = 0.96f),
                tonalElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(WarmAmber.copy(alpha = 0.45f), WarmOrange.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Animated Warm Mascot Emblem
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .scale(pulseScale),
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer Halo
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(WarmAmber.copy(alpha = 0.35f), Color.Transparent)
                                    ),
                                    shape = CircleShape
                                )
                        )
                        // Inner Circle
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(WarmOrange.copy(alpha = 0.25f), WarmAmber.copy(alpha = 0.15f))
                                    )
                                )
                                .border(1.5.dp, WarmAmber.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = WarmGold,
                                modifier = Modifier
                                    .size(32.dp)
                                    .rotate(rotation)
                            )
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = WarmCoral,
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.BottomEnd)
                                    .offset(x = (-4).dp, y = (-4).dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Status Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(WarmOrange.copy(alpha = 0.15f))
                            .border(1.dp, WarmAmber.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(WarmAmber, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SYSTEM MAINTENANCE",
                            color = WarmGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Title
                    Text(
                        text = "This app is under maintenance we will be back soon",
                        color = TextWarmPrimary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Warm Message Body
                    Text(
                        text = "We're currently fine-tuning our video player engine and streaming infrastructure to ensure seamless, stutter-free playback for you.\n\nOur team is working hard around the clock to bring everything back stronger than ever. Grab a coffee or your favorite snack — we'll be back shortly! ☕❤️",
                        color = TextWarmSecondary,
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 21.sp
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    // Status Checklist Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = DeepObsidian.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(WarmAmber, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Video Player Engine: Calibration & Tuning",
                                    color = TextWarmSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(WarmAmber, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Streaming Gateways: Optimizing Server Health",
                                    color = TextWarmSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(26.dp))

                    // Action: Check Status Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(WarmOrange, WarmAmber)
                                )
                            )
                            .clickable {
                                if (!isChecking) {
                                    isChecking = true
                                    scope.launch {
                                        delay(1200L)
                                        isChecking = false
                                        Toast.makeText(
                                            context,
                                            "Still under maintenance. Our engineers are actively working on it!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Check Status",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Exit Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .clickable { onExit() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Exit App",
                            color = TextWarmMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * TV-optimized 16:9 Landscape Maintenance Screen with D-pad Focus Locking
 */
@Composable
fun MaintenanceTvScreen(
    modifier: Modifier = Modifier,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "tvPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tvPulseScale"
    )
    val gearRotate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tvGearRotate"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepObsidian)
            .padding(horizontal = 48.dp, vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        // TV Cinematic Ambient Background
        Box(
            modifier = Modifier
                .size(450.dp)
                .align(Alignment.Center)
                .background(
                    Brush.radialGradient(
                        colors = listOf(WarmAmber.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        // TV Center Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp),
            shape = RoundedCornerShape(24.dp),
            color = CardSurfaceDark.copy(alpha = 0.98f),
            tonalElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                Brush.horizontalGradient(
                    listOf(WarmAmber.copy(alpha = 0.45f), WarmOrange.copy(alpha = 0.2f), WarmAmber.copy(alpha = 0.45f))
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: TV Emblem & Pulse
                Column(
                    modifier = Modifier.weight(0.35f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(pulseScale),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(WarmAmber.copy(alpha = 0.35f), Color.Transparent)
                                    ),
                                    CircleShape
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(WarmOrange.copy(alpha = 0.3f), WarmAmber.copy(alpha = 0.15f))
                                    )
                                )
                                .border(2.dp, WarmAmber, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = WarmGold,
                                modifier = Modifier
                                    .size(42.dp)
                                    .rotate(gearRotate)
                            )
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = WarmCoral,
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.BottomEnd)
                                    .offset(x = (-6).dp, y = (-6).dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(WarmOrange.copy(alpha = 0.15f))
                            .border(1.dp, WarmAmber.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(WarmAmber, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TV MAINTENANCE",
                            color = WarmGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(28.dp))

                // Right Column: Title, Warm Body, and TV D-pad Buttons
                Column(
                    modifier = Modifier.weight(0.65f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "This app is under maintenance we will be back soon",
                        color = TextWarmPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 32.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "We are currently upgrading the video player playback engine and high-bitrate streaming servers for your TV screen.\n\nAniFlow will be back online shortly with butter-smooth playback. Thank you for waiting! ☕📺",
                        color = TextWarmSecondary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Action Buttons Row (TV D-Pad Focusable)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Check Status Button (TV Default Focus)
                        val isStatusFocused = focusedIndex == 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .onFocusChanged { if (it.isFocused) focusedIndex = 0 }
                                .focusGlow(isStatusFocused, shape = RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isStatusFocused) WarmAmber else WarmOrange.copy(alpha = 0.35f)
                                )
                                .border(
                                    2.dp,
                                    if (isStatusFocused) Color.White else WarmAmber.copy(alpha = 0.6f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (!isChecking) {
                                        isChecking = true
                                        scope.launch {
                                            delay(1200L)
                                            isChecking = false
                                            Toast.makeText(
                                                context,
                                                "Still under maintenance. Our team is actively upgrading the TV player!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = if (isStatusFocused) DeepObsidian else Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Check Status",
                                    color = if (isStatusFocused) DeepObsidian else TextWarmPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Exit TV Button
                        val isExitFocused = focusedIndex == 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { if (it.isFocused) focusedIndex = 1 }
                                .focusGlow(isExitFocused, shape = RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isExitFocused) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f)
                                )
                                .border(
                                    1.5.dp,
                                    if (isExitFocused) WarmAmber else Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onExit() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Exit AniFlow",
                                color = if (isExitFocused) TextWarmPrimary else TextWarmMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
