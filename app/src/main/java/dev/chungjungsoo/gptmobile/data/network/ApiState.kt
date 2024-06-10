package dev.chungjungsoo.gptmobile.data.network

sealed class ApiState<out T> {
    data object Loading : ApiState<Nothing>()
    data class Success<T>(val data: T) : ApiState<T>()
    data class Error(val message: String) : ApiState<Nothing>()
    data object Done : ApiState<Nothing>()
}
