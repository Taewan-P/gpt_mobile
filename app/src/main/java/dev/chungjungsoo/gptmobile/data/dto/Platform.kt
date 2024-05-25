package dev.chungjungsoo.gptmobile.data.dto

data class Platform(
    val name: ApiType,
    val selected: Boolean = false,
    val enabled: Boolean = false,
    val token: String? = null,
    val model: String? = null
)
