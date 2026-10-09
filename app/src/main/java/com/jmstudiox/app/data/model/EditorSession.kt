package com.jmstudiox.app.data.model

import android.net.Uri

data class EditorSession(
    val sourceUri: String,
    val fileName: String,
    val editState: EditState = EditState(),
    val extraClipUris: List<String> = emptyList(),
    val selectedTool: String = "TRIM",
    val playbackPositionMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun source(): Uri = Uri.parse(sourceUri)
}
