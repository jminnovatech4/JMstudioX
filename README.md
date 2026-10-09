# JM StudioX — Pro Editor module

Replace the old single-file editor with this modular structure:

app/src/main/java/com/jmstudiox/app/
├── data/model/EditState.kt
├── data/model/ProcessingState.kt
├── data/model/Tool.kt
├── engine/FfmpegCommandBuilder.kt
├── engine/FfmpegEngine.kt
├── repository/MediaStoreRepository.kt
├── utils/MediaUtils.kt
├── ui/components/ProcessingOverlay.kt
├── ui/components/TimelineEditor.kt
├── ui/components/EditorToolBar.kt
├── ui/screens/VideoEditorScreen.kt
├── ui/screens/ToolExecutionScreen.kt
├── ui/screens/ProjectsScreen.kt
└── ui/JMStudioXApp.kt

The source is designed for the existing package `com.jmstudiox.app`.

IMPORTANT:
1. Do not keep the old giant `VideoEditorScreen()` in `JMStudioXApp.kt`.
2. Keep your existing MainActivity/theme if they already work.
3. Add the FFmpeg dependency shown below.
4. Add the FileProvider entry shown below if you want Share/Open to work.
5. The editor uses FFmpeg for the final export. Media3 is used only for preview.

FFmpeg dependency:
implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9")

Recommended existing media preview dependencies:
implementation("androidx.media3:media3-exoplayer:1.5.1")
implementation("androidx.media3:media3-ui:1.5.1")

The editor supports in one project:
- trim/cut
- split markers in the timeline UI
- playback speed
- volume/mute
- brightness
- contrast
- saturation
- crop/aspect ratio
- rotate
- text overlay
- image watermark
- audio track selection
- export with FFmpeg
- real progress
- cancel
- save to Gallery
- share
- open result

AI vocal separation and social-media downloading are intentionally separate engines; ordinary FFmpeg processing is not AI stem separation or a platform downloader.
