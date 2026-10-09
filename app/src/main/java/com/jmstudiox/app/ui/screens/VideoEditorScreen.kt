
package com.jmstudiox.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.jmstudiox.app.data.model.*
import com.jmstudiox.app.engine.FfmpegEngine
import com.jmstudiox.app.repository.MediaStoreRepository
import com.jmstudiox.app.ui.components.*
import com.jmstudiox.app.utils.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@Composable
fun VideoEditorScreen(
    uri: Uri,
    fileName: String,
    initialSession: EditorSession? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var playableUri by remember(uri) { mutableStateOf<Uri?>(null) }
    val engine = remember { FfmpegEngine(context) }

    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var state by remember(uri) { mutableStateOf(initialSession?.editState ?: EditState()) }
    var selectedTool by remember(uri) { mutableStateOf(runCatching { EditorTool.valueOf(initialSession?.selectedTool ?: "TRIM") }.getOrDefault(EditorTool.TRIM)) }
    var processing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var resultFile by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var extraClipUris by remember(uri) { mutableStateOf(initialSession?.extraClipUris?.map(Uri::parse) ?: emptyList()) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf("") }

    val logoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { picked ->
        if (picked != null) {
            try {
                val file = MediaUtils.copyToCache(context, picked, "watermark.png")
                state = state.copy(watermarkPath = file.absolutePath)
                message = "Logo added to the timeline."
            } catch (e: Exception) {
                message = e.message ?: "Could not read logo."
            }
        }
    }

    val clipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { picked ->
        if (picked.isNotEmpty()) {
            picked.forEach { clip ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        clip,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            extraClipUris = (extraClipUris + picked).distinct()
        }
    }

    // Copy the selected video into app-private storage. Photo Picker URIs may
    // lose permission after the app is restarted, but this copy remains readable.
    LaunchedEffect(uri, fileName) {
        playableUri = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val directory = File(context.filesDir, "editor_media")
                if (!directory.exists()) {
                    check(directory.mkdirs()) { "Could not create editor media folder." }
                }

                val extension = fileName.substringAfterLast('.', "mp4")
                    .filter { it.isLetterOrDigit() }
                    .ifBlank { "mp4" }
                val destination = File(directory, "source_${uri.toString().hashCode()}.$extension")

                if (!destination.exists() || destination.length() == 0L) {
                    val tempFile = File(directory, "${destination.name}.tmp")
                    if (tempFile.exists()) tempFile.delete()
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Cannot open selected video. Please select it again." }
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    check(tempFile.length() > 0L) { "Selected video is empty." }
                    if (destination.exists()) destination.delete()
                    check(tempFile.renameTo(destination)) { "Could not save video for editing." }
                }
                Uri.fromFile(destination)
            }
        }
        result.onSuccess { playableUri = it }
            .onFailure { message = it.message ?: "Could not load the selected video." }
    }

    val player = remember(playableUri) {
        ExoPlayer.Builder(context).build().apply {
            playableUri?.let { localUri ->
                setMediaItem(MediaItem.fromUri(localUri))
                prepare()
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(1L)
                    if (state.endMs == Long.MAX_VALUE) {
                        state = state.copy(endMs = duration)
                    }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("JMStudioX", "Preview failed. URI=$playableUri", error)
                message = "Preview failed: ${error.errorCodeName}. Please select the video again if needed."
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            delay(150)
        }
    }

    // Persist editing settings after each change, with a short debounce to avoid
    // writing preferences on every slider frame.
    LaunchedEffect(uri, fileName, state, extraClipUris, selectedTool, playableUri) {
        delay(250)
        val savedUri = playableUri ?: return@LaunchedEffect
        runCatching {
            EditorSessionStore.save(
                context,
                EditorSession(
                    sourceUri = savedUri.toString(),
                    fileName = fileName,
                    editState = state,
                    extraClipUris = extraClipUris.map(Uri::toString),
                    selectedTool = selectedTool.name,
                    playbackPositionMs = player.currentPosition.coerceAtLeast(0L)
                )
            )
        }
    }

    // Store the playhead periodically so a force-stop/reboot resumes near the
    // last position without causing frequent disk writes.
    LaunchedEffect(uri, state, extraClipUris, selectedTool, playableUri) {
        while (true) {
            delay(2000)
            val savedUri = playableUri ?: continue
            runCatching {
                EditorSessionStore.save(
                    context,
                    EditorSession(
                        sourceUri = savedUri.toString(), fileName = fileName, editState = state,
                        extraClipUris = extraClipUris.map(Uri::toString), selectedTool = selectedTool.name,
                        playbackPositionMs = player.currentPosition.coerceAtLeast(0L)
                    )
                )
            }
        }
    }

    LaunchedEffect(player, initialSession) {
        val savedPosition = initialSession?.playbackPositionMs ?: 0L
        if (savedPosition > 0L) {
            player.seekTo(savedPosition)
        }
    }

    LaunchedEffect(state.volume) {
        player.volume = if (state.mute) 0f else state.volume
    }

    LaunchedEffect(state.speed) {
        player.setPlaybackSpeed(state.speed)
    }

    fun export() {
        if (duration <= 0L) {
            message = "Video duration is not ready yet."
            return
        }
        if (state.endMs <= state.startMs) {
            message = "End must be greater than start."
            return
        }
        scope.launch {
            processing = true
            progress = 0
            resultFile = null
            val sourceForExport = playableUri
            if (sourceForExport == null) {
                processing = false
                message = "Video is still loading. Please wait and try again."
                return@launch
            }

            val mainInput = try {
                MediaUtils.copyToCache(context, sourceForExport, fileName)
            } catch (e: Exception) {
                processing = false
                message = e.message ?: "Could not read the selected video."
                return@launch
            }

            val allInputs = mutableListOf(mainInput)
            try {
                extraClipUris.forEachIndexed { index, clip ->
                    allInputs += MediaUtils.copyToCache(context, clip, "clip_$index.mp4")
                }
            } catch (e: Exception) {
                processing = false
                message = e.message ?: "Could not read one of the added clips."
                return@launch
            }

            fun renderFinal(input: File, exportDurationMs: Long) {
                val finalState = if (allInputs.size > 1) {
                    state.copy(startMs = 0L, endMs = exportDurationMs).normalized(exportDurationMs)
                } else {
                    state.normalized(exportDurationMs)
                }
                engine.export(
                    input = input,
                    state = finalState,
                    inputDurationMs = exportDurationMs,
                    onProgress = { p -> progress = p },
                    onSuccess = { file ->
                        processing = false
                        resultFile = file
                        message = "Export completed successfully."
                    },
                    onError = { error ->
                        processing = false
                        message = error
                    }
                )
            }

            if (allInputs.size == 1) {
                renderFinal(mainInput, duration)
            } else {
                val totalDuration = duration + extraClipUris.sumOf { MediaUtils.durationMs(context, it) }
                engine.merge(
                    inputs = allInputs,
                    onProgress = { p -> progress = p.coerceIn(0, 99) },
                    onSuccess = { merged -> renderFinal(merged, totalDuration.coerceAtLeast(duration)) },
                    onError = { error ->
                        processing = false
                        message = error
                    }
                )
            }
        }
    }


    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("JM StudioX Pro Editor", style = MaterialTheme.typography.titleLarge)
                Text(fileName, style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = ::export,
                enabled = !processing && duration > 0L
            ) {
                Icon(Icons.Default.FileUpload, null)
                Spacer(Modifier.width(6.dp))
                Text("Export")
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    clipPicker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo
                        )
                    )
                }
            ) {
                Icon(Icons.Default.AddToPhotos, null)

                Spacer(
                    Modifier.width(6.dp)
                )

                Text("Add clips")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                val exoPlayer = player
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            useController = true
                            this.player = exoPlayer
                        }
                    },
                    update = { it.player = exoPlayer },
                    modifier = Modifier.fillMaxSize()
                )
                if (playableUri == null) {
                    CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.Center))
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = {
                if (playing) player.pause() else player.play()
            }) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
            }
            IconButton(onClick = {
                player.seekTo(state.startMs.coerceIn(0L, duration))
            }) {
                Icon(Icons.Default.SkipPrevious, null)
            }
            IconButton(onClick = {
                player.seekTo(state.endMs.coerceAtMost(duration))
            }) {
                Icon(Icons.Default.SkipNext, null)
            }
        }

        TimelineEditor(
            durationMs = duration,
            positionMs = position,
            startMs = state.startMs,
            endMs = state.endMs.coerceAtMost(duration),
            onStartChanged = { value ->
                state = state.copy(startMs = value.coerceAtMost(state.endMs - 250))
                player.seekTo(state.startMs)
            },
            onEndChanged = { value ->
                state = state.copy(endMs = value.coerceAtLeast(state.startMs + 250))
            },
            onSeek = { player.seekTo(it) }
        )

        EditorToolBar(selectedTool, onSelected = { selectedTool = it })

        when (selectedTool) {
            EditorTool.TRIM -> TrimPanel(state, duration) { state = it }
            EditorTool.SPEED -> SpeedPanel(state) {
                state = state.copy(speed = it)
                player.setPlaybackSpeed(it)
            }
            EditorTool.AUDIO -> AudioPanel(state) { state = it }
            EditorTool.ADJUST -> AdjustPanel(state) { state = it }
            EditorTool.CROP -> CropPanel(state) { state = it }
            EditorTool.ROTATE -> RotatePanel(state) { state = it }
            EditorTool.TEXT -> TextPanel(
                state = state,
                onAdd = { textValue = ""; showTextDialog = true },
                onRemove = { state = state.copy(textOverlay = null) }
            )
            EditorTool.WATERMARK -> WatermarkPanel(
                state = state,
                onPick = { logoPicker.launch("image/*") },
                onPathChanged = { state = state.copy(watermarkPath = it) }
            )
        }

        Text(
            "Edits are combined and rendered by FFmpeg only when you tap Export.",
            style = MaterialTheme.typography.labelSmall
        )

        resultFile?.let { file ->
            ResultCard(
                file = file,
                onSave = {
                    val ok = MediaStoreRepository.saveVideo(
                        context, file, "JMStudioX_${System.currentTimeMillis()}"
                    )
                    message = if (ok) "Saved to Gallery / Movies / JM StudioX" else "Could not save to Gallery."
                },
                onShare = {
                    try {
                        val contentUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, contentUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share video"))
                    } catch (e: Exception) {
                        message = e.message ?: "Could not open the share sheet."
                    }
                }
            )
        }
    }

    if (processing) {
        ProcessingOverlay(progress, "Exporting") {
            engine.cancel()
            processing = false
        }
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add text") },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("Text") },
                    singleLine = false
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        state = state.copy(textOverlay = TextOverlayState(textValue))
                        showTextDialog = false
                    }
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showTextDialog = false }) { Text("Cancel") } }
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("JM StudioX") },
            text = { Text(it) },
            confirmButton = { Button(onClick = { message = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun TrimPanel(state: EditState, duration: Long, onChanged: (EditState) -> Unit) {
    val total = duration.coerceAtLeast(0L)
    val minGap = 250L
    val start = state.startMs.coerceIn(0L, total)
    val end = state.endMs.coerceIn(start, total)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Trim / Cut", style = MaterialTheme.typography.titleMedium)
            Text("Start: ${MediaUtils.formatTime(start)}")
            Slider(
                value = start.toFloat(),
                onValueChange = { raw ->
                    val maxStart = (end - minGap).coerceAtLeast(0L)
                    onChanged(state.copy(startMs = raw.toLong().coerceIn(0L, maxStart)))
                },
                valueRange = 0f..total.coerceAtLeast(1L).toFloat(),
                enabled = total >= minGap
            )
            Text("End: ${MediaUtils.formatTime(end)}")
            Slider(
                value = end.toFloat(),
                onValueChange = { raw ->
                    val minEnd = (start + minGap).coerceAtMost(total)
                    onChanged(state.copy(endMs = raw.toLong().coerceIn(minEnd, total)))
                },
                valueRange = 0f..total.coerceAtLeast(1L).toFloat(),
                enabled = total >= minGap
            )
            Text("Selected duration: ${MediaUtils.formatTime((end - start).coerceAtLeast(0L))}")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { onChanged(state.copy(startMs = 0L)) }, enabled = start > 0L) {
                    Text("Reset Start")
                }
                OutlinedButton(onClick = { onChanged(state.copy(endMs = total)) }, enabled = end < total && total > start) {
                    Text("Reset End")
                }
            }
        }
    }
}

