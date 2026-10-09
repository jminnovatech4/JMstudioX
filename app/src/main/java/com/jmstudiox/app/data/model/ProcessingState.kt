package com.jmstudiox.app.data.model

sealed interface ProcessingState {
    data object Idle : ProcessingState
    data class Running(
        val progress: Int,
        val message: String = "Processing video…"
    ) : ProcessingState
    data class Success(val path: String) : ProcessingState
    data class Error(val message: String) : ProcessingState
    data object Cancelled : ProcessingState
}
