package com.jmstudiox.app.data.model

data class EditState(
    val startMs: Long = 0L,
    val endMs: Long = Long.MAX_VALUE,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val crop: CropState? = null,
    val rotation: Int = 0,
    val textOverlay: TextOverlayState? = null,
    val watermarkPath: String? = null,
    val mute: Boolean = false
)

data class CropState(
    val aspect: String = "free"
)

data class TextOverlayState(
    val text: String,
    val x: Float = 0.5f,
    val y: Float = 0.85f,
    val fontSize: Int = 48
)

fun EditState.normalized(durationMs: Long): EditState {
    val safeEnd = if (endMs == Long.MAX_VALUE) durationMs else endMs
    return copy(
        startMs = startMs.coerceIn(0L, durationMs),
        endMs = safeEnd.coerceIn(1L, durationMs).coerceAtLeast(startMs + 1L),
        speed = speed.coerceIn(0.25f, 4f),
        volume = volume.coerceIn(0f, 2f),
        brightness = brightness.coerceIn(-1f, 1f),
        contrast = contrast.coerceIn(0.2f, 2f),
        saturation = saturation.coerceIn(0f, 2f),
        rotation = ((rotation % 360) + 360) % 360
    )
}

fun EditState.outputDurationMs(inputDurationMs: Long): Long {
    val e = normalized(inputDurationMs)
    return ((e.endMs - e.startMs) / e.speed).toLong().coerceAtLeast(1L)
}
