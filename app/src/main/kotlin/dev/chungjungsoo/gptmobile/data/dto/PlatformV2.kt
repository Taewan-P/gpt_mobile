package dev.chungjungsoo.gptmobile.data.dto

import java.util.UUID

data class PlatformV2(
    val uid: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = false,
    val apiUrl: String,
    val token: String? = null,
    val model: String,
    val temperature: Float? = null,
    val topP: Float? = null,
    val systemPrompt: String? = null,
    val stream: Boolean = true
)
