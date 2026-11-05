package com.dualaudio.streamer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualaudio.streamer.ui.theme.AccentSecondary
import com.dualaudio.streamer.ui.theme.StatusAmber
import com.dualaudio.streamer.ui.theme.SurfaceElevated
import com.dualaudio.streamer.ui.theme.TextMuted
import com.dualaudio.streamer.ui.theme.TextPrimary
import com.dualaudio.streamer.ui.theme.TextSecondary

/**
 * Controller for adjusting audio buffer sync offset (delay line) to eliminate
 * Bluetooth decoding latency lag between two different earphone models.
 */
@Composable
fun SyncOffsetSlider(
    delayMs: Int,
    onDelayChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync Offset",
                    tint = StatusAmber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = " Latency Sync Offset",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Surface(
                color = StatusAmber.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "+${delayMs} ms",
                    color = StatusAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Text(
            text = "Adds buffer delay to synchronize with slower Bluetooth headsets",
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onDelayChanged((delayMs - 10).coerceAtLeast(0)) },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "-10ms",
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Slider(
                value = delayMs.toFloat(),
                onValueChange = { onDelayChanged(it.toInt()) },
                valueRange = 0f..400f,
                steps = 39, // 10ms increments
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = StatusAmber,
                    activeTrackColor = StatusAmber,
                    inactiveTrackColor = SurfaceElevated
                )
            )

            IconButton(
                onClick = { onDelayChanged((delayMs + 10).coerceAtMost(400)) },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "+10ms",
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Quick preset chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0, 40, 80, 150).forEach { presetMs ->
                FilterChip(
                    selected = delayMs == presetMs,
                    onClick = { onDelayChanged(presetMs) },
                    label = {
                        Text(
                            text = if (presetMs == 0) "0 ms" else "+$presetMs ms",
                            fontSize = 11.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StatusAmber.copy(alpha = 0.25f),
                        selectedLabelColor = StatusAmber,
                        containerColor = SurfaceElevated,
                        labelColor = TextSecondary
                    )
                )
            }
        }
    }
}

