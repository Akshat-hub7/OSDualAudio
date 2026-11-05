package com.dualaudio.streamer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualaudio.streamer.data.BluetoothDeviceModel
import com.dualaudio.streamer.data.BluetoothDeviceType
import com.dualaudio.streamer.ui.theme.AccentPrimary
import com.dualaudio.streamer.ui.theme.AccentSecondary
import com.dualaudio.streamer.ui.theme.StatusGreen
import com.dualaudio.streamer.ui.theme.SurfaceBorder
import com.dualaudio.streamer.ui.theme.SurfaceDark
import com.dualaudio.streamer.ui.theme.SurfaceElevated
import com.dualaudio.streamer.ui.theme.TextMuted
import com.dualaudio.streamer.ui.theme.TextPrimary

@Composable
fun DeviceCard(
    device: BluetoothDeviceModel,
    isSlot1Selected: Boolean,
    isSlot2Selected: Boolean,
    onAssignSlot1: () -> Unit,
    onAssignSlot2: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, SurfaceBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val iconVector = when (device.deviceType) {
                        BluetoothDeviceType.HEADSET -> Icons.Default.Headphones
                        BluetoothDeviceType.EARBUDS -> Icons.Default.Headphones
                        BluetoothDeviceType.SPEAKER -> Icons.Default.Speaker
                        BluetoothDeviceType.HEARING_AID -> Icons.Default.Hearing
                        else -> Icons.Default.Bluetooth
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = device.deviceType.name,
                            tint = if (device.isConnected) StatusGreen else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = device.name,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = device.address,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                // Status chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (device.isConnected) StatusGreen.copy(alpha = 0.2f) else SurfaceElevated
                ) {
                    Text(
                        text = if (device.isConnected) "Connected" else "Paired",
                        color = if (device.isConnected) StatusGreen else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Assign Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAssignSlot1,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSlot1Selected) AccentPrimary else SurfaceBorder
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSlot1Selected) AccentPrimary.copy(alpha = 0.2f) else SurfaceElevated
                    )
                ) {
                    Text(
                        text = if (isSlot1Selected) "In Headset 1" else "+ Assign to 1",
                        fontSize = 12.sp,
                        color = if (isSlot1Selected) AccentPrimary else TextPrimary
                    )
                }

                OutlinedButton(
                    onClick = onAssignSlot2,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSlot2Selected) AccentSecondary else SurfaceBorder
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSlot2Selected) AccentSecondary.copy(alpha = 0.2f) else SurfaceElevated
                    )
                ) {
                    Text(
                        text = if (isSlot2Selected) "In Headset 2" else "+ Assign to 2",
                        fontSize = 12.sp,
                        color = if (isSlot2Selected) AccentSecondary else TextPrimary
                    )
                }
            }
        }
    }
}

