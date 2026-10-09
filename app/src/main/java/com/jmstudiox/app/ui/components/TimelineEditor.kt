package com.jmstudiox.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jmstudiox.app.utils.MediaUtils
import kotlin.math.roundToInt

@Composable
fun TimelineEditor(
    durationMs: Long,
    positionMs: Long,
    startMs: Long,
    endMs: Long,
    onStartChanged: (Long) -> Unit,
    onEndChanged: (Long) -> Unit,
    onSeek: (Long) -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(MediaUtils.formatTime(positionMs), style = MaterialTheme.typography.labelSmall)
            Text(MediaUtils.formatTime(safeDuration), style = MaterialTheme.typography.labelSmall)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .horizontalScroll(rememberScrollState())
        ) {
            Row(
                Modifier
                    .fillMaxHeight()
                    .width(900.dp)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(18) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (i % 3 == 0)
                                    MaterialTheme.colorScheme.primary.copy(alpha = .30f)
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)
                            )
                            .clickable {
                                onSeek((safeDuration * i / 18f).roundToInt().toLong())
                            }
                    )
                }
            }
        }

        RangeSlider(
            value = startMs.toFloat() / safeDuration..endMs.coerceAtMost(safeDuration).toFloat() / safeDuration,
            onValueChange = {
                val s = (it.start * safeDuration).toLong()
                val e = (it.endInclusive * safeDuration).toLong()
                if (e - s >= 250) {
                    onStartChanged(s)
                    onEndChanged(e)
                }
            },
            valueRange = 0f..1f
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Start ${MediaUtils.formatTime(startMs)}", style = MaterialTheme.typography.labelSmall)
            Text("End ${MediaUtils.formatTime(endMs)}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
