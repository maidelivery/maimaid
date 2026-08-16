package org.rhythmeta.maimaid.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

object DatabaseConverters {
    @TypeConverter
    fun encodeStringDoubleMap(value: Map<String, Double>?): String? =
        value?.let { Json.encodeToString(it) }

    @TypeConverter
    fun decodeStringDoubleMap(value: String?): Map<String, Double>? =
        value?.let { Json.decodeFromString(it) }
}
