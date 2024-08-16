package dev.chungjungsoo.gptmobile.data.dto

import dev.chungjungsoo.gptmobile.data.ModelConstants.getDefaultAPIUrl
import dev.chungjungsoo.gptmobile.data.model.ApiType

data class Platform(
    val name: ApiType,
    val selected: Boolean = false,
    val enabled: Boolean = false,
    val apiUrl: String = getDefaultAPIUrl(name),
    val token: String? = null,
    val model: String? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val systemPrompt: String? = null
)
