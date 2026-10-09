
package com.jmstudiox.app.engine

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.jmstudiox.app.data.model.EditState
import com.jmstudiox.app.data.model.normalized
import com.jmstudiox.app.data.model.outputDurationMs
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

class FfmpegEngine(private val context: Context) {

    companion object {
        private const val TAG = "JMStudioX_FFMPEG"
    }

    private val runningSessionId = AtomicLong(-1L)
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun ui(block: () -> Unit) {
        mainHandler.post(block)
    }

    private fun outputDirectory(): File {
        val base = context.getExternalFilesDir(
            Environment.DIRECTORY_MOVIES
        ) ?: File(context.filesDir, "Movies")

        return File(base, "JMStudioX").apply {
            if (!exists() && !mkdirs()) {
                throw IllegalStateException(
                    "Cannot create output directory: $absolutePath"
                )
            }
            if (!isDirectory || !canWrite()) {
                throw IllegalStateException(
                    "Output directory is not writable: $absolutePath"
                )
            }
        }
    }

    fun export(
        input: File,
        state: EditState,
        inputDurationMs: Long,
        onProgress: (Int) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!input.exists() || input.length() == 0L) {
            onError("Input video does not exist or is empty.")
            return
        }

        if (inputDurationMs <= 0L) {
            onError("Video duration is invalid.")
            return
        }

        val safeState = state.normalized(inputDurationMs)

        if (safeState.endMs <= safeState.startMs) {
            onError("End time must be greater than start time.")
            return
        }

        val output: File
        val command: String

        try {
            output = File(
                outputDirectory(),
                "JMStudioX_${System.currentTimeMillis()}.mp4"
            )

            command = FfmpegCommandBuilder.export(
                input = input,
                output = output,
                state = safeState
            )
        } catch (e: Exception) {
            onError(e.message ?: "Could not prepare export.")
            return
        }

        val expectedMs = safeState.outputDurationMs(inputDurationMs)
            .coerceAtLeast(1L)

        Log.i(TAG, "Starting export")
        Log.i(TAG, "Input: ${input.absolutePath}")
        Log.i(TAG, "Output: ${output.absolutePath}")
        Log.i(TAG, "Expected duration: $expectedMs ms")
        Log.i(TAG, "Command: $command")

        try {
            FFmpegKit.executeAsync(
                command,
                { session: FFmpegSession ->
                    runningSessionId.compareAndSet(
                        session.sessionId,
                        -1L
                    )

                    val returnCode = session.returnCode

                    if (ReturnCode.isSuccess(returnCode)) {
                        if (output.exists() && output.length() > 0L) {
                            Log.i(
                                TAG,
                                "Export success: ${output.length()} bytes"
                            )
                            ui {
                                onProgress(100)
                                onSuccess(output)
                            }
                        } else {
                            output.delete()
                            val error =
                                "FFmpeg reported success, but the output file is missing or empty."
                            Log.e(TAG, error)
                            ui { onError(error) }
                        }
                    } else if (ReturnCode.isCancel(returnCode)) {
                        output.delete()
                        Log.w(TAG, "Export cancelled")
                        ui { onError("Export cancelled.") }
                    } else {
                        output.delete()

                        val details = buildString {
                            appendLine("FFmpeg export failed.")
                            appendLine("Return code: $returnCode")
                            session.failStackTrace?.let {
                                appendLine(it)
                            }
                            session.allLogsAsString?.let {
                                appendLine(it)
                            }
                        }.trim()

                        Log.e(TAG, details)
                        ui {
                            onError(details.takeLast(2500))
                        }
                    }
                },
                { log ->
                    Log.d(TAG, log.message ?: "")
                },
                { statistics: Statistics ->
                    val currentMs = statistics.time
                    val percent = (
                            currentMs * 100.0 / expectedMs
                            ).roundToInt().coerceIn(0, 99)

                    ui { onProgress(percent) }
                }
            ).also { sessionId ->
                runningSessionId.set(sessionId.sessionId)
            }
        } catch (e: Exception) {
            runningSessionId.set(-1L)
            output.delete()

            val error = e.stackTraceToString()
            Log.e(TAG, "Could not start FFmpeg", e)
            ui {
                onError(
                    error.takeLast(2500)
                )
            }
        }
    }

    fun merge(
        inputs: List<File>,
        onProgress: (Int) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (inputs.size < 2) {
            onError("Select at least two videos to merge.")
            return
        }

        val invalid = inputs.firstOrNull {
            !it.exists() || it.length() == 0L
        }

        if (invalid != null) {
            onError("An input video is missing: ${invalid.name}")
            return
        }

        val output: File
        val command: String

        try {
            output = File(
                outputDirectory(),
                "JMStudioX_merge_${System.currentTimeMillis()}.mp4"
            )
            command = FfmpegCommandBuilder.merge(inputs, output)
        } catch (e: Exception) {
            onError(e.message ?: "Could not prepare merge.")
            return
        }

        Log.i(TAG, "Starting merge: $command")

        try {
            FFmpegKit.executeAsync(
                command,
                { session ->
                    runningSessionId.compareAndSet(
                        session.sessionId,
                        -1L
                    )

                    if (ReturnCode.isSuccess(session.returnCode)) {
                        if (output.exists() && output.length() > 0L) {
                            ui {
                                onProgress(100)
                                onSuccess(output)
                            }
                        } else {
                            output.delete()
                            ui {
                                onError(
                                    "FFmpeg finished, but merged output is empty."
                                )
                            }
                        }
                    } else if (ReturnCode.isCancel(session.returnCode)) {
                        output.delete()
                        ui { onError("Merge cancelled.") }
                    } else {
                        output.delete()

                        val details = buildString {
                            appendLine("FFmpeg merge failed.")
                            appendLine("Return code: ${session.returnCode}")
                            session.failStackTrace?.let {
                                appendLine(it)
                            }
                            session.allLogsAsString?.let {
                                appendLine(it)
                            }
                        }.trim()

                        Log.e(TAG, details)
                        ui { onError(details.takeLast(2500)) }
                    }
                },
                { log ->
                    Log.d(TAG, log.message ?: "")
                },
                { statistics ->
                    // Activity indicator only; total duration is not known here.
                    val percent = (statistics.time / 1000L % 99L)
                        .toInt()
                        .coerceIn(0, 98)

                    ui { onProgress(percent) }
                }
            ).also {
                runningSessionId.set(it.sessionId)
            }
        } catch (e: Exception) {
            runningSessionId.set(-1L)
            output.delete()
            Log.e(TAG, "Could not start merge", e)
            ui { onError(e.stackTraceToString().takeLast(2500)) }
        }
    }

    fun cancel() {
        val id = runningSessionId.getAndSet(-1L)
        if (id >= 0L) {
            FFmpegKit.cancel(id)
            Log.i(TAG, "Cancellation requested for session $id")
        }
    }
}
