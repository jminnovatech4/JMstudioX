package com.jmstudiox.app.data.model

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

enum class MediaKind { VIDEO, AUDIO, IMAGE, ANY }

data class Tool(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val mediaKind: MediaKind = MediaKind.VIDEO
)

val toolList = listOf(
    Tool("Video Editor", "Multi-layer professional editor", Icons.Default.Movie),
    Tool("Cut & Trim", "Precise timeline cutting", Icons.Default.ContentCut),
    Tool("Merge Video", "Join multiple clips", Icons.Default.VideoLibrary),
    Tool("Compress", "Reduce video size", Icons.Default.Compress),
    Tool("Crop & Resize", "Crop and change aspect ratio", Icons.Default.Crop),
    Tool("Watermark", "Logo and branding", Icons.Default.LocalOffer),
    Tool("Brightness", "Color and visual adjustment", Icons.Default.Tune),
    Tool("Audio Extractor", "Video to MP3/AAC", Icons.Default.AudioFile),
    Tool("Audio Cutter", "Trim an audio file", Icons.Default.ContentCut, MediaKind.AUDIO),
    Tool("Audio Merger", "Combine audio tracks", Icons.Default.Merge, MediaKind.AUDIO),
    Tool("Vocal Remover", "Basic center-channel reduction", Icons.Default.MicOff, MediaKind.AUDIO),
    Tool("Converter", "Convert media formats", Icons.Default.Sync, MediaKind.ANY),
    Tool("Image to Video", "Create video from images", Icons.Default.Image, MediaKind.IMAGE)
)
