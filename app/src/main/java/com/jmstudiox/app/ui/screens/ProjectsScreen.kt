package com.jmstudiox.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProjectsScreen() {
    Box(
        Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.VideoLibrary, null, Modifier.size(64.dp))
            Spacer(Modifier.height(12.dp))
            Text("Projects", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Your saved editing projects can be listed here.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
