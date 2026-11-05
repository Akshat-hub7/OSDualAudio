package com.dualaudio.streamer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualaudio.streamer.data.BluetoothDeviceModel
import com.dualaudio.streamer.data.HeadsetSlot
import com.dualaudio.streamer.ui.theme.AccentPrimary
import com.dualaudio.streamer.ui.theme.AccentSecondary
import com.dualaudio.streamer.ui.theme.CardGradientEnd1
import com.dualaudio.streamer.ui.theme.CardGradientEnd2
import com.dualaudio.streamer.ui.theme.CardGradientStart1
import com.dualaudio.streamer.ui.theme.CardGradientStart2
import com.dualaudio.streamer.ui.theme.StatusGreen
import com.dualaudio.streamer.ui.theme.StatusRed
import com.dualaudio.streamer.ui.theme.SurfaceBorder
import com.dualaudio.streamer.ui.theme.SurfaceDark
import com.dualaudio.streamer.ui.theme.TextMuted
import com.dualaudio.streamer.ui.theme.TextPrimary
import com.dualaudio.streamer.ui.theme.TextSecondary

/**
 * Control card for an individual Headset Slot (Slot 1 or Slot 2).
 * Provides device selection, real-time volume slider, mute toggle, and latency offset controller.
 */
@Composable
fun HeadsetSlotCard(
    slot: HeadsetSlot,
    availableDevices: List<BluetoothDeviceModel>,
    isStreaming: Boolean,
    onDeviceSelected: (BluetoothDeviceModel?) -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onMuteToggled: (Boolean) -> Unit,
    onDelayChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeviceMenu by remember { mutableStateOf(false) }
    var showSyncControls by remember { mutableStateOf(false) }

    val isSlot1 = slot.slotNumber == 1
    val accentColor = if (isSlot1) AccentPrimary else AccentSecondary
    val cardGradient = if (isSlot1) {
        listOf(CardGradientStart1, CardGradientEnd1)
    } else {
        listOf(CardGradientStart2, CardGradientEnd2)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = if (isStreaming && slot.selectedDevice?.isConnected == true) accentColor.copy(alpha = 0.8f) else SurfaceBorder
                ),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .background(Brush.horizontalGradient(cardGradient))
                .padding(16.dp)
        ) {
            // Header: Slot Title + Connection Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "Headset Icon",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isSlot1) "HEADSET 1 (SLOT A)" else "HEADSET 2 (SLOT B)",
                            color = accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = slot.selectedDevice?.name ?: "No device assigned",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Status Badge
                val isConnected = slot.selectedDevice?.isConnected == true
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isConnected) StatusGreen.copy(alpha = 0.2f) else SurfaceBorder.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) StatusGreen else StatusRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                slot.selectedDevice == null -> "Unassigned"
                                isStreaming && isConnected -> "Streaming"
                                isConnected -> "Connected"
                                else -> "Disconnected"
                            },
                            color = if (isConnected) StatusGreen else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Device Selector Trigger
            Box {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceDark.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, SurfaceBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeviceMenu = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Target Output Device",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Text(
                                text = slot.selectedDevice?.let { "${it.name} (${it.address})" } ?: "Tap to choose connected Bluetooth device",
                                color = if (slot.selectedDevice != null) TextPrimary else accentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select",
                            tint = TextSecondary
                        )
                    }
                }

                DropdownMenu(
                    expanded = showDeviceMenu,
                    onDismissRequest = { showDeviceMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None (Clear slot)", color = StatusRed) },
                        onClick = {
                            onDeviceSelected(null)
                            showDeviceMenu = false
                        }
                    )
                    availableDevices.forEach { device ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = device.name,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${device.address} • ${if (device.isConnected) "Connected" else "Paired"}",
                                        fontSize = 11.sp,
                                        color = if (device.isConnected) StatusGreen else TextMuted
                                    )
                                }
                            },
                            onClick = {
                                onDeviceSelected(device)
                                showDeviceMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Volume Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onMuteToggled(!slot.isMuted) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (slot.isMuted || slot.volume <= 0.01f) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                        contentDescription = "Mute Toggle",
                        tint = if (slot.isMuted) StatusRed else TextPrimary
                    )
                }

                Slider(
                    value = if (slot.isMuted) 0f else slot.volume,
                    onValueChange = {
                        if (slot.isMuted) onMuteToggled(false)
                        onVolumeChanged(it)
                    },
                    valueRange = 0f..1.2f, // Allows up to +20% software boost
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = SurfaceBorder
                    )
                )

                Text(
                    text = if (slot.isMuted) "MUTED" else "${(slot.volume * 100).toInt()}%",
                    color = if (slot.isMuted) StatusRed else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(52.dp)
                )

                IconButton(
                    onClick = { showSyncControls = !showSyncControls },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Sync latency settings",
                        tint = if (showSyncControls || slot.syncDelayMs > 0) accentColor else TextSecondary
                    )
                }
            }

            // Sync Delay Controller (Expandable)
            AnimatedVisibility(visible = showSyncControls || slot.syncDelayMs > 0) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    SyncOffsetSlider(
                        delayMs = slot.syncDelayMs,
                        onDelayChanged = onDelayChanged
                    )
                }
            }
        }
    }
}

