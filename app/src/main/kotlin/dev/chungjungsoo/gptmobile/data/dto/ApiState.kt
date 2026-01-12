package dev.chungjungsoo.gptmobile.data.dto

sealed class ApiState {
    data object Loading : ApiState()
    data class Thinking(val thinkingChunk: String) : ApiState()
    data class Success(val textChunk: String) : ApiState()
    data class Error(val message: String) : ApiState()
    data object Done : ApiState()
}