@Composable
private fun SpeedPanel(state: EditState, onChanged: (Float) -> Unit) {
    val values = listOf(.25f, .5f, .75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Speed", style = MaterialTheme.typography.titleMedium)
            Text(String.format("%.2fx", state.speed))
            Slider(
                value = values.indexOf(state.speed).coerceAtLeast(0).toFloat(),
                onValueChange = { raw ->
                    onChanged(values[raw.roundToInt().coerceIn(0, values.lastIndex)])
                },
                valueRange = 0f..values.lastIndex.toFloat(),
                steps = values.size - 2
            )
        }
    }
}

@Composable
private fun AudioPanel(state: EditState, onChanged: (EditState) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Audio", style = MaterialTheme.typography.titleMedium)
            Text("Volume ${String.format("%.0f%%", state.volume * 100)}")
            Slider(value = state.volume, onValueChange = { onChanged(state.copy(volume = it)) }, valueRange = 0f..2f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Mute")
                Switch(checked = state.mute, onCheckedChange = { onChanged(state.copy(mute = it)) })
            }
        }
    }
}

@Composable
private fun AdjustPanel(state: EditState, onChanged: (EditState) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Adjust", style = MaterialTheme.typography.titleMedium)
            LabeledSlider("Brightness", state.brightness, -1f..1f) { onChanged(state.copy(brightness = it)) }
            LabeledSlider("Contrast", state.contrast, .2f..2f) { onChanged(state.copy(contrast = it)) }
            LabeledSlider("Saturation", state.saturation, 0f..2f) { onChanged(state.copy(saturation = it)) }
        }
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Text("$title ${String.format("%.2f", value)}")
    Slider(value = value, onValueChange = onChange, valueRange = range)
}

