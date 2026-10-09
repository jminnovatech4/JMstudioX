package com.jmstudiox.app.engine

import com.jmstudiox.app.data.model.EditState
import java.io.File
import java.util.Locale

object FfmpegCommandBuilder {

    fun export(input: File, output: File, state: EditState): String {
        val filters = buildVideoFilters(state)
        val cmd = mutableListOf<String>()
        cmd += "-y"
        cmd += "-ss"; cmd += seconds(state.startMs)
        cmd += "-t"; cmd += seconds((state.endMs - state.startMs).coerceAtLeast(1L))
        cmd += "-i"; cmd += quote(input.absolutePath)

        if (state.watermarkPath.isNullOrBlank()) {
            if (filters.isNotEmpty()) { cmd += "-vf"; cmd += quote(filters.joinToString(",")) }
            cmd += "-map"; cmd += "0:v:0"
            cmd += "-map"; cmd += "0:a?"
        } else {
            cmd += "-loop"; cmd += "1"
            cmd += "-i"; cmd += quote(state.watermarkPath)
            val base = if (filters.isEmpty()) "[0:v]null[base]" else "[0:v]${filters.joinToString(",")}[base]"
            val graph = "$base;[1:v]format=rgba[wm];[base][wm]overlay=W-w-24:H-h-24:eof_action=repeat[vout]"
            cmd += "-filter_complex"; cmd += quote(graph)
            cmd += "-map"; cmd += "[vout]"
            cmd += "-map"; cmd += "0:a?"
        }

        if (state.mute || state.volume == 0f) {
            cmd += "-an"
        } else {
            val audioFilters = mutableListOf<String>()
            if (state.speed != 1f) audioFilters += atempoChain(state.speed)
            if (state.volume != 1f) audioFilters += "volume=${fmt(state.volume)}"
            if (audioFilters.isNotEmpty()) { cmd += "-af"; cmd += quote(audioFilters.joinToString(",")) }
            cmd += "-c:a"; cmd += "aac"
            cmd += "-b:a"; cmd += "192k"
        }

        // LGPL full build: use OpenH264 rather than GPL x264.
        cmd += "-c:v"; cmd += "libopenh264"
        cmd += "-b:v"; cmd += "6M"
        cmd += "-pix_fmt"; cmd += "yuv420p"
        cmd += "-movflags"; cmd += "+faststart"
        cmd += quote(output.absolutePath)
        return cmd.joinToString(" ")
    }

    fun merge(inputs: List<File>, output: File): String {
        require(inputs.size >= 2)
        val cmd = mutableListOf<String>()
        cmd += "-y"
        inputs.forEach { file ->
            cmd += "-i"; cmd += quote(file.absolutePath)
        }
        val graph = buildString {
            inputs.indices.forEach { i ->
                append("[$i:v:0]scale=trunc(iw/2)*2:trunc(ih/2)*2,setsar=1[v$i];")
                append("[$i:a:0]aresample=async=1:first_pts=0[a$i];")
            }
            inputs.indices.forEach { i -> append("[v$i][a$i]") }
            append("concat=n=${inputs.size}:v=1:a=1[v][a]")
        }
        cmd += "-filter_complex"; cmd += quote(graph)
        cmd += "-map"; cmd += "[v]"
        cmd += "-map"; cmd += "[a]"
        cmd += "-c:a"; cmd += "aac"
        cmd += "-b:a"; cmd += "192k"
        cmd += "-c:v"; cmd += "libopenh264"
        cmd += "-b:v"; cmd += "6M"
        cmd += "-pix_fmt"; cmd += "yuv420p"
        cmd += "-movflags"; cmd += "+faststart"
        cmd += quote(output.absolutePath)
        return cmd.joinToString(" ")
    }

    private fun buildVideoFilters(state: EditState): MutableList<String> {
        val vf = mutableListOf<String>()
        state.crop?.let {
            when (it.aspect) {
                "1:1" -> vf += "crop=min(iw\\,ih):min(iw\\,ih)"
                "16:9" -> vf += "crop=if(iw/ih>16/9\\,ih*16/9\\,iw):if(iw/ih>16/9\\,ih\\,iw*9/16)"
                "9:16" -> vf += "crop=if(iw/ih>9/16\\,ih*9/16\\,iw):if(iw/ih>9/16\\,ih\\,iw*16/9)"
                "4:5" -> vf += "crop=if(iw/ih>4/5\\,ih*4/5\\,iw):if(iw/ih>4/5\\,ih\\,iw*5/4)"
            }
        }
        if (state.brightness != 0f || state.contrast != 1f || state.saturation != 1f) {
            vf += String.format(Locale.US, "eq=brightness=%.3f:contrast=%.3f:saturation=%.3f", state.brightness, state.contrast, state.saturation)
        }
        if (state.speed != 1f) vf += "setpts=PTS/${fmt(state.speed)}"
        when (state.rotation) {
            90 -> vf += "transpose=1"
            180 -> vf += "transpose=1,transpose=1"
            270 -> vf += "transpose=2"
        }
        state.textOverlay?.let { t ->
            val text = escapeDrawText(t.text)
            vf += "drawtext=fontfile=/system/fonts/Roboto-Regular.ttf:text='$text':fontsize=${t.fontSize}:fontcolor=white:borderw=3:bordercolor=black:x=(w-text_w)*${t.x}:y=(h-text_h)*${t.y}"
        }
        return vf
    }

    private fun atempoChain(speed: Float): String {
        var s = speed
        val parts = mutableListOf<String>()
        while (s < 0.5f) { parts += "atempo=0.5"; s /= 0.5f }
        while (s > 2f) { parts += "atempo=2.0"; s /= 2f }
        parts += "atempo=${fmt(s)}"
        return parts.joinToString(",")
    }

    private fun seconds(ms: Long) = String.format(Locale.US, "%.3f", ms / 1000.0)
    private fun fmt(v: Float) = String.format(Locale.US, "%.3f", v)
    private fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"
    private fun escapeDrawText(s: String) = s.replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'").replace("%", "\\%").replace("\n", "\\n")
}
