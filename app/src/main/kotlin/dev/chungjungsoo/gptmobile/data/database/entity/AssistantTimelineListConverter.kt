package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.TypeConverter
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class AssistantTimelineListConverter {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromString(value: String): List<AssistantTimelineItem> = if (value.isBlank()) {
        emptyList()
    } else {
        try {
            json.decodeFromString(value)
        } catch (_: SerializationException) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromList(value: List<AssistantTimelineItem>): String = json.encodeToString(value)
}