@Composable
private fun CropPanel(state: EditState, onChanged: (EditState) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Crop / Aspect Ratio", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("free", "1:1", "16:9", "9:16", "4:5").forEach { ratio ->
                    FilterChip(
                        selected = state.crop?.aspect == ratio,
                        onClick = { onChanged(state.copy(crop = if (ratio == "free") null else CropState(ratio))) },
                        label = { Text(ratio) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RotatePanel(state: EditState, onChanged: (EditState) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = { onChanged(state.copy(rotation = (state.rotation + 270) % 360)) }) {
                Text("↶ 90°")
            }
            Text("${state.rotation}°", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { onChanged(state.copy(rotation = (state.rotation + 90) % 360)) }) {
                Text("90° ↷")
            }
        }
    }
}

@Composable
private fun TextPanel(
    state: EditState,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(state.textOverlay?.text ?: "No text layer")
            Row {
                Button(onClick = onAdd) { Text("Add") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun WatermarkPanel(
    state: EditState,
    onPick: () -> Unit,
    onPathChanged: (String?) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Logo / Watermark", style = MaterialTheme.typography.titleMedium)
            Text(
                state.watermarkPath ?: "Choose a logo image and wire the Android picker to this path.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPick) { Text("Choose Logo") }
                OutlinedButton(onClick = { onPathChanged(null) }) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun ResultCard(
    file: File,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Export ready", style = MaterialTheme.typography.titleMedium)
            Text(file.name)
            Text(MediaUtils.fileSize(file), style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save to Gallery")
                }
                OutlinedButton(onClick = onShare) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
            }
        }
    }
}

