package com.jmstudiox.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProcessingOverlay(
    progress: Int,
    title: String,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("$progress%")
//                Text(
//                    "FFmpeg is processing your media. You can cancel safely.",
//                    style = MaterialTheme.typography.bodySmall
//                )
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onCancel) {
                Icon(Icons.Default.Close, null)
                Spacer(Modifier.width(6.dp))
                Text("Cancel")
            }
        }
    )
}
