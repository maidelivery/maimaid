package net.krtl.maimaid.data.local.converter

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

class RoomConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>?): String = json.encodeToString(value ?: emptyList())

    @TypeConverter
    fun toStringList(value: String?): List<String> = if (value.isNullOrBlank()) emptyList() else json.decodeFromString(value)
}
