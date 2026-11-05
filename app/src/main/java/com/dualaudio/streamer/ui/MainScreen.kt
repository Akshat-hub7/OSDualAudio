package com.dualaudio.streamer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualaudio.streamer.data.RoutingEngineMode
import com.dualaudio.streamer.data.RoutingServiceState
import com.dualaudio.streamer.ui.components.AudioVisualizer
import com.dualaudio.streamer.ui.components.DeviceCard
import com.dualaudio.streamer.ui.components.HeadsetSlotCard
import com.dualaudio.streamer.ui.theme.AccentPrimary
import com.dualaudio.streamer.ui.theme.AccentSecondary
import com.dualaudio.streamer.ui.theme.DarkBackground
import com.dualaudio.streamer.ui.theme.StatusAmber
import com.dualaudio.streamer.ui.theme.StatusGreen
import com.dualaudio.streamer.ui.theme.StatusRed
import com.dualaudio.streamer.ui.theme.SurfaceBorder
import com.dualaudio.streamer.ui.theme.SurfaceDark
import com.dualaudio.streamer.ui.theme.SurfaceElevated
import com.dualaudio.streamer.ui.theme.TextMuted
import com.dualaudio.streamer.ui.theme.TextPrimary
import com.dualaudio.streamer.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: DualAudioViewModel,
    onRequestPermissions: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isStreaming = uiState.serviceState == RoutingServiceState.RUNNING_AUDIOTRACK ||
                      uiState.serviceState == RoutingServiceState.RUNNING_AURACAST

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(AccentPrimary, AccentSecondary))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "DualCast Audio",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshBluetoothDevices() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Devices",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        bottomBar = {
            // Master Action Floating Bottom Bar
            Surface(
                color = SurfaceDark,
                tonalElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            if (isStreaming) {
                                viewModel.onStopDualAudioClicked()
                            } else {
                                if (!uiState.hasRequiredPermissions) {
                                    onRequestPermissions()
                                } else {
                                    viewModel.onStartDualAudioClicked()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStreaming) StatusRed else AccentPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isStreaming) "STOP DUAL STREAM" else "START DUAL STREAMING",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Warning Banner
            if (!uiState.hasRequiredPermissions) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusAmber.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Permissions Required",
                                tint = StatusAmber
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Permissions Required",
                                    fontWeight = FontWeight.Bold,
                                    color = StatusAmber,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Bluetooth and Audio capture permissions are needed to stream dual audio.",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = onRequestPermissions,
                                colors = ButtonDefaults.buttonColors(containerColor = StatusAmber)
                            ) {
                                Text("Grant", color = DarkBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Mode Selector: Auracast vs Dual AudioTrack Fallback
            item {
                Column {
                    Text(
                        text = "ROUTING ENGINE MODE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = uiState.routingMode == RoutingEngineMode.AURACAST_LE_AUDIO,
                            onClick = { viewModel.setRoutingMode(RoutingEngineMode.AURACAST_LE_AUDIO) },
                            label = {
                                Text("Auracast (LE Audio)", fontSize = 12.sp)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Cast, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentPrimary.copy(alpha = 0.3f),
                                selectedLabelColor = AccentPrimary,
                                containerColor = SurfaceElevated
                            )
                        )

                        FilterChip(
                            selected = uiState.routingMode == RoutingEngineMode.DUAL_AUDIOTRACK_FALLBACK,
                            onClick = { viewModel.setRoutingMode(RoutingEngineMode.DUAL_AUDIOTRACK_FALLBACK) },
                            label = {
                                Text("Dual PCM Engine", fontSize = 12.sp)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentSecondary.copy(alpha = 0.3f),
                                selectedLabelColor = AccentSecondary,
                                containerColor = SurfaceElevated
                            )
                        )
                    }

                    // Engine Mode Context Subtitle
                    Text(
                        text = if (uiState.routingMode == RoutingEngineMode.AURACAST_LE_AUDIO) {
                            if (uiState.isAuracastSupported)
                                "⚡ Auracast Broadcast active: Broadcasts single stream to infinite LE Audio headsets."
                            else
                                "⚠️ Auracast unsupported on this chipset: Will auto-fallback to Dual PCM Engine."
                        } else {
                            "🔄 Captures system audio via MediaProjection and splits PCM into dual parallel AudioTracks."
                        },
                        fontSize = 11.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }

            // Real-time Audio Spectrum Visualizer
            item {
                AudioVisualizer(
                    amplitude = uiState.liveAmplitude,
                    isStreaming = isStreaming
                )
            }

            // Headset 1 (Slot A)
            item {
                HeadsetSlotCard(
                    slot = uiState.headsetSlot1,
                    availableDevices = uiState.pairedDevices,
                    isStreaming = isStreaming,
                    onDeviceSelected = { viewModel.selectDeviceSlot1(it) },
                    onVolumeChanged = { viewModel.updateVolumeSlot1(it) },
                    onMuteToggled = { viewModel.toggleMuteSlot1(it) },
                    onDelayChanged = { viewModel.updateDelaySlot1(it) }
                )
            }

            // Headset 2 (Slot B)
            item {
                HeadsetSlotCard(
                    slot = uiState.headsetSlot2,
                    availableDevices = uiState.pairedDevices,
                    isStreaming = isStreaming,
                    onDeviceSelected = { viewModel.selectDeviceSlot2(it) },
                    onVolumeChanged = { viewModel.updateVolumeSlot2(it) },
                    onMuteToggled = { viewModel.toggleMuteSlot2(it) },
                    onDelayChanged = { viewModel.updateDelaySlot2(it) }
                )
            }

            // Paired / Available Bluetooth Devices Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AVAILABLE BLUETOOTH DEVICES (${uiState.pairedDevices.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )

                    if (!uiState.isBluetoothEnabled) {
                        Surface(
                            color = StatusRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Bluetooth is OFF",
                                color = StatusRed,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Empty state if no devices
            if (uiState.pairedDevices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Paired Devices Found",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Pair your Bluetooth earphones in Android Settings, then tap Refresh above.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Device list items
            items(uiState.pairedDevices) { device ->
                val isSlot1 = uiState.headsetSlot1.selectedDevice?.address == device.address
                val isSlot2 = uiState.headsetSlot2.selectedDevice?.address == device.address

                DeviceCard(
                    device = device,
                    isSlot1Selected = isSlot1,
                    isSlot2Selected = isSlot2,
                    onAssignSlot1 = { viewModel.selectDeviceSlot1(device) },
                    onAssignSlot2 = { viewModel.selectDeviceSlot2(device) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

