package com.example.aniflow.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.aniflow.DeviceType
import com.example.aniflow.LocalDeviceType
import com.example.aniflow.data.model.AudioType
import com.example.aniflow.data.model.SourceEndpoint
import com.example.aniflow.data.model.ProviderId
import com.example.aniflow.data.model.ProviderStatus
import com.example.aniflow.theme.*
import com.example.aniflow.ui.redesign.theme.glassSurface
import com.example.aniflow.ui.redesign.theme.focusGlow

@Composable
fun AdvancedServerProviderSelector(
    providerStatuses: Map<ProviderId, ProviderStatus>,
    sources: List<SourceEndpoint>,
    selectedSource: SourceEndpoint?,
    onSelectProvider: (ProviderId) -> Unit,
    onSelectServer: (String, AudioType) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isRedesign = remember { context.packageName.endsWith(".redesign") }
    val deviceType = LocalDeviceType.current

    // Display only registered and enabled/configured providers (hide unconfigured entirely)
    val visibleProviders = remember(providerStatuses) {
        ProviderId.entries.filter { providerStatuses[it] != ProviderStatus.Unconfigured }
    }

    // Identify the active provider (which one is selected or currently playing/resolving)
    val activeProvider = remember(providerStatuses, selectedSource) {
        providerStatuses.entries.firstOrNull { it.value == ProviderStatus.Selected || it.value == ProviderStatus.Loading }?.key
            ?: selectedSource?.provider
            ?: ProviderId.ANILIGHT
    }

    // Keep track of the focused provider in the TV layout
    var tvFocusedProvider by remember { mutableStateOf(activeProvider) }

    val activeOrFocusedProvider = if (deviceType == DeviceType.TV) tvFocusedProvider else activeProvider
    val currentStatus = providerStatuses[activeOrFocusedProvider] ?: ProviderStatus.Available

    // Filter sources belonging to the currently selected or focused provider
    val providerSources = remember(sources, activeOrFocusedProvider) {
        sources.filter { it.provider == activeOrFocusedProvider }
    }

    val subOptions = remember(providerSources) {
        providerSources.filter { it.audioType == AudioType.SUB }.distinctBy { it.server }
    }
    val dubOptions = remember(providerSources) {
        providerSources.filter { it.audioType == AudioType.DUB }.distinctBy { it.server }
    }

    val totalOptionsCount = subOptions.size + dubOptions.size
    val focusRequesters = remember(visibleProviders.size, totalOptionsCount) {
        Pair(
            List(visibleProviders.size) { FocusRequester() },
            List(totalOptionsCount) { FocusRequester() }
        )
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
                .widthIn(max = if (deviceType == DeviceType.TV) 640.dp else 360.dp)
                .heightIn(max = if (deviceType == DeviceType.TV) 400.dp else 600.dp)
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
                    text = "Source & Provider settings",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                if (deviceType == DeviceType.TV) {
                    // Split-pane layout for Android TV
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left Column: Providers List
                        Column(
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "PROVIDERS",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            visibleProviders.forEachIndexed { idx, provider ->
                                val status = providerStatuses[provider] ?: ProviderStatus.Available
                                val isSelected = provider == activeProvider
                                var isFocused by remember { mutableStateOf(false) }

                                val (statusText, statusColor) = getStatusTextAndColor(status)

                                val providerModifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters.first[idx])
                                    .onFocusChanged { 
                                        isFocused = it.isFocused 
                                        if (it.isFocused) {
                                            tvFocusedProvider = provider
                                        }
                                    }
                                    .focusGlow(isFocused, shape = RoundedCornerShape(8.dp))
                                    .let {
                                        if (isRedesign) {
                                            it.glassSurface(shape = RoundedCornerShape(8.dp), isFocused = isSelected || isFocused)
                                        } else {
                                            it.clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) PrimaryAccent else Color.Transparent)
                                        }
                                    }
                                    .clickable(enabled = status != ProviderStatus.CircuitOpen) {
                                        onSelectProvider(provider)
                                    }
                                    .padding(12.dp)

                                Row(
                                    modifier = providerModifier,
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = getProviderDisplayName(provider),
                                        color = if (status == ProviderStatus.CircuitOpen) TextSecondary else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    if (status == ProviderStatus.Loading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = statusColor
                                        )
                                    } else {
                                        Text(
                                            text = statusText,
                                            color = statusColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // Vertical divider
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                        )

                        // Right Column: Servers List
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            when (currentStatus) {
                                ProviderStatus.Loading -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = SecondaryAccent)
                                    }
                                }
                                ProviderStatus.CircuitOpen -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Provider is in cooldown", color = TextSecondary, fontSize = 14.sp)
                                    }
                                }
                                ProviderStatus.IdentityMismatch -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Identity mismatch", color = TextSecondary, fontSize = 14.sp)
                                    }
                                }
                                else -> {
                                    if (subOptions.isEmpty() && dubOptions.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No servers available", color = TextSecondary, fontSize = 14.sp)
                                        }
                                    } else {
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            if (subOptions.isNotEmpty()) {
                                                item {
                                                    Text(
                                                        text = "SUBTITLE SERVERS",
                                                        color = TextSecondary,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(bottom = 4.dp)
                                                    )
                                                }
                                                itemsIndexed(subOptions) { index, source ->
                                                    val isSelected = selectedSource?.server == source.server && selectedSource.audioType == AudioType.SUB
                                                    var isFocused by remember { mutableStateOf(false) }

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .focusRequester(focusRequesters.second[index])
                                                            .onFocusChanged { isFocused = it.isFocused }
                                                            .focusGlow(isFocused, shape = RoundedCornerShape(8.dp))
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
                                                            .padding(10.dp),
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
                                                        modifier = Modifier.padding(bottom = 4.dp)
                                                    )
                                                }
                                                itemsIndexed(dubOptions) { index, source ->
                                                    val realIndex = subOptions.size + index
                                                    val isSelected = selectedSource?.server == source.server && selectedSource.audioType == AudioType.DUB
                                                    var isFocused by remember { mutableStateOf(false) }

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .focusRequester(focusRequesters.second[realIndex])
                                                            .onFocusChanged { isFocused = it.isFocused }
                                                            .focusGlow(isFocused, shape = RoundedCornerShape(8.dp))
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
                                                            .padding(10.dp),
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
                    }
                } else {
                    // Mobile vertical layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Providers Section
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "PROVIDERS",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            visibleProviders.forEach { provider ->
                                val status = providerStatuses[provider] ?: ProviderStatus.Available
                                val isSelected = provider == activeProvider
                                val (statusText, statusColor) = getStatusTextAndColor(status)

                                val providerModifier = Modifier
                                    .fillMaxWidth()
                                    .let {
                                        if (isRedesign) {
                                            it.glassSurface(shape = RoundedCornerShape(8.dp), isFocused = isSelected)
                                        } else {
                                            it.clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) PrimaryAccent else SurfaceCard)
                                        }
                                    }
                                    .clickable(enabled = status != ProviderStatus.CircuitOpen) {
                                        onSelectProvider(provider)
                                    }
                                    .padding(12.dp)

                                Row(
                                    modifier = providerModifier,
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = getProviderDisplayName(provider),
                                        color = if (status == ProviderStatus.CircuitOpen) TextSecondary else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    if (status == ProviderStatus.Loading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = statusColor
                                        )
                                    } else {
                                        Text(
                                            text = statusText,
                                            color = statusColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                        )

                        // Servers Section
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "SERVERS",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            when (currentStatus) {
                                ProviderStatus.Loading -> {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(80.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = SecondaryAccent)
                                    }
                                }
                                ProviderStatus.CircuitOpen -> {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(80.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Provider is in cooldown", color = TextSecondary, fontSize = 14.sp)
                                    }
                                }
                                ProviderStatus.IdentityMismatch -> {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(80.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Identity mismatch", color = TextSecondary, fontSize = 14.sp)
                                    }
                                }
                                else -> {
                                    if (subOptions.isEmpty() && dubOptions.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height(80.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No servers available", color = TextSecondary, fontSize = 14.sp)
                                        }
                                    } else {
                                        if (subOptions.isNotEmpty()) {
                                            Text(
                                                text = "SUBTITLE SERVERS",
                                                color = TextSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            subOptions.forEach { source ->
                                                val isSelected = selectedSource?.server == source.server && selectedSource.audioType == AudioType.SUB
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .let {
                                                            if (isRedesign) {
                                                                it.glassSurface(shape = RoundedCornerShape(8.dp), isFocused = isSelected)
                                                            } else {
                                                                it.clip(RoundedCornerShape(8.dp))
                                                                    .background(if (isSelected) PrimaryAccent else SurfaceCard)
                                                            }
                                                        }
                                                        .clickable {
                                                            onSelectServer(source.server.value, AudioType.SUB)
                                                            onDismiss()
                                                        }
                                                        .padding(12.dp),
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
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = "DUBBED SERVERS",
                                                color = TextSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            dubOptions.forEach { source ->
                                                val isSelected = selectedSource?.server == source.server && selectedSource.audioType == AudioType.DUB
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .let {
                                                            if (isRedesign) {
                                                                it.glassSurface(shape = RoundedCornerShape(8.dp), isFocused = isSelected)
                                                            } else {
                                                                it.clip(RoundedCornerShape(8.dp))
                                                                    .background(if (isSelected) PrimaryAccent else SurfaceCard)
                                                            }
                                                        }
                                                        .clickable {
                                                            onSelectServer(source.server.value, AudioType.DUB)
                                                            onDismiss()
                                                        }
                                                        .padding(12.dp),
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
                }
            }
        }
    }
}

private fun getProviderDisplayName(provider: ProviderId): String {
    return when (provider) {
        ProviderId.ANILIGHT -> "AniLight"
        ProviderId.MIRURO -> "Miruro"
        ProviderId.ANIKOTO -> "Anikoto"
    }
}

private fun getStatusTextAndColor(status: ProviderStatus): Pair<String, Color> {
    return when (status) {
        ProviderStatus.Selected -> Pair("Selected", Color(0xFF4CAF50)) // Green
        ProviderStatus.Loading -> Pair("Loading...", Color(0xFFFFC107)) // Amber
        ProviderStatus.Available -> Pair("Available", Color(0xFF2196F3)) // Blue
        ProviderStatus.CircuitOpen -> Pair("Cooldown", Color(0xFFF44336)) // Red
        ProviderStatus.Unconfigured -> Pair("Unavailable", Color(0xFF9E9E9E)) // Grey
        ProviderStatus.IdentityMismatch -> Pair("Mismatch", Color(0xFFFF5722)) // Orange
    }
}
