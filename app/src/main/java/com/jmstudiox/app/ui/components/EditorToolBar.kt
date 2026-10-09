package com.jmstudiox.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class EditorTool {
    TRIM,
    SPEED,
    AUDIO,
    ADJUST,
    CROP,
    ROTATE,
    TEXT,
    WATERMARK
}

@Composable
fun EditorToolBar(
    selected: EditorTool,
    onSelected: (EditorTool) -> Unit
) {
    val tools = listOf(
        EditorTool.TRIM to (Icons.Default.ContentCut to "Trim"),
        EditorTool.SPEED to (Icons.Default.Speed to "Speed"),
        EditorTool.AUDIO to (Icons.Default.VolumeUp to "Audio"),
        EditorTool.ADJUST to (Icons.Default.Tune to "Adjust"),
        EditorTool.CROP to (Icons.Default.Crop to "Crop"),
        EditorTool.ROTATE to (Icons.Default.RotateRight to "Rotate"),
        EditorTool.TEXT to (Icons.Default.TextFields to "Text"),
        EditorTool.WATERMARK to (Icons.Default.Image to "Logo")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tools.forEach { (tool, pair) ->

            FilterChip(
                selected = selected == tool,

                onClick = {
                    onSelected(tool)
                },

                label = {
                    Text(pair.second)
                },

                leadingIcon = {
                    Icon(
                        imageVector = pair.first,
                        contentDescription = pair.second
                    )
                }
            )
        }
    }
}