package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.TypeConverter
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class AssistantRevisionListConverter {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromString(value: String): List<AssistantRevision> = if (value.isBlank()) {
        emptyList()
    } else {
        try {
            json.decodeFromString(value)
        } catch (e: SerializationException) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromList(value: List<AssistantRevision>): String = json.encodeToString(value)
}
